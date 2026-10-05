/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.registry.SoundsRegistry;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.structures.core.AmbienceSettings;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What a soul sounds like (#166), which is: almost nothing, most of the time.
 *
 * <h2>What is here, and what is under it</h2>
 *
 * <p>This class is the one-shots: single sounds a minute or more apart, placed off at a distance
 * and shaped by what the soul is made of. Under them, and separate from them, is the ambient bed -
 * see {@code SoulAmbienceBed}, which is a loop, and {@code SoulAmbience.bedMix}, which decides what
 * it is worth.
 *
 * <p>There was no bed for a while, on the argument that repetition is what makes ambient audio
 * unbearable and the surest way not to write a loop a player can hum along with is not to have one.
 * The danger was real and the conclusion was not: #166 asked for both halves, and a loop nobody can
 * notice is a solved problem rather than an open one (#210). The two halves stay apart because they
 * answer different questions - the bed is the place, and a one-shot is the place saying something.
 *
 * <h2>The palette is this mod's own</h2>
 *
 * <p>It was vanilla's, and that was wrong twice (#209). Half the table was the sound of a block
 * being placed or broken, in a dimension whose entire purpose is placing blocks; the other half
 * were cues with meanings of their own, so the base voice played the ascension ritual's own hum at
 * random. Every voice now has its own event, rendered by the generator in {@code tools/ambience} -
 * which also means a resource pack can replace the soul's warm sound without replacing every
 * campfire in the game. See {@code SoundsRegistry}.
 *
 * <h2>Where a one-shot comes from, and how big the place it comes from is</h2>
 *
 * <p>Three things decide a one-shot, and all three of them are arithmetic in {@code structures.core}
 * rather than anything here:
 *
 * <ul>
 *   <li><b>Which voice speaks</b> - {@code SoulAmbience.voiceFor}, off the soul's own blend.</li>
 *   <li><b>Which direction it speaks from</b> (#215) - {@code SoulAmbience.oneShotOrigin} picks a
 *       room that actually pulls toward that voice, weighted by pull, so a warm crackle comes from
 *       the direction of the hearth rather than from the aquarium. A voice with no room behind it
 *       falls back to a random compass angle: the blend is still right, only the direction is
 *       unknown. The base voice is always random, because it is the place rather than a room.</li>
 *   <li><b>How large the place sounds</b> (#216) - {@code SoulAmbience.oneShotProfile} moves the
 *       distance band outward with rank and gives the sound a built tail, since Minecraft has no
 *       reverb: two or three quieter, lower repeats, each a little further round the compass, so
 *       the tail moves the way a reflection would. The first sound is scaled down to pay for them,
 *       which is the difference between a larger soul and a louder one.</li>
 * </ul>
 *
 * <h2>Getting out of the way</h2>
 *
 * <p>No one-shot starts while something of this mod's own is playing, and a tail already in flight
 * is silenced rather than allowed to talk over it (#212). What counts as "ours" is decided at the
 * call sites that play those sounds - see {@code SoulSounds} - so footsteps and block-placing can
 * never hold anything, which is the one thing the owner of #212 asked for by name.
 *
 * <h2>Category and volume</h2>
 *
 * <p>Everything plays under {@link SoundSource#AMBIENT}, the Ambient/Environment slider, so a
 * player can turn this down without touching blocks, mobs or music - though that slider also
 * carries cave sounds, weather and every other environmental cue, so it is not the same as turning
 * only the soul down (#211).
 *
 * <p><b>Finding, #211:</b> {@code SoundSource} is not one of the enums Forge 47.3.0 patches to
 * {@code IExtensibleEnum} - it is a plain {@code final class extends Enum<SoundSource>} with no
 * {@code create} factory, checked directly against the mapped jar rather than assumed. Giving this
 * mod's ambience its own vanilla sound category is possible only through the same enum-extension
 * mixin a few sound mods carry, and it was judged not worth it: a mixin against an enum's own
 * constant pool is exactly the kind of fragile, easy-to-break-on-an-update code this mod otherwise
 * avoids, for one options-screen slider. {@code AMBIENT} plus this mod's own {@code sound_volume}
 * knob is the real ceiling on 1.20.1, not a placeholder for something better - see the config
 * comment on that knob. The {@code 1.21.1} line is its own, separate check: NeoForge patches a
 * different set of enums than Forge does, so the same question has to be asked again there rather
 * than assumed to have the same answer.
 */
public final class SoulAmbienceSounds
{
    /** Shortest gap between two sounds, in ticks - a minute. */
    private static final int MIN_GAP = 1_200;

    /** Longest, at three and a half minutes. Drawn uniformly, so nothing about it is periodic. */
    private static final int MAX_GAP = 4_200;

    /**
     * Shared ceiling on the whole thing. Full, now that the assets are mastered where they should be
     * (#163): the quiet lives in {@code tools/ambience} and the player's own knobs, and a third
     * attenuation stacked on top of both is how the one-shots came to be inaudible.
     */
    private static final float BASE_VOLUME = 1.0f;

    /** How much further round the compass each repeat of a tail arrives from. */
    private static final double ECHO_SWING = 0.4d;

    /** And how much further out, so a reflection reads as coming off something further away. */
    private static final double ECHO_SPREAD = 1.12d;

    /** Each repeat is a little lower than the one before, as a real tail loses its top end. */
    private static final float ECHO_PITCH_STEP = 0.94f;

    private static final Deque<PendingEcho> ECHOES = new ArrayDeque<>();

    private static int ticksUntilNext = MIN_GAP;

    /** The rank this client last saw, so an ascension can be answered once (#216). */
    private static int lastRank = -1;

    private static int ascensionBeatTicks;

    private SoulAmbienceSounds()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused())
        {
            return;
        }

        final AmbienceSettings settings = SoulHomeClientConfig.ambience();

        if (!settings.soundActive() || !ClientAmbience.active())
        {
            // held at the full gap while off, so switching it back on is not answered instantly -
            // and the rank is forgotten with it, so a rank that changed while this was switched off
            // is not mistaken for an ascension the moment it comes back on
            ticksUntilNext = Math.max(ticksUntilNext, MIN_GAP);
            ECHOES.clear();
            ascensionBeatTicks = 0;
            lastRank = -1;
            return;
        }

        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience
                .forDimension(minecraft.level.dimension().location().toString());

        if (!SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul))
        {
            lastRank = -1;
            ECHOES.clear();
            ascensionBeatTicks = 0;
            return;
        }

        final RandomSource random = minecraft.level.random;

        if (ClientAmbience.held())
        {
            // a tail already in flight is part of the ambience like any other one-shot, and #212 is
            // explicit that a ritual starting mid-tail silences it rather than waiting it out
            ECHOES.clear();
        }
        else
        {
            playDueEchoes(minecraft);
        }

        tickAscensionBeat(minecraft, soul, settings, random);

        if (--ticksUntilNext > 0)
        {
            return;
        }

        ticksUntilNext = MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP);

        // the timer is redrawn either way, so a hold skips one sound rather than banking it and
        // bunching the next few up behind whatever was playing
        if (!ClientAmbience.held())
        {
            play(minecraft, soul, settings, random, SoulAmbience.voiceFor(soul.character(), random.nextDouble()));
        }
    }

    /**
     * The moment of ascending is worth one extra one-shot (#216): the base voice, once, from the new
     * verge distance, a few seconds after the rank arrives. #164's "and then the sky changed" beat,
     * in sound - and deliberately not on the same tick as the sky, nor while the ritual's own audio
     * is still finishing.
     */
    private static void tickAscensionBeat(
            Minecraft minecraft, SyncSoulAmbienceMessage soul, AmbienceSettings settings, RandomSource random)
    {
        final int rank = soul.getRank();

        if (lastRank < 0)
        {
            // first sight of this soul: arriving in a rank V soulhome is not an ascension
            lastRank = rank;
            return;
        }

        if (rank > lastRank)
        {
            ascensionBeatTicks = SoulAmbience.ASCENSION_BEAT_DELAY_TICKS;
        }

        lastRank = rank;

        if (ascensionBeatTicks <= 0)
        {
            return;
        }

        if (ClientAmbience.held())
        {
            // the ritual's own hold is still running, so the beat waits for it rather than being
            // lost - this is the one one-shot that is about something that just happened
            return;
        }

        if (--ascensionBeatTicks <= 0)
        {
            play(minecraft, soul, settings, random, SoulVoice.BASE);
        }
    }

    private static void play(
            Minecraft minecraft, SyncSoulAmbienceMessage soul, AmbienceSettings settings,
            RandomSource random, SoulVoice voice)
    {
        final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                soul.getRank(), soul.getMaxRank(), soul.getVergeHalfExtentOrLegacy(), settings);

        final SoundEvent sound = soundFor(voice);

        final double listenerX = minecraft.player.getX();
        final double listenerY = minecraft.player.getEyeY();
        final double listenerZ = minecraft.player.getZ();

        // a room that pulls toward this voice, if the soul has one; otherwise the honest random
        // angle the one-shots have always used - see SoulAmbience.oneShotOrigin
        SoulAmbience.OneShotPlacement placement = SoulAmbience.oneShotOrigin(
                voice, soul.voiceRooms(), ArchetypeManager.byId(),
                listenerX, listenerY, listenerZ, random.nextDouble(), random.nextDouble(), profile);

        double directionX;
        double directionZ;

        if (placement != null && placement.hasDirection())
        {
            directionX = placement.directionX();
            directionZ = placement.directionZ();
        }
        else
        {
            placement = SoulAmbience.oneShotPlacement(profile, random.nextDouble(), random.nextDouble());

            final double angle = random.nextDouble() * Math.PI * 2d;

            directionX = Math.cos(angle);
            directionZ = Math.sin(angle);
        }

        final float pitch = profile.pitch() + random.nextFloat() * 0.06f;
        final float volume = (float) settings.soundVolume() * (float) settings.intensity() * BASE_VOLUME;

        emit(minecraft, sound, placement, directionX, directionZ, volume * profile.leadVolume(), pitch);

        queueEchoes(minecraft, sound, placement, directionX, directionZ, volume, pitch, profile, random);
    }

    /**
     * The built tail (#216). Each repeat is quieter, lower, a little further round the compass and a
     * little further out, so what a player hears is a reflection arriving from somewhere else rather
     * than the same sound played twice.
     */
    private static void queueEchoes(
            Minecraft minecraft, SoundEvent sound, SoulAmbience.OneShotPlacement placement,
            double directionX, double directionZ, float volume, float pitch,
            SoulAmbience.OneShotProfile profile, RandomSource random)
    {
        double horizontal = placement.horizontalDistance();
        double angle = Math.atan2(directionZ, directionX);
        float echoPitch = pitch;
        final double swing = random.nextBoolean() ? ECHO_SWING : -ECHO_SWING;

        for (int repeat = 1; repeat <= profile.echoes(); repeat++)
        {
            horizontal *= ECHO_SPREAD;
            angle += swing;
            echoPitch *= ECHO_PITCH_STEP;

            final SoulAmbience.OneShotPlacement echo = SoulAmbience.OneShotPlacement.fitted(
                    horizontal, placement.verticalOffset(), Math.cos(angle), Math.sin(angle));

            ECHOES.add(new PendingEcho(
                    repeat * profile.echoDelayTicks(),
                    sound,
                    minecraft.player.getX() + echo.directionX() * echo.horizontalDistance(),
                    minecraft.player.getEyeY() + echo.verticalOffset(),
                    minecraft.player.getZ() + echo.directionZ() * echo.horizontalDistance(),
                    volume * profile.volumeOf(repeat),
                    echoPitch));
        }
    }

    private static void playDueEchoes(Minecraft minecraft)
    {
        if (ECHOES.isEmpty())
        {
            return;
        }

        final List<PendingEcho> remaining = new ArrayList<>(ECHOES.size());

        for (PendingEcho echo : ECHOES)
        {
            final PendingEcho advanced = echo.tick();

            if (advanced.ticksUntil() > 0)
            {
                remaining.add(advanced);
                continue;
            }

            minecraft.level.playLocalSound(
                    advanced.x(), advanced.y(), advanced.z(), advanced.sound(),
                    SoundSource.AMBIENT, advanced.volume(), advanced.pitch(), false);
        }

        ECHOES.clear();
        ECHOES.addAll(remaining);
    }

    private static void emit(
            Minecraft minecraft, SoundEvent sound, SoulAmbience.OneShotPlacement placement,
            double directionX, double directionZ, float volume, float pitch)
    {
        // placed relative to the listener - the player's ear, not their feet (#208) - and kept
        // well inside vanilla's audible radius; see SoulAmbience.oneShotPlacement's javadoc
        final double x = minecraft.player.getX() + directionX * placement.horizontalDistance();
        final double z = minecraft.player.getZ() + directionZ * placement.horizontalDistance();
        final double y = minecraft.player.getEyeY() + placement.verticalOffset();

        minecraft.level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, volume, pitch, false);
    }

    /**
     * Which sound event a voice speaks with (#209).
     *
     * <p>A lookup now, where it used to be a coin flip between two vanilla events. The variety it
     * used to get from that flip comes from {@code sounds.json}, which names three files per voice
     * and lets the game pick between them - which is both less code here and more variety there.
     */
    private static SoundEvent soundFor(SoulVoice voice)
    {
        return SoundsRegistry.voice(voice);
    }

    /**
     * One repeat of a tail, waiting its turn.
     *
     * <p>Fixed in the world at the moment the one-shot was thrown rather than followed round the
     * player, because a reflection comes from where the wall was, not from where the listener has
     * since walked to.
     */
    private record PendingEcho(
            int ticksUntil, SoundEvent sound, double x, double y, double z, float volume, float pitch)
    {
        PendingEcho tick()
        {
            return new PendingEcho(
                    this.ticksUntil - 1, this.sound, this.x, this.y, this.z, this.volume, this.pitch);
        }
    }
}
