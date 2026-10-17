/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Recorded instrument notes for {@link SoulSynth} (#163). Where a bank has an instrument, that
 * instrument's notes are played from recordings; where it does not, the oscillator recipe in
 * {@link SoulSynth.Voice#of} plays it exactly as it did before the bank existed. So a bank may cover
 * half the instruments, or none, and the soul still sounds like a soul.
 *
 * <h2>Why a manifest rather than a table in Java</h2>
 *
 * <p>Which recording plays a note - the key range it covers, the velocity layer, the round-robin
 * between two takes of the same note - is a decision that gets tuned by listening, and tuning it
 * should not mean a recompile. The manifest holds those decisions; this class only applies them.
 *
 * <h2>Why the decoder is a parameter</h2>
 *
 * <p>This package is Minecraft-free, so it cannot reach the game's Ogg decoder. The client hands in
 * one, and the tests hand in JOrbis; the bank never knows which.
 *
 * <h2>Why a bad bank is an exception, not a partial bank</h2>
 *
 * <p>A bank that loaded half of itself would play some instruments from recordings and the rest from
 * oscillators, with nobody having chosen that mix. So {@link #parse} either returns the whole bank
 * or throws, and the caller falls back to {@link #EMPTY} as a whole.
 */
public final class SampleBank
{
    /** No recordings at all: every instrument falls back to its oscillator. */
    public static final SampleBank EMPTY = new SampleBank(Map.of());

    /** The manifest's format version. A later one is refused rather than guessed at. */
    static final int FORMAT = 1;

    /** The loop sentinel: a manifest's {@code loop_start} or {@code loop_end} of -1 means not looped. */
    private static final int NO_LOOP = -1;

    /** Fetches one file's samples. The name is the manifest's string, relative to the bank's folder. */
    @FunctionalInterface
    public interface Decoder
    {
        /** Mono float PCM in [-1, 1] at {@link SoulSynth#SAMPLE_RATE}. */
        float[] decode(String file) throws IOException;
    }

    private final Map<Instrument, Kit> kits;

    private SampleBank(Map<Instrument, Kit> kits)
    {
        this.kits = kits;
    }

    /**
     * Reads a manifest and decodes every file it names. Instrument keys this build does not know are
     * skipped without error, so a pack written for a later version loses only the instruments it
     * added.
     *
     * @throws IOException if the manifest is malformed, a zone is missing something it needs, a loop
     *                     points outside its sample, or a file will not decode
     */
    public static SampleBank parse(Reader manifest, Decoder decoder) throws IOException
    {
        final JsonObject root = readObject(manifest);
        final Where top = new Where(null, null);

        if (root.has("format") && number(root.get("format"), "format", top) != FORMAT)
        {
            throw top.fail("format " + root.get("format") + " is not one this build reads");
        }

        if (root.has("sample_rate") && number(root.get("sample_rate"), "sample_rate", top) != SoulSynth.SAMPLE_RATE)
        {
            throw top.fail("sample_rate " + root.get("sample_rate") + " is not " + SoulSynth.SAMPLE_RATE);
        }

        final JsonElement instruments = required(root, "instruments", top);

        if (!instruments.isJsonObject())
        {
            throw top.fail("'instruments' is not an object");
        }

        // a file two instruments share is decoded once, not once per zone that names it
        final Map<String, short[]> decoded = new HashMap<>();
        final Map<Instrument, Kit> kits = new EnumMap<>(Instrument.class);

        for (Map.Entry<String, JsonElement> entry : instruments.getAsJsonObject().entrySet())
        {
            final Instrument instrument = instrument(entry.getKey());

            if (instrument != null)
            {
                kits.put(instrument, kit(instrument, entry.getValue(), decoder, decoded));
            }
        }

        return new SampleBank(Collections.unmodifiableMap(kits));
    }

    /** Whether the bank has recordings for this instrument. */
    public boolean has(Instrument instrument)
    {
        return this.kits.containsKey(instrument);
    }

    /** The instrument's recordings, or null when the bank has none for it. */
    Kit kit(Instrument instrument)
    {
        return this.kits.get(instrument);
    }

    // ---------------------------------------------------------------------------------------------
    // Model
    // ---------------------------------------------------------------------------------------------

    /** How a note's amplitude moves over its life. */
    enum Envelope
    {
        /** Plays the recording to its end; the note's duration is ignored. */
        STRUCK,

        /** Holds for the note's duration, then releases. */
        SUSTAIN,

        /** A sin-squared arch over the note's duration: wind, rustle, the swell. */
        SWELL;

        String key()
        {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Everything one instrument's manifest entry says, with its zones. */
    static final class Kit
    {
        final Instrument instrument;
        final double level;
        final Envelope envelope;
        final double attack;
        final double release;
        final boolean trackPitch;

        /** The tone filter's cutoff at velocity 0 and at velocity 1. Both zero when there is no filter. */
        final double toneLow;
        final double toneHigh;

        final List<Zone> zones;

        Kit(Instrument instrument, double level, Envelope envelope, double attack, double release,
                boolean trackPitch, double toneLow, double toneHigh, List<Zone> zones)
        {
            this.instrument = instrument;
            this.level = level;
            this.envelope = envelope;
            this.attack = attack;
            this.release = release;
            this.trackPitch = trackPitch;
            this.toneLow = toneLow;
            this.toneHigh = toneHigh;
            this.zones = List.copyOf(zones);
        }

        boolean filtered()
        {
            return this.toneLow > 0d;
        }

        /**
         * The zone that plays {@code event}. Chosen by key, then velocity, then duration, relaxing the
         * last two only when nothing else is left: a note with no recording in its own velocity layer
         * is still played from the right key, rather than from the wrong one or not at all.
         *
         * <p>When several zones are left they are round-robin, with the choice a hash of the seed, the
         * note's start and its pitch. That is deterministic - the same piece renders the same audio -
         * and it spreads repeated notes across takes rather than playing one recording on a loop.
         */
        Zone select(NoteEvent event, long seed)
        {
            final int midi = event.midi();
            final double velocity = Math.max(0d, Math.min(1d, event.velocity()));
            final double duration = event.duration();

            List<Zone> candidates = where(zone -> zone.covers(midi)
                    && zone.velocityLow <= velocity && velocity <= zone.velocityHigh
                    && zone.minDuration <= duration && duration <= zone.maxDuration);

            if (candidates.isEmpty())
            {
                candidates = where(zone -> zone.covers(midi));
            }

            if (candidates.isEmpty())
            {
                // off the bottom or top of every zone: the nearest recorded pitch, and a tie goes to the
                // lower root so the choice does not depend on the order the manifest lists them in
                int bestDistance = Integer.MAX_VALUE;
                int bestRoot = Integer.MAX_VALUE;

                for (Zone zone : this.zones)
                {
                    final int distance = Math.abs(zone.root - midi);

                    if (distance < bestDistance || distance == bestDistance && zone.root < bestRoot)
                    {
                        bestDistance = distance;
                        bestRoot = zone.root;
                    }
                }

                final int root = bestRoot;

                candidates = where(zone -> zone.root == root);
            }

            if (candidates.size() == 1)
            {
                return candidates.get(0);
            }

            final long hash = SoulMusicBrief.mix(seed ^ Double.doubleToLongBits(event.start()) ^ midi);

            return candidates.get(Math.floorMod(hash, candidates.size()));
        }

        private List<Zone> where(Predicate<Zone> test)
        {
            final List<Zone> matches = new ArrayList<>();

            for (Zone zone : this.zones)
            {
                if (test.test(zone))
                {
                    matches.add(zone);
                }
            }

            return matches;
        }
    }

    /** One recording and the notes it is allowed to play. */
    static final class Zone
    {
        final String file;
        /** Mono, 16-bit: a sample value of {@code s} is {@code s / 32767f}. */
        final short[] samples;
        final int root;
        final int low;
        final int high;
        final double velocityLow;
        final double velocityHigh;
        final double minDuration;
        final double maxDuration;
        final double gain;

        /** Sample frames, or {@link #NO_LOOP} for a zone that does not loop. */
        final int loopStart;
        final int loopEnd;

        Zone(String file, short[] samples, int root, int low, int high, double velocityLow, double velocityHigh,
                double minDuration, double maxDuration, double gain, int loopStart, int loopEnd)
        {
            this.file = file;
            this.samples = samples;
            this.root = root;
            this.low = low;
            this.high = high;
            this.velocityLow = velocityLow;
            this.velocityHigh = velocityHigh;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.gain = gain;
            this.loopStart = loopStart;
            this.loopEnd = loopEnd;
        }

        boolean covers(int midi)
        {
            return this.low <= midi && midi <= this.high;
        }

        boolean looped()
        {
            return this.loopStart != NO_LOOP;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Manifest
    // ---------------------------------------------------------------------------------------------

    /** Where a manifest problem is, so every error names the instrument and file it is about. */
    private record Where(String instrument, String file)
    {
        IOException fail(String problem)
        {
            final StringBuilder message = new StringBuilder("soul music bank");

            if (this.instrument != null)
            {
                message.append(", instrument '").append(this.instrument).append('\'');
            }

            if (this.file != null)
            {
                message.append(", file '").append(this.file).append('\'');
            }

            return new IOException(message.append(": ").append(problem).toString());
        }
    }

    private static JsonObject readObject(Reader manifest) throws IOException
    {
        final JsonElement element;

        try
        {
            element = JsonParser.parseReader(manifest);
        }
        catch (JsonParseException e)
        {
            throw new IOException("soul music bank is not valid JSON: " + e.getMessage(), e);
        }

        if (element == null || !element.isJsonObject())
        {
            throw new IOException("soul music bank is not a JSON object");
        }

        return element.getAsJsonObject();
    }

    private static Instrument instrument(String key)
    {
        for (Instrument instrument : Instrument.values())
        {
            if (instrument.name().toLowerCase(Locale.ROOT).equals(key))
            {
                return instrument;
            }
        }

        return null;
    }

    private static Kit kit(Instrument instrument, JsonElement element, Decoder decoder,
            Map<String, short[]> decoded) throws IOException
    {
        final String name = instrument.name().toLowerCase(Locale.ROOT);
        final Where where = new Where(name, null);

        if (!element.isJsonObject())
        {
            throw where.fail("the entry is not an object");
        }

        final JsonObject object = element.getAsJsonObject();
        final double level = nonNegative(object, "level", 1d, where);
        final Envelope envelope = envelope(object, where);
        final double attack = nonNegative(object, "attack", 0d, where);
        final double release = nonNegative(object, "release", 0.25d, where);
        final boolean trackPitch = bool(object, "track_pitch", instrument.pitched(), where);
        final double[] tone = tone(object, where);

        final JsonElement zoneList = required(object, "zones", where);

        if (!zoneList.isJsonArray() || zoneList.getAsJsonArray().isEmpty())
        {
            throw where.fail("'zones' is not a non-empty array");
        }

        final List<Zone> zones = new ArrayList<>();

        for (JsonElement zone : zoneList.getAsJsonArray())
        {
            zones.add(zone(instrument, zone, decoder, decoded));
        }

        return new Kit(instrument, level, envelope, attack, release, trackPitch,
                tone == null ? 0d : tone[0], tone == null ? 0d : tone[1], zones);
    }

    private static Zone zone(Instrument instrument, JsonElement element, Decoder decoder,
            Map<String, short[]> decoded) throws IOException
    {
        final String name = instrument.name().toLowerCase(Locale.ROOT);

        if (!element.isJsonObject())
        {
            throw new Where(name, null).fail("a zone is not an object");
        }

        final JsonObject object = element.getAsJsonObject();
        final JsonElement fileElement = required(object, "file", new Where(name, null));

        if (!fileElement.isJsonPrimitive() || !fileElement.getAsJsonPrimitive().isString()
                || fileElement.getAsString().isBlank())
        {
            throw new Where(name, null).fail("a zone's 'file' is not a non-empty string");
        }

        final String file = fileElement.getAsString();
        final Where where = new Where(name, file);
        final short[] samples = samples(where, decoder, decoded);

        required(object, "root", where);

        final int root = midi(object, "root", 0, where);
        final int low = midi(object, "lo", 0, where);
        final int high = midi(object, "hi", 127, where);

        if (low > high)
        {
            throw where.fail("'lo' " + low + " is above 'hi' " + high);
        }

        final double velocityLow = number(object, "vel_lo", 0d, where);
        final double velocityHigh = number(object, "vel_hi", 1d, where);

        if (velocityLow < 0d || velocityHigh > 1d || velocityLow > velocityHigh)
        {
            throw where.fail("velocity range " + velocityLow + ".." + velocityHigh + " is not inside 0..1");
        }

        final double minDuration = nonNegative(object, "min_duration", 0d, where);
        final double maxDuration = nonNegative(object, "max_duration", 1_000_000d, where);

        if (minDuration > maxDuration)
        {
            throw where.fail("'min_duration' is above 'max_duration'");
        }

        final double gain = nonNegative(object, "gain", 1d, where);

        // both or neither: a loop with only one end is a typo, not a request for no loop
        final int loopStart = whole(object, "loop_start", NO_LOOP, where);
        final int loopEnd = whole(object, "loop_end", NO_LOOP, where);

        if (loopStart == NO_LOOP && loopEnd == NO_LOOP)
        {
            return new Zone(file, samples, root, low, high, velocityLow, velocityHigh,
                    minDuration, maxDuration, gain, NO_LOOP, NO_LOOP);
        }

        if (loopStart == NO_LOOP || loopEnd == NO_LOOP)
        {
            throw where.fail("'loop_start' and 'loop_end' must be given together");
        }

        if (loopStart < 0 || loopStart >= loopEnd || loopEnd > samples.length)
        {
            throw where.fail("loop " + loopStart + ".." + loopEnd + " is not inside the "
                    + samples.length + " frames of the sample");
        }

        return new Zone(file, samples, root, low, high, velocityLow, velocityHigh,
                minDuration, maxDuration, gain, loopStart, loopEnd);
    }

    private static short[] samples(Where where, Decoder decoder, Map<String, short[]> decoded) throws IOException
    {
        final short[] cached = decoded.get(where.file());

        if (cached != null)
        {
            return cached;
        }

        final float[] floats;

        try
        {
            floats = decoder.decode(where.file());
        }
        catch (IOException | RuntimeException e)
        {
            // a RuntimeException from a decoder is still a file that will not load: the caller falls back
            // to EMPTY on IOException, and a game must not crash over one bad recording
            throw new IOException(where.fail("could not be decoded: " + e.getMessage()).getMessage(), e);
        }

        if (floats == null || floats.length == 0)
        {
            throw where.fail("decoded to no samples");
        }

        final short[] samples = toShorts(floats);

        decoded.put(where.file(), samples);

        return samples;
    }

    /**
     * Stored as 16-bit. The shipped bank is some 200 seconds of audio, and as floats it would be twice
     * the heap for no audible gain; the rounding is 1/32767 at worst, below anything a player hears.
     * The decoder's float array is dropped once this has run.
     */
    static short[] toShorts(float[] floats)
    {
        final short[] shorts = new short[floats.length];

        for (int index = 0; index < floats.length; index++)
        {
            shorts[index] = (short) Math.round(Math.max(-1f, Math.min(1f, floats[index])) * 32767f);
        }

        return shorts;
    }

    private static Envelope envelope(JsonObject object, Where where) throws IOException
    {
        if (!object.has("envelope"))
        {
            return Envelope.STRUCK;
        }

        final JsonElement element = object.get("envelope");

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
        {
            for (Envelope envelope : Envelope.values())
            {
                if (envelope.key().equals(element.getAsString()))
                {
                    return envelope;
                }
            }
        }

        throw where.fail("envelope " + element + " is not struck, sustain or swell");
    }

    /** {@code [lo_hz, hi_hz]}, or null when absent. */
    private static double[] tone(JsonObject object, Where where) throws IOException
    {
        if (!object.has("tone"))
        {
            return null;
        }

        final JsonElement element = object.get("tone");

        if (!element.isJsonArray() || element.getAsJsonArray().size() != 2)
        {
            throw where.fail("'tone' is not [lo_hz, hi_hz]");
        }

        final JsonArray pair = element.getAsJsonArray();
        final double low = number(pair.get(0), "tone", where);
        final double high = number(pair.get(1), "tone", where);

        // zero would mean "no filter" and a negative cutoff is not a filter at all
        if (low <= 0d || high < low)
        {
            throw where.fail("'tone' " + low + ".." + high + " must be positive and rising");
        }

        return new double[] {low, high};
    }

    private static JsonElement required(JsonObject object, String key, Where where) throws IOException
    {
        final JsonElement element = object.get(key);

        if (element == null || element.isJsonNull())
        {
            throw where.fail("'" + key + "' is missing");
        }

        return element;
    }

    private static double number(JsonElement element, String key, Where where) throws IOException
    {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            throw where.fail("'" + key + "' is not a number");
        }

        final double value = element.getAsDouble();

        if (!Double.isFinite(value))
        {
            throw where.fail("'" + key + "' is not finite");
        }

        return value;
    }

    private static double number(JsonObject object, String key, double fallback, Where where) throws IOException
    {
        return object.has(key) ? number(object.get(key), key, where) : fallback;
    }

    private static double nonNegative(JsonObject object, String key, double fallback, Where where) throws IOException
    {
        final double value = number(object, key, fallback, where);

        if (value < 0d)
        {
            throw where.fail("'" + key + "' is negative");
        }

        return value;
    }

    private static int whole(JsonObject object, String key, int fallback, Where where) throws IOException
    {
        if (!object.has(key))
        {
            return fallback;
        }

        final double value = number(object.get(key), key, where);

        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
        {
            throw where.fail("'" + key + "' is not a whole number");
        }

        return (int) value;
    }

    private static int midi(JsonObject object, String key, int fallback, Where where) throws IOException
    {
        final int value = whole(object, key, fallback, where);

        if (value < 0 || value > 127)
        {
            throw where.fail("'" + key + "' " + value + " is not a MIDI note");
        }

        return value;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback, Where where) throws IOException
    {
        if (!object.has(key))
        {
            return fallback;
        }

        final JsonElement element = object.get(key);

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
        {
            throw where.fail("'" + key + "' is not true or false");
        }

        return element.getAsBoolean();
    }
}
