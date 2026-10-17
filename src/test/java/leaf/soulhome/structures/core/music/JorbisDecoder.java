/* File created ~ 17 - 10 - 2026 */

package leaf.soulhome.structures.core.music;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decodes the shipped Ogg Vorbis recordings with JOrbis, for the tests (#163). The game decodes with
 * its own decoder; this exists so a test can read the bank the way the client does, offline.
 *
 * <p>Reads from the source tree rather than the classpath, the way {@code AmbienceAssetsTest}
 * reads the shipped resources, so what is checked is what actually ships. Channels are
 * averaged to mono, as the {@link SampleBank.Decoder} contract asks, but the source's channel count
 * and rate are kept so a test can assert the shipped files are what the manifest says they are.
 */
final class JorbisDecoder implements SampleBank.Decoder
{
    static final Path ROOT = Path.of("src", "main", "resources", "assets", "soulhome", "soul_music");

    private static final Map<String, Decoded> CACHE = new ConcurrentHashMap<>();

    private static SampleBank shipped;

    /** Mono samples in [-1, 1], plus what the file itself said it was. */
    record Decoded(float[] samples, int sampleRate, int channels)
    {
    }

    @Override
    public float[] decode(String file) throws IOException
    {
        return decoded(file).samples();
    }

    /** One file, decoded once and then remembered for the rest of the test run. */
    static Decoded decoded(String file) throws IOException
    {
        final Decoded cached = CACHE.get(file);

        if (cached != null)
        {
            return cached;
        }

        final Decoded decoded = read(ROOT.resolve(file));

        CACHE.put(file, decoded);

        return decoded;
    }

    /** The shipped {@code bank.json}, parsed once. Fails if the bank has not been produced yet. */
    static synchronized SampleBank shipped() throws IOException
    {
        if (shipped == null)
        {
            try (Reader reader = Files.newBufferedReader(ROOT.resolve("bank.json"), StandardCharsets.UTF_8))
            {
                shipped = SampleBank.parse(reader, new JorbisDecoder());
            }
        }

        return shipped;
    }

    private static Decoded read(Path path) throws IOException
    {
        final byte[] bytes = Files.readAllBytes(path);

        final SyncState sync = new SyncState();
        sync.init();

        final int index = sync.buffer(bytes.length);
        System.arraycopy(bytes, 0, sync.data, index, bytes.length);
        sync.wrote(bytes.length);

        final StreamState stream = new StreamState();
        final Page page = new Page();
        final Packet packet = new Packet();
        final Info info = new Info();
        final Comment comment = new Comment();
        final DspState dsp = new DspState();
        final Block block = new Block(dsp);

        info.init();
        comment.init();

        boolean started = false;
        int headers = 0;
        final List<float[]> chunks = new ArrayList<>();
        int frames = 0;

        int paged;

        while ((paged = sync.pageout(page)) != 0)
        {
            if (paged < 0)
            {
                throw new IOException(path + ": corrupt Ogg page");
            }

            if (!started)
            {
                stream.init(page.serialno());
                started = true;
            }

            if (stream.pagein(page) != 0)
            {
                throw new IOException(path + ": page does not belong to its stream");
            }

            int packeted;

            while ((packeted = stream.packetout(packet)) != 0)
            {
                if (packeted < 0)
                {
                    throw new IOException(path + ": corrupt Vorbis packet");
                }

                if (headers < 3)
                {
                    if (info.synthesis_headerin(comment, packet) < 0)
                    {
                        throw new IOException(path + ": not a Vorbis header");
                    }

                    headers++;

                    if (headers == 3)
                    {
                        dsp.synthesis_init(info);
                        block.init(dsp);
                    }

                    continue;
                }

                if (block.synthesis(packet) == 0)
                {
                    dsp.synthesis_blockin(block);
                }

                final float[][][] pcm = new float[1][][];
                final int[] offsets = new int[info.channels];
                int available;

                while ((available = dsp.synthesis_pcmout(pcm, offsets)) > 0)
                {
                    final float[] mono = new float[available];

                    for (int channel = 0; channel < info.channels; channel++)
                    {
                        for (int frame = 0; frame < available; frame++)
                        {
                            mono[frame] += pcm[0][channel][offsets[channel] + frame] / info.channels;
                        }
                    }

                    chunks.add(mono);
                    frames += available;
                    dsp.synthesis_read(available);
                }
            }
        }

        if (headers < 3)
        {
            throw new IOException(path + ": no Vorbis headers");
        }

        final float[] samples = new float[frames];
        int at = 0;

        for (float[] chunk : chunks)
        {
            System.arraycopy(chunk, 0, samples, at, chunk.length);
            at += chunk.length;
        }

        return new Decoded(samples, info.rate, info.channels);
    }
}
