/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample bank's rules, pinned against synthetic recordings (#163): a sine at a known pitch
 * stands in for an instrument, so every assertion is about the mechanism rather than about any
 * recording. {@link ShippedSampleBankTest} checks the files that actually ship.
 */
class SampleBankTest
{
    private static final int RATE = SoulSynth.SAMPLE_RATE;

    @Test
    @DisplayName("the lowest key range wins below its edge and the upper one above it")
    void keyRanges() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"low.ogg\", \"root\": 48, \"lo\": 0, \"hi\": 59},"
                + "{\"file\": \"high.ogg\", \"root\": 72, \"lo\": 60, \"hi\": 127}]}"),
                Map.of("low.ogg", constant(0.1, 10), "high.ogg", constant(0.2, 10)));

        assertEquals("low.ogg", select(bank, Instrument.PAD, 50, 0.5, 1d).file);
        assertEquals("high.ogg", select(bank, Instrument.PAD, 70, 0.5, 1d).file);
    }

    @Test
    @DisplayName("velocity layers pick the soft take for a soft note and the hard one for a hard note")
    void velocityLayers() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"soft.ogg\", \"root\": 60, \"vel_lo\": 0, \"vel_hi\": 0.4},"
                + "{\"file\": \"hard.ogg\", \"root\": 60, \"vel_lo\": 0.6, \"vel_hi\": 1}]}"),
                Map.of("soft.ogg", constant(0.1, 10), "hard.ogg", constant(0.2, 10)));

        assertEquals("soft.ogg", select(bank, Instrument.PAD, 60, 0.2, 1d).file);
        assertEquals("hard.ogg", select(bank, Instrument.PAD, 60, 0.9, 1d).file);
    }

    @Test
    @DisplayName("duration layers pick the short take for a short note and the long one for a long note")
    void durationLayers() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"short.ogg\", \"root\": 60, \"min_duration\": 0, \"max_duration\": 0.4},"
                + "{\"file\": \"long.ogg\", \"root\": 60, \"min_duration\": 0.6, \"max_duration\": 1000}]}"),
                Map.of("short.ogg", constant(0.1, 10), "long.ogg", constant(0.2, 10)));

        assertEquals("short.ogg", select(bank, Instrument.PAD, 60, 0.5, 0.2d).file);
        assertEquals("long.ogg", select(bank, Instrument.PAD, 60, 0.5, 3d).file);
    }

    @Test
    @DisplayName("a note with no zone in its velocity layer is played from the right key, not dropped")
    void velocityFallsBackToKey() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"soft.ogg\", \"root\": 60, \"vel_lo\": 0, \"vel_hi\": 0.4}]}"),
                Map.of("soft.ogg", constant(0.1, 10)));

        assertEquals("soft.ogg", select(bank, Instrument.PAD, 60, 0.9, 1d).file);
    }

    @Test
    @DisplayName("a note shorter or longer than every duration layer is still played from the key")
    void durationFallsBackToKey() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"held.ogg\", \"root\": 60, \"min_duration\": 5, \"max_duration\": 10}]}"),
                Map.of("held.ogg", constant(0.1, 10)));

        assertEquals("held.ogg", select(bank, Instrument.PAD, 60, 0.5, 1d).file);
    }

    @Test
    @DisplayName("off every key range, the zone with the nearest root plays, and a tie goes to the lower root")
    void outsideEveryRangeUsesNearestRoot() throws IOException
    {
        // midi 60 sits between the two ranges, equidistant from both roots: 50 is 10 below it and 70 is 10
        // above it, so the tie goes to 50 whatever order the manifest lists them in
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"high.ogg\", \"root\": 70, \"lo\": 20, \"hi\": 30},"
                + "{\"file\": \"low.ogg\", \"root\": 50, \"lo\": 0, \"hi\": 10}]}"),
                Map.of("low.ogg", constant(0.1, 10), "high.ogg", constant(0.2, 10)));

        assertEquals("low.ogg", select(bank, Instrument.PAD, 60, 0.5, 1d).file);

        final SampleBank nearer = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"a.ogg\", \"root\": 20, \"lo\": 0, \"hi\": 10},"
                + "{\"file\": \"b.ogg\", \"root\": 90, \"lo\": 30, \"hi\": 40}]}"),
                Map.of("a.ogg", constant(0.1, 10), "b.ogg", constant(0.2, 10)));

        assertEquals("b.ogg", select(nearer, Instrument.PAD, 60, 0.5, 1d).file);
    }

    @Test
    @DisplayName("several zones for one note are round-robin, the same every time, and both are heard")
    void roundRobinIsDeterministic() throws IOException
    {
        final String json = manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"a.ogg\", \"root\": 60},"
                + "{\"file\": \"b.ogg\", \"root\": 60}]}");
        final Map<String, float[]> files = Map.of("a.ogg", constant(0.1, 10), "b.ogg", constant(0.2, 10));

        final List<String> first = new ArrayList<>();
        final List<String> second = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        final SampleBank one = parse(json, files);
        final SampleBank two = parse(json, files);

        for (int index = 0; index < 100; index++)
        {
            final double start = index * 0.1d;
            final String picked = select(one, Instrument.PAD, 60, 0.5, start, 7L, 1d).file;

            first.add(picked);
            second.add(select(two, Instrument.PAD, 60, 0.5, start, 7L, 1d).file);
            seen.add(picked);
        }

        assertEquals(first, second, "the same notes choose the same takes");
        assertEquals(Set.of("a.ogg", "b.ogg"), seen, "repeated notes are spread across both takes");
    }

    @Test
    @DisplayName("a sample recorded at 60 played at 72 sounds an octave up, and at 60 it is untouched")
    void pitchTracking() throws IOException
    {
        final SampleBank tracking = single("pad", "\"envelope\": \"struck\", \"track_pitch\": true",
                "", sine(440d, 2d));
        final SampleBank fixed = single("pad", "\"envelope\": \"struck\", \"track_pitch\": false",
                "", sine(440d, 2d));

        final float[] up = render(SampleVoice.play(note(0d, 1d, 72, 1f, Instrument.PAD),
                tracking.kit(Instrument.PAD), 1L), RATE / 2);
        final float[] same = render(SampleVoice.play(note(0d, 1d, 72, 1f, Instrument.PAD),
                fixed.kit(Instrument.PAD), 1L), RATE / 2);

        assertEquals(880d, frequency(up, RATE / 10, RATE / 2), 880d * 0.01d, "tracked to the note");
        assertEquals(440d, frequency(same, RATE / 10, RATE / 2), 440d * 0.01d, "kept at the recorded pitch");
    }

    @Test
    @DisplayName("a sustained note holds its loop for its duration, releases, and is then done")
    void sustainLoopsThenReleases() throws IOException
    {
        final SampleBank bank = single("pad", "\"envelope\": \"sustain\", \"release\": 0.1, \"track_pitch\": false",
                "\"loop_start\": 0, \"loop_end\": 22050", sine(220d, 0.5d));
        final SampleVoice voice = SampleVoice.play(note(0d, 1d, 60, 1f, Instrument.PAD),
                bank.kit(Instrument.PAD), 1L);

        final float[] out = new float[2 * RATE];
        int doneAt = -1;

        for (int index = 0; index < out.length; index++)
        {
            out[index] = voice.next();

            if (doneAt < 0 && voice.done())
            {
                doneAt = index + 1;
            }
        }

        // half a second of recording, held for a second by the loop, then 0.1 s of release
        assertTrue(rms(out, (int) Math.round(0.6 * RATE), (int) Math.round(0.9 * RATE)) > 0.3d, "the loop holds the note past its sample");
        assertTrue(doneAt >= RATE, "not done while still held: " + doneAt);
        assertTrue(Math.abs(doneAt - 1.1 * RATE) <= 2, "done at the end of the release: " + doneAt);
    }

    @Test
    @DisplayName("an unlooped sustained note ends with its recording, even when held longer")
    void sustainRunsOutWithItsRecording() throws IOException
    {
        final SampleBank bank = single("pad", "\"envelope\": \"sustain\", \"track_pitch\": false", "",
                sine(220d, 0.2d));
        final SampleVoice voice = SampleVoice.play(note(0d, 1d, 60, 1f, Instrument.PAD),
                bank.kit(Instrument.PAD), 1L);

        int doneAt = -1;

        for (int index = 0; index < RATE && doneAt < 0; index++)
        {
            voice.next();

            if (voice.done())
            {
                doneAt = index + 1;
            }
        }

        assertEquals(Math.round(0.2 * RATE), doneAt);
    }

    @Test
    @DisplayName("a struck note ignores its duration and rings to the end of its recording")
    void struckIgnoresDuration() throws IOException
    {
        final SampleBank bank = single("pad", "\"envelope\": \"struck\"", "", sine(440d, 0.3d));
        final SampleVoice voice = SampleVoice.play(note(0d, 0.01d, 60, 1f, Instrument.PAD),
                bank.kit(Instrument.PAD), 1L);

        final float[] out = new float[RATE];
        int doneAt = -1;

        for (int index = 0; index < out.length && doneAt < 0; index++)
        {
            out[index] = voice.next();

            if (voice.done())
            {
                doneAt = index + 1;
            }
        }

        assertEquals(Math.round(0.3 * RATE), doneAt, "done at the recording's end, not at the note's");
        assertTrue(rms(out, (int) (0.2 * RATE), (int) (0.25 * RATE)) > 0.1d, "still ringing after the duration");
    }

    @Test
    @DisplayName("a swell is silent at both ends of its duration and full in the middle")
    void swellEnvelopeIsZeroAtBothEnds() throws IOException
    {
        final SampleBank bank = single("wind", "\"envelope\": \"swell\", \"track_pitch\": false", "",
                constant(1d, RATE));
        final SampleVoice voice = SampleVoice.play(note(0d, 1d, 60, 1f, Instrument.WIND),
                bank.kit(Instrument.WIND), 1L);

        final float[] out = new float[RATE];
        int doneAt = -1;

        for (int index = 0; index < out.length && doneAt < 0; index++)
        {
            out[index] = voice.next();

            if (voice.done())
            {
                doneAt = index + 1;
            }
        }

        assertEquals(0f, out[0], 0f, "the first sample of a swell is silent");
        assertTrue(out[doneAt - 1] < 1e-4f, "the last sample of a swell is silent: " + out[doneAt - 1]);
        assertTrue(out[RATE / 2] > 0.99f, "the middle of a swell is full: " + out[RATE / 2]);
        assertEquals(RATE, doneAt, "done at the end of its duration");
    }

    @Test
    @DisplayName("a decoded recording is stored at 16 bits, within one step of the float it came from")
    void storageRoundTrip() throws IOException
    {
        final float[] input = new float[1000];

        for (int index = 0; index < input.length; index++)
        {
            input[index] = (float) Math.sin(index * 0.37d) * 0.9f;
        }

        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60}]}"),
                Map.of("x.ogg", input));
        final short[] stored = bank.kit(Instrument.PAD).zones.get(0).samples;

        for (int index = 0; index < input.length; index++)
        {
            assertTrue(Math.abs(stored[index] / 32767f - input[index]) <= 1f / 32767f,
                    "frame " + index + " stored as " + stored[index]);
        }
    }

    @Test
    @DisplayName("a manifest that does not parse, or names something it needs and lacks, is an IOException")
    void malformedManifestsAreRefused()
    {
        final Map<String, float[]> files = Map.of("x.ogg", constant(0.1, 1000));

        assertThrows(IOException.class, () -> parse("{\"format\": 1, \"instruments\": {", files), "truncated");
        assertThrows(IOException.class, () -> parse("{\"format\": 1}", files), "no instruments");
        assertThrows(IOException.class, () -> parse(manifest("\"pad\": {\"zones\": []}"), files), "no zones");
        assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"zones\": [{\"root\": 60}]}"), files), "zone without a file");
        assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\"}]}"), files), "zone without a root");
        assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60, \"lo\": 70, \"hi\": 60}]}"),
                        files), "key range inverted");
        assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"envelope\": \"wobble\", \"zones\": [{\"file\": \"x.ogg\", \"root\": 60}]}"),
                        files), "unknown envelope");
        assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60, \"loop_start\": 10}]}"),
                        files), "loop with one end");
        assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"zones\": [{\"file\": \"gone.ogg\", \"root\": 60}]}"), files),
                "a file the decoder cannot find");
        assertThrows(IOException.class,
                () -> SampleBank.parse(new StringReader(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60}]}")),
                        file ->
                        {
                            throw new IllegalStateException("corrupt Ogg");
                        }), "a decoder that throws");
        assertThrows(IOException.class, () -> parse("{\"format\": 2, \"instruments\": {}}", files), "a later format");
    }

    @Test
    @DisplayName("a loop past its recording is refused, and the error names the instrument and file")
    void loopPastTheEndNamesTheFile()
    {
        final IOException error = assertThrows(IOException.class,
                () -> parse(manifest("\"pad\": {\"zones\": [{\"file\": \"short.ogg\", \"root\": 60,"
                        + " \"loop_start\": 0, \"loop_end\": 5000}]}"),
                        Map.of("short.ogg", constant(0.1, 1000))));

        assertTrue(error.getMessage().contains("pad"), error.getMessage());
        assertTrue(error.getMessage().contains("short.ogg"), error.getMessage());
    }

    @Test
    @DisplayName("instrument keys this build does not know are skipped without error or decoding")
    void unknownInstrumentsAreIgnored() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60}]},"
                + "\"kazoo\": {\"zones\": [{\"file\": \"never-decoded.ogg\", \"root\": 60}]}"),
                Map.of("x.ogg", constant(0.1, 10)));

        assertTrue(bank.has(Instrument.PAD));
        assertFalse(bank.has(Instrument.BELL));
    }

    @Test
    @DisplayName("an instrument the bank lacks plays from its oscillator, exactly as it did before the bank")
    void missingInstrumentsFallBackToOscillators() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60}]}"),
                Map.of("x.ogg", sine(440d, 1d)));
        final SoulComposer.Piece piece = bellPiece();

        final float[][] withBank = renderPiece(piece, bank, 8d);
        final float[][] without = renderPiece(piece, SampleBank.EMPTY, 8d);

        assertTrue(peak(withBank, 3 * RATE, 8 * RATE) > 0.01f, "the oscillator bell is audible");
        assertArrayEquals(without[0], withBank[0], "a bell with no recording is the oscillator bell");
        assertArrayEquals(without[1], withBank[1]);
    }

    @Test
    @DisplayName("an instrument the bank has is played from its recording, not its oscillator")
    void recordedInstrumentsAreUsed() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": [{\"file\": \"x.ogg\", \"root\": 60}]}"),
                Map.of("x.ogg", sine(440d, 1d)));
        final SoulComposer.Piece piece = padPiece();

        final float[][] recorded = renderPiece(piece, bank, 8d);
        final float[][] oscillated = renderPiece(piece, SampleBank.EMPTY, 8d);

        assertFalse(Arrays.equals(oscillated[0], recorded[0]), "the recording is not the oscillator");
        assertTrue(peak(recorded, 3 * RATE, 8 * RATE) > 0.01f, "the recorded pad is audible");
    }

    @Test
    @DisplayName("a soul with a bank renders the same audio twice")
    void recordedRenderIsDeterministic() throws IOException
    {
        final SampleBank bank = parse(manifest("\"pad\": {\"zones\": ["
                + "{\"file\": \"a.ogg\", \"root\": 60},{\"file\": \"b.ogg\", \"root\": 60}]}"),
                Map.of("a.ogg", sine(440d, 1d), "b.ogg", sine(523d, 1d)));
        final SoulComposer.Piece piece = padPiece();

        final float[][] first = renderPiece(piece, bank, 8d);
        final float[][] second = renderPiece(piece, bank, 8d);

        assertArrayEquals(first[0], second[0]);
        assertArrayEquals(first[1], second[1]);
    }

    @Test
    @DisplayName("the empty bank has nothing, and so plays nothing from a recording")
    void emptyBankHasNothing()
    {
        assertFalse(SampleBank.EMPTY.has(Instrument.PAD));
    }

    // ---------------------------------------------------------------------------------------------

    private static SampleBank parse(String json, Map<String, float[]> files) throws IOException
    {
        return SampleBank.parse(new StringReader(json), file ->
        {
            final float[] samples = files.get(file);

            if (samples == null)
            {
                throw new IOException("no file " + file);
            }

            return samples;
        });
    }

    private static String manifest(String instruments)
    {
        return "{\"format\": 1, \"sample_rate\": 44100, \"instruments\": {" + instruments + "}}";
    }

    /**
     * A bank with one instrument and one zone. {@code zoneFields} is appended to the zone's own object,
     * so a loop can be given without writing the whole manifest out.
     */
    private static SampleBank single(String key, String instrumentFields, String zoneFields, float[] sample)
            throws IOException
    {
        final String zone = "{\"file\": \"x.ogg\", \"root\": 60" + (zoneFields.isEmpty() ? "" : ", " + zoneFields) + "}";

        return parse(manifest("\"" + key + "\": {" + instrumentFields + ", \"zones\": [" + zone + "]}"),
                Map.of("x.ogg", sample));
    }

    private static SampleBank.Zone select(SampleBank bank, Instrument instrument, int midi, double velocity,
            double duration)
    {
        return select(bank, instrument, midi, velocity, 0d, 1L, duration);
    }

    private static SampleBank.Zone select(SampleBank bank, Instrument instrument, int midi, double velocity,
            double start, long seed, double duration)
    {
        return bank.kit(instrument).select(note(start, duration, midi, (float) velocity, instrument), seed);
    }

    private static NoteEvent note(double start, double duration, int midi, float velocity, Instrument instrument)
    {
        return new NoteEvent(start, duration, midi, velocity, instrument, 0f);
    }

    /** A sine at {@code frequency}, sampled at the synth's rate. */
    private static float[] sine(double frequency, double seconds)
    {
        final float[] samples = new float[(int) Math.round(seconds * RATE)];

        for (int index = 0; index < samples.length; index++)
        {
            samples[index] = (float) (0.8d * Math.sin(2d * Math.PI * frequency * index / RATE));
        }

        return samples;
    }

    private static float[] constant(double value, int frames)
    {
        final float[] samples = new float[frames];

        Arrays.fill(samples, (float) value);

        return samples;
    }

    private static float[] render(SoulSynth.Voice voice, int frames)
    {
        final float[] out = new float[frames];

        for (int index = 0; index < frames; index++)
        {
            out[index] = voice.next();
        }

        return out;
    }

    /** Rising zero crossings per second over a window: a sine's frequency, to within a crossing. */
    private static double frequency(float[] samples, int from, int to)
    {
        int crossings = 0;

        for (int index = from + 1; index < to; index++)
        {
            if (samples[index - 1] < 0f && samples[index] >= 0f)
            {
                crossings++;
            }
        }

        return crossings * (double) RATE / (to - from);
    }

    private static double rms(float[] samples, int from, int to)
    {
        double sum = 0d;

        for (int index = from; index < to; index++)
        {
            sum += samples[index] * samples[index];
        }

        return Math.sqrt(sum / Math.max(1, to - from));
    }

    private static float peak(float[][] audio, int from, int to)
    {
        float peak = 0f;

        for (float[] channel : audio)
        {
            for (int index = from; index < Math.min(to, channel.length); index++)
            {
                peak = Math.max(peak, Math.abs(channel[index]));
            }
        }

        return peak;
    }

    private static float[][] renderPiece(SoulComposer.Piece piece, SampleBank bank, double seconds)
    {
        final int frames = (int) (seconds * RATE);
        final float[] left = new float[frames];
        final float[] right = new float[frames];

        new SoulSynth(index -> piece, 17L, bank).render(left, right, frames);

        return new float[][] {left, right};
    }

    /** A piece of the given events, in a real piece's clothing. The synth only reads its events and length. */
    private static SoulComposer.Piece piece(List<NoteEvent> events)
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.BASE, 1d), 0d, 1L);
        final SoulComposer.Piece template = SoulComposer.compose(brief, brief.pieceSeed(0));

        return new SoulComposer.Piece(template.voice(), template.support(), template.mode(), template.tonic(),
                template.bpm(), template.space(), 6d, events);
    }

    private static SoulComposer.Piece bellPiece()
    {
        return piece(List.of(
                note(0d, 2d, 60, 0.8f, Instrument.BELL),
                note(1d, 2d, 67, 0.8f, Instrument.BELL)));
    }

    private static SoulComposer.Piece padPiece()
    {
        return piece(List.of(
                note(0d, 2d, 60, 0.8f, Instrument.PAD),
                note(1d, 2d, 67, 0.8f, Instrument.PAD)));
    }
}
