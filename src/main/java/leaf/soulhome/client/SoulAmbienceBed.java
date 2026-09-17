/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.registry.SoundsRegistry;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * The bed under the one-shots (#210): what a soulhome sounds like when nothing is happening in it.
 *
 * <p>Two looping layers for rank, mixed against each other by how far the soul has climbed - {@code
 * SoulAmbience.bedMix} decides the levels and this only applies them, which is why the interesting
 * half of this feature is tested without a sound engine anywhere near it. Nine more, quieter still,
 * carry the character half (#214): one per axis pole plus one per contested reading, decided by
 * {@code SoulAmbience.characterBedMix} the same way.
 *
 * <h2>Why a loop after all</h2>
 *
 * <p>The argument against one was that repetition is what makes ambient audio unbearable, so the
 * surest way not to write a loop a player can hum along with is not to have one. The danger is
 * real; the conclusion was not. A bed of five noise bands, each drifting on its own slow LFO at a
 * rate sharing no whole-number ratio with the others, presents no two identical seconds inside the
 * loop, and the loop itself is folded back over its own head with an equal-power crossfade so there
 * is no seam to hear. The thing a player would have to notice in order to notice the loop is the
 * drift, and the drift does not repeat inside it. See {@code tools/ambience}.
 *
 * <h2>Not a thing in the place - the place</h2>
 *
 * <p>Both layers are {@code relative} with attenuation switched off, so they sit at the listener
 * wherever they walk. A positioned bed would mean a soulhome has a corner its ambience comes from,
 * and a player would find that corner.
 *
 * <h2>Surviving the sound engine</h2>
 *
 * <p>F3+T, a resource pack change, or anything else that reloads the sound engine destroys every
 * sound instance in flight without telling the instance about it. So the bed checks each tick that
 * the game still thinks its layers are playing, and starts them again when it does not. Without
 * that, the one thing a player does while tuning ambience - reload a pack - permanently silences the
 * feature until they change dimension.
 */
public final class SoulAmbienceBed
{
    /**
     * Ticks after starting a layer before its liveness is believed.
     *
     * <p>A sound is queued before it is playing, and a check on the very next tick can read as
     * "gone" and start a second copy. One second of grace is far longer than the queue ever takes
     * and far shorter than a player would notice after a pack reload.
     */
    private static final int STARTUP_GRACE_TICKS = 20;

    private static Layer close;
    private static Layer open;

    /** The character half of the bed (#214), one layer per non-{@link SoulVoice#BASE} voice. */
    private static final Map<SoulVoice, Layer> character = new EnumMap<>(SoulVoice.class);

    private SoulAmbienceBed()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            stopAll(minecraft);
            return;
        }

        // muted in the game's own options: SoundEngine drops a silent sound rather than registering
        // it, so every liveness check below would fail and this would queue a fresh instance every
        // tick forever. Nothing to play means nothing to keep alive.
        if (minecraft.options.getSoundSourceVolume(SoundSource.AMBIENT) <= 0f
                || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0f)
        {
            stopAll(minecraft);
            return;
        }

        final SoundManager manager = minecraft.getSoundManager();

        close = tickLayer(manager, close, SoundsRegistry.BED_CLOSE.get(), ClientAmbience.bedClose());
        open = tickLayer(manager, open, SoundsRegistry.BED_OPEN.get(), ClientAmbience.bedOpen());

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            character.put(voice, tickLayer(
                    manager, character.get(voice), SoundsRegistry.characterBed(voice),
                    ClientAmbience.characterBed(voice)));
        }
    }

    /** Drop every layer immediately - a disconnect, or the game's own sound switched off. */
    public static void stopAll(Minecraft minecraft)
    {
        close = stopLayer(minecraft.getSoundManager(), close);
        open = stopLayer(minecraft.getSoundManager(), open);

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice != SoulVoice.BASE)
            {
                character.put(voice, stopLayer(minecraft.getSoundManager(), character.get(voice)));
            }
        }
    }

    private static Layer tickLayer(SoundManager manager, Layer layer, SoundEvent event, float level)
    {
        if (level <= SoulAmbience.BED_SILENCE)
        {
            return stopLayer(manager, layer);
        }

        if (layer == null || layer.lost(manager))
        {
            if (layer != null)
            {
                manager.stop(layer);
            }

            layer = new Layer(event);
            manager.play(layer);
        }

        layer.setLevel(level);

        return layer;
    }

    private static Layer stopLayer(SoundManager manager, Layer layer)
    {
        if (layer != null)
        {
            manager.stop(layer);
        }

        return null;
    }

    /**
     * One layer of the bed.
     *
     * <p>Its volume is written from outside rather than computed in {@link #tick()}, because the
     * easing that produces it belongs to {@code ClientAmbience} along with the colour and the fog -
     * one place that knows how fast this mod's ambience is allowed to move, rather than one per
     * thing that moves.
     */
    private static final class Layer extends AbstractTickableSoundInstance
    {
        private int age;

        private Layer(SoundEvent event)
        {
            super(event, SoundSource.AMBIENT, RandomSource.create());

            this.looping = true;
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

        /** Whether the sound engine has forgotten this instance - see the class javadoc. */
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
            // the bed always starts at zero and is eased up - without this the engine would drop it
            // on the tick it was queued and the fade-in would never begin
            return true;
        }
    }
}
