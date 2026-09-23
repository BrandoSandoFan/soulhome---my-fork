/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

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
