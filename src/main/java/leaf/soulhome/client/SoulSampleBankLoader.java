/*
 * File created ~ 17 - 10 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.core.music.SampleBank;
import leaf.soulhome.structures.core.music.SoulSynth;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Loads the soul's recorded instrument bank (#163) for {@link SoulMusicPlayer} to play from.
 *
 * <h2>Why the game's own Ogg decoder</h2>
 *
 * <p>{@link OggAudioStream} is what vanilla uses for every {@code .ogg} it plays, so a sample decodes
 * here exactly as a pack author would hear it in the game. Pulling in a second Vorbis decoder would
 * add a jar for the same job, and a second place where a file could decode differently.
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
 * {@link Util#backgroundExecutor()}. The reload's listener itself runs on the render thread, and only
 * drops the old bank and starts the new load - it never waits for it. A reload that starts while a
 * load is still decoding issues a new ticket, and the older result is thrown away when it arrives.
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
    private static final ResourceLocation MANIFEST = new ResourceLocation(SoulHome.MODID, "soul_music/bank.json");

    /** Where the manifest's file names are relative to. */
    private static final String FOLDER = "soul_music/";

    /**
     * Full scale of a 16-bit sample. Dividing by 32768 rather than 32767 maps the whole range onto
     * [-1, 1): -32768 is exactly -1, and the largest positive value stays just under 1.
     */
    private static final float FULL_SCALE = 32_768f;

    /**
     * Guards {@link #bank} and {@link #ticket} together. A load's result is applied only if no later
     * reload has been issued since it started, so an old pack's decode cannot overwrite the new one.
     */
    private static final Object LOCK = new Object();

    private static int ticket;

    /** Null while the current reload's bank is still decoding. Read on the render thread. */
    private static volatile SampleBank bank;

    /**
     * Registered with Forge's {@code RegisterClientReloadListenersEvent} - see {@code ClientRegistry}.
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
     */
    static float[] decode(ResourceManager manager, String file) throws IOException
    {
        final ResourceLocation location = new ResourceLocation(SoulHome.MODID, FOLDER + file);

        // the stream closes the input it was given, but the input is still opened here so that a failure
        // in the constructor, before the stream exists to close it, does not leak the file
        try (InputStream input = open(manager, location); OggAudioStream ogg = new OggAudioStream(input))
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

            // OggAudioStream writes its shorts in native order, so they are read back in native order too
            final ShortBuffer shorts = ogg.readAll().order(ByteOrder.nativeOrder()).asShortBuffer();
            final int frames = shorts.remaining() / channels;
            final float[] mono = new float[frames];

            for (int frame = 0; frame < frames; frame++)
            {
                float sum = 0f;

                for (int channel = 0; channel < channels; channel++)
                {
                    sum += shorts.get(frame * channels + channel);
                }

                mono[frame] = sum / (channels * FULL_SCALE);
            }

            return mono;
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
