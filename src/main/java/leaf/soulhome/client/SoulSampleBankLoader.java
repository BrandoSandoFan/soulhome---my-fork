/*
 * File created ~ 17 - 10 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.core.music.SampleBank;
import leaf.soulhome.structures.core.music.SoulSynth;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Loads the soul's recorded instrument bank (#163) for {@link SoulMusicPlayer} to play from.
 *
 * <h2>Why the game's own Ogg decoder</h2>
 *
 * <p>{@link JOrbisAudioStream} is what vanilla's sound engine uses for every {@code .ogg} it plays, so
 * a sample decodes here exactly as a pack author would hear it in the game. Pulling in a second Vorbis
 * decoder would add a jar for the same job, and a second place where a file could decode differently.
 *
 * <h2>Why Minecraft's resource manager, not the reload's</h2>
 *
 * <p>The reload hands its listener a resource manager of its own, and the next reload closes it. A
 * decode still running when a second reload starts would then read closed packs. Minecraft's manager
 * is the one the sound engine reads through, and it always delegates to the packs of the latest reload,
 * so the decode reads what the game is using at the moment it reads.
 *
 * <h2>Threading</h2>
 *
 * <p>Decoding some minutes of Vorbis is not something to do on the render thread, so the work runs on
 * {@link Util#backgroundExecutor()}. The reload's listener itself runs on the render thread (the reload
 * hands it over with {@code thenRunAsync} on the game executor), and only drops the old bank and starts
 * the new load - it never waits for it. A reload that starts while a load is still decoding issues a new
 * ticket, and the older result is thrown away when it arrives.
 *
 * <h2>Why failure is {@link SampleBank#EMPTY}, not no music</h2>
 *
 * <p>A pack with a broken file must not cost a player their music. {@link SampleBank#EMPTY} plays
 * every instrument from its oscillator recipe, which is what the soul sounded like before the bank
 * existed, so a failure is a quieter-sounding soul rather than a silent one.
 */
public final class SoulSampleBankLoader
{
    /** The manifest, the same place {@link SampleBank} documents it. */
    private static final ResourceLocation MANIFEST = ResourceLocation.fromNamespaceAndPath(SoulHome.MODID, "soul_music/bank.json");

    /** Where the manifest's file names are relative to. */
    private static final String FOLDER = "soul_music/";

    /**
     * Guards {@link #bank} and {@link #ticket} together. A load's result is applied only if no later
     * reload has been issued since it started, so an old pack's decode cannot overwrite the new one.
     */
    private static final Object LOCK = new Object();

    private static int ticket;

    /** Null while the current reload's bank is still decoding. Read on the render thread. */
    private static volatile SampleBank bank;

    /**
     * Registered with NeoForge's {@code RegisterClientReloadListenersEvent} - see {@code ClientRegistry}.
     * Called on the render thread once per reload, after the reload has swapped in its new packs.
     */
    public static final ResourceManagerReloadListener LISTENER = ignored -> reload();

    private SoulSampleBankLoader()
    {
    }

    /**
     * The loaded bank: {@link SampleBank#EMPTY} if loading failed, or null while it is still decoding.
     * A null means "not yet", and the caller should ask again on a later tick rather than start
     * without the recordings.
     */
    static SampleBank current()
    {
        return bank;
    }

    private static void reload()
    {
        // read on the render thread, where Minecraft's own field is safe to read
        final ResourceManager manager = Minecraft.getInstance().getResourceManager();
        final int issued;

        synchronized (LOCK)
        {
            issued = ++ticket;
            bank = null;
        }

        CompletableFuture.supplyAsync(() -> load(manager), Util.backgroundExecutor())
                .whenComplete((loaded, failure) ->
                {
                    // load() already turns its own failures into EMPTY; this catches anything it did not
                    // expect, an error included, so the future can never be left without an answer
                    final SampleBank result = failure == null ? loaded : failed(failure);

                    synchronized (LOCK)
                    {
                        if (issued == ticket)
                        {
                            bank = result;
                        }
                    }
                });
    }

    /** Reads the manifest and decodes what it names. Never throws. */
    static SampleBank load(ResourceManager manager)
    {
        try (Reader reader = new InputStreamReader(open(manager, MANIFEST), StandardCharsets.UTF_8))
        {
            return SampleBank.parse(reader, file -> decode(manager, file));
        }
        catch (IOException | RuntimeException e)
        {
            return failed(e);
        }
    }

    /**
     * One recording as mono float PCM at {@link SoulSynth#SAMPLE_RATE}. Stereo is averaged to mono,
     * which is what {@link SampleBank.Decoder} promises.
     *
     * <p>Samples are taken from {@link JOrbisAudioStream#readChunk} as floats, not from {@code readAll()}:
     * that path would round every sample to 16 bits and then read it back, for no gain.
     */
    static float[] decode(ResourceManager manager, String file) throws IOException
    {
        final ResourceLocation location = ResourceLocation.fromNamespaceAndPath(SoulHome.MODID, FOLDER + file);

        // the stream closes the input it was given, but the input is still opened here so that a failure
        // in the constructor, before the stream exists to close it, does not leak the file
        try (InputStream input = open(manager, location); JOrbisAudioStream ogg = new JOrbisAudioStream(input))
        {
            final AudioFormat format = ogg.getFormat();

            // a sample at any other rate would play at the wrong pitch, and resampling here would be a
            // second place to get the audio wrong - so the pack is told which file to fix
            if (format.getSampleRate() != SoulSynth.SAMPLE_RATE)
            {
                throw new IOException(file + " is " + format.getSampleRate() + " Hz, not " + SoulSynth.SAMPLE_RATE);
            }

            final int channels = format.getChannels();

            if (channels < 1)
            {
                throw new IOException(file + " has no audio channels");
            }

            final MonoSink sink = new MonoSink(channels);

            while (ogg.readChunk(sink::accept))
            {
                // every chunk is pushed into the sink as it is decoded
            }

            return sink.finish();
        }
    }

    /**
     * Folds JOrbis's interleaved samples into mono frames as they arrive. Growing one array keeps a whole
     * recording's worth of samples in a single pass, rather than decoding to bytes first and reading back.
     */
    private static final class MonoSink
    {
        private final int channels;
        private float[] mono = new float[1 << 16];
        private int frames;
        private int slot;
        private float sum;

        private MonoSink(int channels)
        {
            this.channels = channels;
        }

        private void accept(float sample)
        {
            this.sum += sample;

            if (++this.slot < this.channels)
            {
                return;
            }

            if (this.frames == this.mono.length)
            {
                this.mono = Arrays.copyOf(this.mono, this.mono.length * 2);
            }

            this.mono[this.frames++] = this.sum / this.channels;
            this.sum = 0f;
            this.slot = 0;
        }

        private float[] finish()
        {
            return Arrays.copyOf(this.mono, this.frames);
        }
    }

    private static InputStream open(ResourceManager manager, ResourceLocation location) throws IOException
    {
        final Resource resource = manager.getResource(location)
                .orElseThrow(() -> new IOException("no such resource " + location));

        return resource.open();
    }

    private static SampleBank failed(Throwable cause)
    {
        LogHelper.LOGGER.warn("Soul music samples did not load, so the soul plays from its oscillators instead", cause);

        return SampleBank.EMPTY;
    }
}
