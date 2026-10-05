/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
 * <p>Each voice renders its first piece, a minute and a half of audio apiece; the whole class is a
 * few seconds, because the synth runs at forty-odd times realtime.
 */
class SoulSynthTest
{
    private static final int RATE = SoulSynth.SAMPLE_RATE;

    @Test
    @DisplayName("every voice plays at about the level of Minecraft's own music, and never reaches full scale")
    void levels()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            final double[] levels = new double[2];

            for (int step = 0; step < 2; step++)
            {
                final float[][] audio = render(new SoulMusicBrief(Map.of(voice, 1d), step, 5L), 90d);
                final double body = rmsDb(audio, (int) (SoulSynth.FIRST_REST * RATE) + 4 * RATE, audio[0].length);
                final double peak = peak(audio);

                assertTrue(body > -28d && body < -16d,
                        voice + " at rank fraction " + step + " plays at " + body + " dBFS RMS");
                assertTrue(peak < 0.96d, voice + " peaks at " + peak);

                levels[step] = body;
            }

            // larger, never louder (#216): a longer tail at the same gain was ten decibels up at the
            // top of the ladder before the reverb was made to hold its energy
            assertTrue(Math.abs(levels[1] - levels[0]) < 4d,
                    voice + " is " + (levels[1] - levels[0]) + " dB louder at the ceiling than at rank 0");
        }
    }

    @Test
    @DisplayName("nothing plays before the first piece, and a piece is followed by a rest")
    void itRests()
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.BASE, 1d), 0d, 1L);
        final SoulComposer.Piece first = SoulComposer.compose(brief, brief.pieceSeed(0));

        // long enough to be well past the first piece's tail and still inside the shortest rest
        final double seconds = SoulSynth.FIRST_REST + first.length() + SoulSynth.MIN_REST * 0.8d;
        final float[][] audio = render(brief, seconds);

        assertEquals(0d, peak(slice(audio, 0, (int) (SoulSynth.FIRST_REST * RATE) - 1)), 1e-9d,
                "a player arriving hears the place for a moment before the music");

        final int restStart = (int) ((SoulSynth.FIRST_REST + first.length() + 2d) * RATE);
        final double rest = rmsDb(audio, restStart, audio[0].length);

        assertTrue(rest < -60d, "the rest after a piece is a rest, not a quieter piece: " + rest + " dBFS");
    }

    @Test
    @DisplayName("the same seed renders the same samples")
    void deterministic()
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.WROUGHT, 1d), 0.5d, 3L);

        final float[][] first = render(brief, 20d);
        final float[][] second = render(brief, 20d);

        assertArrayEquals(first[0], second[0]);
        assertArrayEquals(first[1], second[1]);
    }

    @Test
    @DisplayName("render block size does not change what is rendered")
    void blockSizeIndependent()
    {
        final SoulMusicBrief brief = new SoulMusicBrief(Map.of(SoulVoice.ARCANE, 1d), 0.3d, 9L);
        final int frames = 12 * RATE;

        final SoulSynth whole = synth(brief);
        final float[] wholeLeft = new float[frames];
        final float[] wholeRight = new float[frames];

        whole.render(wholeLeft, wholeRight, frames);

        final SoulSynth pieces = synth(brief);
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

        assertArrayEquals(wholeLeft, left);
        assertArrayEquals(wholeRight, right);
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

    @Test
    @DisplayName("a held note keeps its pitch: glass and flute drift no more at eight seconds than at one (#261)")
    void heldNotesHoldStill()
    {
        // the bell is left out: its FM sidebands change the waveform as they die, which moves a
        // zero-crossing count without the pitch moving at all
        for (Instrument instrument : new Instrument[] {Instrument.GLASS, Instrument.FLUTE, Instrument.PAD})
        {
            final SoulSynth.Voice voice = SoulSynth.Voice.of(
                    new NoteEvent(0d, 9d, 57, 1f, instrument, 0f), 0.5d, 1L);
            final float[] samples = new float[9 * RATE];

            for (int index = 0; index < samples.length; index++)
            {
                samples[index] = voice.next();
            }

            final double early = pitchOf(samples, RATE / 2, RATE / 2 + RATE);
            final double late = pitchOf(samples, 7 * RATE, 8 * RATE);

            // the old glass vibrato multiplied elapsed time, and swung a third of the pitch by here
            assertEquals(early, late, early * 0.02d, instrument + " went from " + early + " Hz to " + late + " Hz");
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

    @Test
    @DisplayName("the foley is heard, and never louder than the music it is part of")
    void foleySitsUnderTheMusic()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.3d, 5L);
            final double music = rmsDb(render(brief, 70d, event -> event.instrument().pitched()), 7 * RATE, 70 * RATE);
            final double foley = rmsDb(render(brief, 70d, event -> !event.instrument().pitched()), 7 * RATE, 70 * RATE);

            assertTrue(foley <= music + 1d, voice + ": foley at " + foley + " dB over music at " + music);
            assertTrue(foley >= music - 22d, voice + ": foley at " + foley + " dB is lost under music at " + music);
        }
    }

    @Test
    @DisplayName("nothing is piercing: the tune's energy sits low, and so does the whole mix")
    void nothingPiercing()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            final SoulMusicBrief brief = new SoulMusicBrief(Map.of(voice, 1d), 0.5d, 5L);
            final double music = brightness(render(brief, 60d, event -> event.instrument().pitched()));
            final double mix = brightness(render(brief, 60d, event -> true));

            // RMS frequency - where the energy sits. The shipped music before #261 put cold at 1.6 kHz
            assertTrue(music < 900d, voice + "'s notes sit at " + music + " Hz");
            assertTrue(mix < 1_300d, voice + "'s mix sits at " + mix + " Hz");
        }
    }

    private static float[][] render(SoulMusicBrief brief, double seconds, Predicate<NoteEvent> keep)
    {
        final int frames = (int) (seconds * RATE);
        final float[] left = new float[frames];
        final float[] right = new float[frames];

        new SoulSynth(index ->
        {
            final SoulComposer.Piece piece = SoulComposer.compose(brief, brief.pieceSeed(index));

            return new SoulComposer.Piece(piece.voice(), piece.support(), piece.mode(), piece.tonic(), piece.bpm(),
                    piece.space(), piece.length(), piece.events().stream().filter(keep).toList());
        }, 17L).render(left, right, frames);

        return new float[][] {left, right};
    }

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

    private static SoulSynth synth(SoulMusicBrief brief)
    {
        return new SoulSynth(index -> SoulComposer.compose(brief, brief.pieceSeed(index)), 17L);
    }

    private static float[][] render(SoulMusicBrief brief, double seconds)
    {
        final int frames = (int) (seconds * RATE);
        final float[] left = new float[frames];
        final float[] right = new float[frames];

        synth(brief).render(left, right, frames);

        return new float[][] {left, right};
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
