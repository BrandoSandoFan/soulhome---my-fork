/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a looped recording behaves while it is held and released (#163). The shipped looped files end
 * exactly at their loop point, so these tests use a recording of that shape: a sine of a whole number
 * of periods, cut at its loop end, with nothing after it.
 */
class SampleVoiceLoopTest
{
    private static final int RATE = SoulSynth.SAMPLE_RATE;

    /** 441 Hz is exactly 100 frames a period at 44.1 kHz, so 4400 frames is 44 whole periods. */
    private static final int LOOP = 4400;

    /** Five whole periods: any block of this many frames holds the same RMS wherever it starts. */
    private static final int BLOCK = 500;

    private static final double HOLD = 0.5d;
    private static final double RELEASE = 0.5d;

    /** Six semitones up: a step of root 2, so the taps at the seam sit between recorded frames. */
    private static final int FRACTIONAL_MIDI = 66;

    @Test
    @DisplayName("a held loop has no seam click, releases smoothly, and lasts duration plus release")
    void heldLoopReleasesWithoutAClick() throws IOException
    {
        final float[] cycle = sine(441d, LOOP);

        for (int midi : new int[] {60, FRACTIONAL_MIDI})
        {
            final SampleBank bank = bank(sustain(), loop(LOOP), cycle);
            final float[] out = renderUntilDone(voice(bank, midi), 2 * RATE);

            // a sine's own largest step is the yardstick: the loop may not add a step four times bigger
            final double sourceJump = largestStep(cycle, cycle.length);
            final double outputJump = largestStep(out, out.length);

            assertTrue(outputJump <= 4d * sourceJump,
                    "midi " + midi + ": a step of " + outputJump + " against the source's " + sourceJump);

            assertTrue(Math.abs(out.length - (HOLD + RELEASE) * RATE) <= BLOCK,
                    "midi " + midi + ": lasts " + out.length + " frames, not duration plus release");

            // the held note is at full level all through its loops: the release is not started early
            final double held = rms(out, (int) (0.1 * RATE), (int) (0.4 * RATE));

            assertTrue(held > 0.5d && held < 0.6d, "midi " + midi + ": held level " + held);

            // the release is monotone, block by block: each full block of it is no louder than the last
            final int releaseStart = (int) Math.round(HOLD * RATE);
            double previous = Double.POSITIVE_INFINITY;

            for (int from = releaseStart; from + BLOCK <= out.length; from += BLOCK)
            {
                final double block = rms(out, from, from + BLOCK);

                assertTrue(block <= previous + 1e-9d, "midi " + midi + ": block at " + from + " rises to " + block);

                previous = block;
            }

            assertTrue(previous < 1e-3d, "midi " + midi + ": the release ends in silence: " + previous);
        }
    }

    @Test
    @DisplayName("a constant recording stays constant through every wrap, so there is no dip at the seam")
    void constantLoopHasNoDipAtTheSeam() throws IOException
    {
        // at a fractional step each wrap reads taps past the loop end; before the fix those read silence
        final SampleBank bank = bank(sustain(), loop(LOOP), constant(0.5d, LOOP));
        final float[] out = renderUntilDone(voice(bank, FRACTIONAL_MIDI), RATE);
        final int hold = (int) Math.round(HOLD * RATE);

        for (int frame = 0; frame < hold; frame++)
        {
            assertEquals(0.5f, out[frame], 1e-4f, "frame " + frame + " dips at a wrap");
        }
    }

    @Test
    @DisplayName("a sine whose period divides the loop stays on that sine through every wrap")
    void sineStaysOnTheSineThroughEveryWrap() throws IOException
    {
        final SampleBank bank = bank(sustain(), loop(LOOP), sine(441d, LOOP));
        final float[] out = renderUntilDone(voice(bank, FRACTIONAL_MIDI), RATE);
        final int hold = (int) Math.round(HOLD * RATE);

        // the note reads the source at n times its step, and the loop is a whole number of periods, so
        // the expected output is the sine itself, however many times the read has come round
        final double step = Math.pow(2d, (FRACTIONAL_MIDI - 60) / 12d);

        for (int frame = 0; frame < hold; frame++)
        {
            final double expected = 0.8d * Math.sin(2d * Math.PI * 441d * (frame * step) / RATE);

            assertEquals(expected, out[frame], 2e-3d, "frame " + frame + " has left the sine");
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** A sustained, tracked pad with a release of {@link #RELEASE} seconds. */
    private static String sustain()
    {
        return "\"envelope\": \"sustain\", \"release\": " + RELEASE + ", \"track_pitch\": true";
    }

    private static String loop(int frames)
    {
        return "\"loop_start\": 0, \"loop_end\": " + frames;
    }

    private static SampleBank bank(String instrumentFields, String zoneFields, float[] recording) throws IOException
    {
        final String json = "{\"format\": 1, \"sample_rate\": 44100, \"instruments\": {\"pad\": {"
                + instrumentFields + ", \"zones\": [{\"file\": \"x.ogg\", \"root\": 60, " + zoneFields + "}]}}}";

        return SampleBank.parse(new StringReader(json), file -> recording);
    }

    /** A pad note of {@link #HOLD} seconds at {@code midi}, from the bank's one recording. */
    private static SampleVoice voice(SampleBank bank, int midi)
    {
        final NoteEvent note = new NoteEvent(0d, HOLD, midi, 1f, Instrument.PAD, 0f);

        return SampleVoice.play(note, bank.kit(Instrument.PAD), 1L);
    }

    /** Renders until the voice reports done, or the limit, and returns the frames up to and including it. */
    private static float[] renderUntilDone(SampleVoice voice, int limit)
    {
        final float[] out = new float[limit];
        int count = 0;

        while (count < limit)
        {
            out[count++] = voice.next();

            if (voice.done())
            {
                break;
            }
        }

        return Arrays.copyOf(out, count);
    }

    private static float[] sine(double frequency, int frames)
    {
        final float[] samples = new float[frames];

        for (int index = 0; index < frames; index++)
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

    private static double largestStep(float[] samples, int frames)
    {
        double largest = 0d;

        for (int index = 1; index < frames; index++)
        {
            largest = Math.max(largest, Math.abs(samples[index] - samples[index - 1]));
        }

        return largest;
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
}
