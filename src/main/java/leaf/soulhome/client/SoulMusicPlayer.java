/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.registry.SoundsRegistry;
import leaf.soulhome.structures.core.AmbienceSettings;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.music.SoulComposer;
import leaf.soulhome.structures.core.music.SoulMusicBrief;
import leaf.soulhome.structures.core.music.SoulSynth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The soul's own music, in place of Minecraft's (#163).
 *
 * <p>The first real playtest of the Ambience epic found vanilla's background music louder than the
 * whole of the soul's ambience, and said what should happen instead: inside a soul, the soul plays
 * its own. So while a player stands in one with {@code soul_music} on, {@link #onSelectMusic} tells
 * vanilla's music manager there is nothing to play, and this plays a stream {@link SoulSynth}
 * composes as it goes, from the same blend and rank the sky and the bed read.
 *
 * <p>NeoForge's {@code SelectMusicEvent} is the whole of the vanilla half on this line. The
 * {@code 1.20.1} line needs a mixin on {@code MusicManager#tick} for it, because Forge 47.3.0 has no
 * such event; here the manager itself stops whatever it had playing when the event hands it nothing,
 * and starts again as soon as it is handed something - which is what leaving the soul does.
 *
 * <h2>How the music follows the soul</h2>
 *
 * <p>The synth asks for its next piece only when the last one has finished and its rest has run
 * out, and it asks {@link #brief()} - so a hearth finished mid-piece is heard from the next piece
 * on, and a piece never changes key or instrument under a player's feet. That is the music's half of
 * #167's no-sudden-changes rule; its volume eases like everything else in {@code ClientAmbience}.
 *
 * <h2>Under the Music slider</h2>
 *
 * <p>It is {@link SoundSource#MUSIC}, so the slider a player already set for Minecraft's music sets
 * this too, and turning music off in the game's options turns this off with it. The ambience's own
 * {@code sound_volume} knob does not touch it: that knob is for the bed and the one-shots, and two
 * sliders for one piece of music would be one too many.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SoulMusicPlayer
{
    /** The silent file behind the event - see {@code SoundsRegistry.SOUL_MUSIC}. */
    private static final ResourceLocation PLACEHOLDER = ResourceLocation.fromNamespaceAndPath(SoulHome.MODID, "music/soul");

    /** About three seconds to fade in on arrival, and the same to fade out on leaving. */
    private static final float FADE = 0.016f;

    /** Ticks to trust a freshly started instance before checking the engine still has it. */
    private static final int STARTUP_GRACE_TICKS = 40;

    private static volatile SoulMusicBrief brief = SoulMusicBrief.of(SoulCharacter.EMPTY, 0, 1, "");

    private static Instance playing;
    private static float level;
    private static boolean replacing;

    private SoulMusicPlayer()
    {
    }

    /**
     * Minecraft's own music holds off for the whole of a visit, rests between pieces included. The
     * quiet between two pieces is part of the soul's music, and vanilla's own track starting up in
     * it would be exactly the thing a player asked to be rid of.
     */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event)
    {
        if (replacing)
        {
            event.overrideMusic(null);
        }
    }

    /** What the next piece should be about. Read on the sound thread, written on the client thread. */
    static SoulMusicBrief brief()
    {
        return brief;
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            stop(minecraft);
            replacing = false;
            return;
        }

        final String dimension = minecraft.level.dimension().location().toString();
        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience.forDimension(dimension);
        final AmbienceSettings settings = SoulHomeClientConfig.ambience();
        final boolean inSoul = SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul) && settings.musicActive();

        replacing = inSoul;

        // switched off in the game's own options: the engine drops a silent sound instead of
        // registering it, so the liveness check below would restart it every tick forever
        final boolean audible = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0f
                && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0f;

        if (inSoul)
        {
            brief = SoulMusicBrief.of(ClientAmbience.character(), soul.getRank(), soul.getMaxRank(), dimension);
        }

        // a switch turned off is a config change rather than something happening in the world, so
        // it cuts rather than fades - the same rule ClientAmbience keeps for the bed
        if (!settings.musicActive() || !audible)
        {
            stop(minecraft);
            return;
        }

        level += ((inSoul ? 1f : 0f) - level) * FADE;

        if (!inSoul && level < 0.002f)
        {
            stop(minecraft);
            return;
        }

        final SoundManager manager = minecraft.getSoundManager();

        if (playing == null || playing.lost(manager))
        {
            if (!inSoul)
            {
                // on the way out and already gone - nothing to fade, and nothing to start
                stop(minecraft);
                return;
            }

            if (playing != null)
            {
                manager.stop(playing);
            }

            playing = new Instance();
            manager.play(playing);
        }

        playing.setLevel(level * ClientAmbience.duckLevel());
    }

    private static void stop(Minecraft minecraft)
    {
        if (playing != null)
        {
            minecraft.getSoundManager().stop(playing);
            playing = null;
        }

        level = 0f;
    }

    /**
     * The sound instance the music plays through. Its stream is {@link SoulMusicStream} rather than
     * the event's file - unless a resource pack has pointed the event at a file of its own, in which
     * case that file plays, looped, and the pack's author gets exactly what they asked for.
     */
    private static final class Instance extends AbstractTickableSoundInstance
    {
        private int age;

        private Instance()
        {
            super(SoundsRegistry.SOUL_MUSIC.get(), SoundSource.MUSIC, RandomSource.create());

            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0f;
            this.x = 0d;
            this.y = 0d;
            this.z = 0d;
        }

        void setLevel(float level)
        {
            this.volume = level;
        }

        boolean lost(SoundManager manager)
        {
            return this.age > STARTUP_GRACE_TICKS && !manager.isActive(this);
        }

        @Override
        public void tick()
        {
            this.age++;
        }

        @Override
        public boolean canStartSilent()
        {
            // starts at zero and fades up, like the bed
            return true;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping)
        {
            if (!PLACEHOLDER.equals(sound.getLocation()))
            {
                return buffers.getStream(sound.getPath(), true);
            }

            // a fresh starting point in the soul's sequence of pieces each visit, so arriving does
            // not always begin with the same piece - the pieces are still this soul's, in its key
            final long offset = ThreadLocalRandom.current().nextLong(1L << 40);
            final SoulSynth synth = new SoulSynth(index ->
            {
                final SoulMusicBrief now = brief();

                return SoulComposer.compose(now, now.pieceSeed(offset + index));
            }, offset);

            return CompletableFuture.completedFuture(new SoulMusicStream(synth));
        }
    }
}
