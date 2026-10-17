/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The soul's music, as sound (#163). Rendered, not listened to - which is why what is pinned is what
 * a measurement can hold: it plays at the level Minecraft's own music does, it never clips, it
 * rests between pieces, and the same seed is the same audio.
 *
 * <p>Most of these run twice, once per {@link Bank}: against the shipped recordings, which is what a
 * player hears, and against the oscillator recipes, which are the fallback a missing or unreadable
 * bank drops to. The fallback is held to the same bounds rather than left unchecked. The one
 * exception is {@link #nothingPiercing}, whose bounds were set for oscillators and stay with them;
 * {@link #nothingPiercingRecorded} is the recorded bank's own bound.
 *
 * <p>Each render is a minute or more of audio, and the synth runs at forty-odd times realtime, so
 * this is one of the slower classes in the unit suite: the sound tests render every voice from each
 * bank.
 */
class SoulSynthTest
{
    private static final int RATE = SoulSynth.SAMPLE_RATE;

    /** Which recipe an instrument is played from. Both are held to the same bounds. */
    enum Bank
    {
        /** The recordings in {@code soul_music/bank.json}, as the client loads them. */
        SHIPPED,

        /** Every instrument's oscillator recipe: what plays where the bank is absent or unreadable. */
        OSCILLATORS;

        SampleBank load() throws IOException
        {
            return this == SHIPPED ? JorbisDecoder.shipped() : SampleBank.EMPTY;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Bank.class)
    @DisplayName("every voice plays at about the level of Minecraft's own music, and never reaches full scale")
    void levels(Bank bank) throws IOException
    {
        final SampleBank recipes = bank.load();

        for (SoulVoice voice : SoulVoice.values())
        {
            final double[] levels = new double[2];

            for (int step = 0; step < 2; step++)
            {
                final float[][] audio = render(new SoulMusicBrief(Map.of(voice, 1d), step, 5L), recipes, 90d);
                final double body = rmsDb(audio, (int) (SoulSynth.FIRST_REST * RATE) + 4 * RATE, audio[0].length);
                final double peak = peak(audio);

                assertTrue(body > -28d && body < -16d,
                        bank + " " + voice + " at rank fraction " + step + " plays at " + body + " dBFS RMS");
                assertTrue(peak < 0.96d, bank + " " + voice + " peaks at " + peak);

                levels[step] = body;
            }

            // larger, never louder (#216): a longer tail at the same gain was ten decibels up at the
            // top of the ladder before the reverb was made to hold its energy
            assertTrue(Math.abs(levels[1] - levels[0]) < 4d,
                    bank + " " + voice + " is " + (levels[1] - levels[0]) + " dB louder at the ceiling than at rank 0");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Bank.class)
    @DisplayName("nothing plays before the first piece, and a piece is followed by a rest")
    void itRests(Bank bank) throws IOException
    {
        final SampleBank recipes = bank.load();
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.BASE, 1d), 0d, 1L);
        final SoulComposer.Piece first = SoulComposer.compose(brief, brief.pieceSeed(0));

        // long enough to be well past the first piece's tail and still inside the shortest rest
        final double seconds = SoulSynth.FIRST_REST + first.length() + SoulSynth.MIN_REST * 0.8d;
        final float[][] audio = render(brief, recipes, seconds);

        assertEquals(0d, peak(slice(audio, 0, (int) (SoulSynth.FIRST_REST * RATE) - 1)), 1e-9d,
                bank + ": a player arriving hears the place for a moment before the music");

        final int restStart = (int) ((SoulSynth.FIRST_REST + first.length() + 2d) * RATE);
        final double rest = rmsDb(audio, restStart, audio[0].length);

        assertTrue(rest < -60d, bank + ": the rest after a piece is a rest, not a quieter piece: " + rest + " dBFS");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Bank.class)
    @DisplayName("the same seed renders the same samples")
    void deterministic(Bank bank) throws IOException
    {
        final SampleBank recipes = bank.load();
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.WROUGHT, 1d), 0.5d, 3L);

        final float[][] first = render(brief, recipes, 20d);
        final float[][] second = render(brief, recipes, 20d);

        assertArrayEquals(first[0], second[0]);
        assertArrayEquals(first[1], second[1]);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Bank.class)
    @DisplayName("render block size does not change what is rendered")
    void blockSizeIndependent(Bank bank) throws IOException
    {
        final SampleBank recipes = bank.load();
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.ARCANE, 1d), 0.3d, 9L);
        final int frames = 12 * RATE;

        final SoulSynth whole = synth(brief, recipes);
        final float[] wholeLeft = new float[frames];
        final float[] wholeRight = new float[frames];

        whole.render(wholeLeft, wholeRight, frames);

        final SoulSynth pieces = synth(brief, recipes);
        final float[] left = new float[frames];
        final float[] right = new float[frames];
        final int[] sizes = {1, 777, 3, 44_100, 5_000};
        int done = 0;

        for (int turn = 0; done < frames; turn++)
        {
            final int count = Math.min(sizes[turn % sizes.length], frames - done);
            final float[] blockLeft = new float[count];
            final float[] blockRight = new float[count];

            pieces.render(blockLeft, blockRight, count);
            System.arraycopy(blockLeft, 0, left, done, count);
            System.arraycopy(blockRight, 0, right, done, count);
            done += count;
        }

        assertArrayEquals(wholeLeft, left, bank + ": blocks of different sizes render differently");
        assertArrayEquals(wholeRight, right, bank + ": blocks of different sizes render differently");
    }

    @Test
    @DisplayName("the limiter passes quiet audio untouched and never lets anything reach full scale")
    void limiter()
    {
        assertEquals(0.3f, SoulSynth.limit(0.3f), 0f);
        assertEquals(-0.5f, SoulSynth.limit(-0.5f), 0f);

        for (float input = 0.5f; input < 100f; input *= 1.3f)
        {
            assertTrue(SoulSynth.limit(input) < 0.95f);
            assertTrue(SoulSynth.limit(input) >= SoulSynth.limit(input / 1.3f), "monotonic");
            assertEquals(-SoulSynth.limit(input), SoulSynth.limit(-input), 0f);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Bank.class)
    @DisplayName("a held note keeps its pitch: glass and flute drift no more at eight seconds than at one (#261)")
    void heldNotesHoldStill(Bank bank) throws IOException
    {
        final SampleBank recipes = bank.load();

        // the bell is left out: its FM sidebands change the waveform as they die, which moves a
        // zero-crossing count without the pitch moving at all
        for (Instrument instrument : new Instrument[] {Instrument.GLASS, Instrument.FLUTE, Instrument.PAD})
        {
            final NoteEvent note = new NoteEvent(0d, 9d, 57, 1f, instrument, 0f);
            final float[][] audio = renderNotes(recipes, 12d, List.of(note));

            // the note starts when the first rest is over, so its own clock starts there
            final float[] samples = Arrays.copyOfRange(audio[0], (int) (SoulSynth.FIRST_REST * RATE), audio[0].length);
            final double early = pitchOf(samples, RATE / 2, RATE / 2 + RATE);
            final double late = pitchOf(samples, 7 * RATE, 8 * RATE);

            // the old glass vibrato multiplied elapsed time, and swung a third of the pitch by here
            assertEquals(early, late, early * 0.02d,
                    bank + " " + instrument + " went from " + early + " Hz to " + late + " Hz");
        }
    }

    @Test
    @DisplayName("nothing beats or wobbles faster than warmth: the pad's detune and the flute's vibrato stay small")
    void noWarble()
    {
        for (double detune : SoulSynth.Pad.DETUNE)
        {
            assertTrue(Math.abs(detune) <= 0.002d, "a pad detuned " + detune + " beats audibly on a held chord");
        }

        assertTrue(SoulSynth.Pad.SWEEP <= 0.1d);
        assertTrue(SoulSynth.Flute.VIBRATO <= 0.002d);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Bank.class)
    @DisplayName("the foley is heard, and never louder than the music it is part of")
    void foleySitsUnderTheMusic(Bank bank) throws IOException
    {
        final SampleBank recipes = bank.load();

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.3d, 5L);
            final double music = rmsDb(render(brief, recipes, 70d, event -> event.instrument().pitched()),
                    7 * RATE, 70 * RATE);
            final double foley = rmsDb(render(brief, recipes, 70d, event -> !event.instrument().pitched()),
                    7 * RATE, 70 * RATE);

            assertTrue(foley <= music + 1d, bank + " " + voice + ": foley at " + foley + " dB over music at " + music);
            assertTrue(foley >= music - 22d,
                    bank + " " + voice + ": foley at " + foley + " dB is lost under music at " + music);
        }
    }

    /**
     * The oscillators only. The bounds were set for them behind their 6 kHz lowpass, and the
     * recorded bank has natural air above that: its own bound is {@link #nothingPiercingRecorded}.
     */
    @Test
    @DisplayName("nothing is piercing: the tune's energy sits low, and so does the whole mix (oscillators)")
    void nothingPiercing()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, 5L);
            final double music = brightness(render(brief, SampleBank.EMPTY, 60d, event -> event.instrument().pitched()));
            final double mix = brightness(render(brief, SampleBank.EMPTY, 60d, event -> true));

            // RMS frequency - where the energy sits. The shipped music before #261 put cold at 1.6 kHz
            assertTrue(music < 900d, voice + "'s notes sit at " + music + " Hz");
            assertTrue(mix < 1_300d, voice + "'s mix sits at " + mix + " Hz");
        }
    }

    /**
     * The recorded bank's own bound, which is not the oscillators'. Real recordings carry air above
     * 5 kHz - cabasa, the rub of a glass, a cymbal - and the bound is on how much of the mix's energy
     * is up there, not on the frequency a loud sound happens to sit at.
     */
    @Test
    @DisplayName("nothing is piercing in the recordings: little energy above 5 kHz, and the tune sits low")
    void nothingPiercingRecorded() throws IOException
    {
        final SampleBank recipes = Bank.SHIPPED.load();

        for (SoulVoice voice : SoulVoice.values())
        {
            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, 5L);
            final float[][] pitched = render(brief, recipes, 60d, event -> event.instrument().pitched());
            final float[][] mix = render(brief, recipes, 60d, event -> true);

            final double tune = brightness(pitched);
            final double share = highShare(mix, 5_000d);

            assertTrue(tune < 1_200d, voice + "'s notes sit at " + tune + " Hz");
            assertTrue(share < 0.03d, voice + "'s mix has " + (100d * share) + "% of its energy above 5 kHz");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    private static float[][] render(SoulMusicBrief brief, SampleBank bank, double seconds)
    {
        return render(brief, bank, seconds, event -> true);
    }

    private static float[][] render(SoulMusicBrief brief, SampleBank bank, double seconds,
            Predicate<NoteEvent> keep)
    {
        final int frames = (int) (seconds * RATE);
        final float[] left = new float[frames];
        final float[] right = new float[frames];

        new SoulSynth(index ->
        {
            final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

            return new SoulComposer.Piece(piece.voice(), piece.support(), piece.mode(), piece.tonic(), piece.bpm(),
                    piece.space(), piece.length(), piece.events().stream().filter(keep).toList());
        }, 17L, bank).render(left, right, frames);

        return new float[][] {left, right};
    }

    /** The first piece of a base brief, with its own notes replaced by {@code events}. */
    private static float[][] renderNotes(SampleBank bank, double seconds, List<NoteEvent> events)
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.BASE, 1d), 0.5d, 1L);
        final int frames = (int) (seconds * RATE);
        final float[] left = new float[frames];
        final float[] right = new float[frames];

        new SoulSynth(index ->
        {
            final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

            return new SoulComposer.Piece(piece.voice(), piece.support(), piece.mode(), piece.tonic(), piece.bpm(),
                    piece.space(), piece.length(), events);
        }, 17L, bank).render(left, right, frames);

        return new float[][] {left, right};
    }

    private static SoulSynth synth(SoulMusicBrief brief, SampleBank bank)
    {
        return new SoulSynth(index -> SoulComposer.compose(brief, brief.pieceSeed(index)), 17L, bank);
    }

    // ---------------------------------------------------------------------------------------------
    // Measurements
    // ---------------------------------------------------------------------------------------------

    /** RMS frequency, from the ratio of the signal's slope to the signal: where its energy sits. */
    private static double brightness(float[][] audio)
    {
        double level = 0d;
        double slope = 0d;

        for (int index = 7 * RATE; index < audio[0].length; index++)
        {
            level += audio[0][index] * audio[0][index];

            final double step = audio[0][index] - audio[0][index - 1];

            slope += step * step;
        }

        return RATE / (2d * Math.PI) * Math.sqrt(slope / Math.max(1e-30d, level));
    }

    /**
     * The share of the mono sum's spectral energy above {@code cutoff}, from seven seconds on, in
     * 4096-point Hann frames hopped by 2048. A fraction, not a percentage.
     */
    private static double highShare(float[][] audio, double cutoff)
    {
        final int size = 4096;
        final int hop = 2048;
        final int frames = audio[0].length;
        double total = 0d;
        double high = 0d;

        for (int start = 7 * RATE; start + size <= frames; start += hop)
        {
            final double[] re = new double[size];
            final double[] im = new double[size];

            for (int index = 0; index < size; index++)
            {
                final double hann = 0.5d - 0.5d * Math.cos(2d * Math.PI * index / (size - 1));

                re[index] = 0.5d * (audio[0][start + index] + audio[1][start + index]) * hann;
            }

            fft(re, im);

            for (int bin = 1; bin < size / 2; bin++)
            {
                final double power = re[bin] * re[bin] + im[bin] * im[bin];

                total += power;

                if (bin * (double) RATE / size > cutoff)
                {
                    high += power;
                }
            }
        }

        return total <= 0d ? 0d : high / total;
    }

    /** In-place iterative radix-2 FFT. The length must be a power of two. */
    private static void fft(double[] re, double[] im)
    {
        final int n = re.length;

        for (int index = 1, j = 0; index < n; index++)
        {
            int bit = n >> 1;

            for (; (j & bit) != 0; bit >>= 1)
            {
                j ^= bit;
            }

            j ^= bit;

            if (index < j)
            {
                double swap = re[index];

                re[index] = re[j];
                re[j] = swap;

                swap = im[index];
                im[index] = im[j];
                im[j] = swap;
            }
        }

        for (int len = 2; len <= n; len <<= 1)
        {
            final double angle = -2d * Math.PI / len;

            for (int start = 0; start < n; start += len)
            {
                for (int k = 0; k < len / 2; k++)
                {
                    final double wr = Math.cos(angle * k);
                    final double wi = Math.sin(angle * k);
                    final int a = start + k;
                    final int b = a + len / 2;
                    final double tr = re[b] * wr - im[b] * wi;
                    final double ti = re[b] * wi + im[b] * wr;

                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }

    /**
     * The fundamental over a window, by the lag of its strongest autocorrelation between 80 Hz and
     * 1 kHz - which, unlike counting zero crossings, a changing timbre does not move.
     */
    private static double pitchOf(float[] samples, int from, int to)
    {
        int bestLag = 1;
        double best = Double.NEGATIVE_INFINITY;

        for (int lag = RATE / 1_000; lag <= RATE / 80; lag++)
        {
            double sum = 0d;

            for (int index = from; index + lag < to; index++)
            {
                sum += samples[index] * samples[index + lag];
            }

            if (sum > best * 1.0001d)
            {
                best = sum;
                bestLag = lag;
            }
        }

        return RATE / (double) bestLag;
    }

    private static float[][] slice(float[][] audio, int from, int to)
    {
        return new float[][] {Arrays.copyOfRange(audio[0], from, to), Arrays.copyOfRange(audio[1], from, to)};
    }

    private static double rmsDb(float[][] audio, int from, int to)
    {
        double sum = 0d;

        for (int index = from; index < to; index++)
        {
            sum += audio[0][index] * audio[0][index] + audio[1][index] * audio[1][index];
        }

        return 10d * Math.log10(sum / (2d * Math.max(1, to - from)) + 1e-30d);
    }

    private static double peak(float[][] audio)
    {
        double peak = 0d;

        for (float[] channel : audio)
        {
            for (float sample : channel)
            {
                peak = Math.max(peak, Math.abs(sample));
            }
        }

        return peak;
    }
}
