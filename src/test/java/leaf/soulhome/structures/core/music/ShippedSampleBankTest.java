/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bank that ships, read the way the client reads it (#163). Where {@link SampleBankTest} pins
 * the rules with synthetic recordings, this pins the actual files: that every instrument is
 * recorded, every recording is there and decodes, and every pitched instrument can be played across
 * the whole range the composer writes in.
 *
 * <p>Fails, rather than skips, while the bank has not been produced: a soul's music is not allowed
 * to go quietly back to oscillators because a file was forgotten.
 */
class ShippedSampleBankTest
{
    private static final int LOWEST = 24;
    private static final int HIGHEST = 96;

    @Test
    @DisplayName("every instrument has an entry in the shipped bank")
    void everyInstrumentIsRecorded() throws IOException
    {
        final SampleBank bank = JorbisDecoder.shipped();

        for (Instrument instrument : Instrument.values())
        {
            assertTrue(bank.has(instrument), instrument + " has no entry in bank.json");
        }
    }

    @Test
    @DisplayName("every zone's file exists, decodes, and is mono at the synth's rate")
    void everyFileIsMonoAtTheSynthRate() throws IOException
    {
        final SampleBank bank = JorbisDecoder.shipped();

        for (Instrument instrument : Instrument.values())
        {
            for (SampleBank.Zone zone : bank.kit(instrument).zones)
            {
                final String where = instrument + " " + zone.file;

                assertTrue(Files.isRegularFile(JorbisDecoder.ROOT.resolve(zone.file)), where + " is missing");

                final JorbisDecoder.Decoded decoded = JorbisDecoder.decoded(zone.file);

                assertEquals(1, decoded.channels(), where + " is not mono");
                assertEquals(SoulSynth.SAMPLE_RATE, decoded.sampleRate(), where + " is not at the synth's rate");
                assertTrue(decoded.samples().length > 0, where + " decodes to nothing");
            }
        }
    }

    @Test
    @DisplayName("every loop sits inside its own recording")
    void loopPointsAreInRange() throws IOException
    {
        final SampleBank bank = JorbisDecoder.shipped();

        for (Instrument instrument : Instrument.values())
        {
            for (SampleBank.Zone zone : bank.kit(instrument).zones)
            {
                if (!zone.looped())
                {
                    continue;
                }

                final int length = JorbisDecoder.decoded(zone.file).samples().length;
                final String where = instrument + " " + zone.file;

                assertTrue(zone.loopStart >= 0 && zone.loopStart < zone.loopEnd,
                        where + " loops " + zone.loopStart + ".." + zone.loopEnd);
                assertTrue(zone.loopEnd <= length, where + " loops past its " + length + " frames");
            }
        }
    }

    @Test
    @DisplayName("every pitched instrument has a recording for every note from C1 to C7, with no gap")
    void pitchedInstrumentsCoverTheRange() throws IOException
    {
        final SampleBank bank = JorbisDecoder.shipped();

        for (Instrument instrument : Instrument.values())
        {
            if (!instrument.pitched())
            {
                continue;
            }

            final boolean[] covered = new boolean[128];

            for (SampleBank.Zone zone : bank.kit(instrument).zones)
            {
                for (int midi = zone.low; midi <= zone.high; midi++)
                {
                    covered[midi] = true;
                }
            }

            for (int midi = LOWEST; midi <= HIGHEST; midi++)
            {
                assertTrue(covered[midi], instrument + " has no zone for MIDI " + midi);
            }
        }
    }
}
