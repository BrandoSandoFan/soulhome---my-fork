/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.structures.core.AmbienceSettings;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * What a soul sounds like (#166), which is: almost nothing, most of the time.
 *
 * <h2>Why there is no loop</h2>
 *
 * <p>The issue leaves open whether this should ship at all, and the argument against it is good:
 * silence in a space people spend hours building in is defensible, and bad ambient audio is worse
 * than none. The thing that makes ambient audio unbearable is repetition, so the design here
 * removes the possibility rather than managing it - there is no bed and no loop, only single
 * sounds a minute or more apart, placed off at a distance and pitched by what the soul is made of.
 * There is no cycle to hum along with because there is no cycle.
 *
 * <h2>Why these are vanilla sounds</h2>
 *
 * <p>No new audio ships with this. Partly that is honest about what can be judged here - an
 * ambient bed nobody has listened to for an hour is exactly what the issue warns against - but
 * mostly it is that the vanilla palette is already tuned to be heard for hours without grating,
 * and a distant campfire crackle says "somewhere warm" more plainly than anything written for the
 * purpose would. A pack that wants its own is a resource pack away from replacing them.
 *
 * <h2>Category and volume</h2>
 *
 * <p>Everything plays under {@link SoundSource#AMBIENT}, the Ambient/Environment slider, so a
 * player can turn this down without touching blocks, mobs or music. That is the closest thing to
 * "its own category" that exists: {@code SoundSource} is a vanilla enum and a mod cannot add to it
 * without doing something to the enum that nobody should do to an enum. On top of it sits this
 * mod's own volume knob, defaulting well under a block being placed.
 */
public final class SoulAmbienceSounds
{
    /** Shortest gap between two sounds, in ticks - a minute. */
    private static final int MIN_GAP = 1_200;

    /** Longest, at three and a half minutes. Drawn uniformly, so nothing about it is periodic. */
    private static final int MAX_GAP = 4_200;

    /** How far off a sound is placed. Distant, so it reads as the place rather than as an event. */
    private static final double DISTANCE = 16d;

    private static int ticksUntilNext = MIN_GAP;

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
            // held at the full gap while off, so switching it back on is not answered instantly
            ticksUntilNext = Math.max(ticksUntilNext, MIN_GAP);
            return;
        }

        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience
                .forDimension(minecraft.level.dimension().location().toString());

        if (!SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul))
        {
            return;
        }

        if (--ticksUntilNext > 0)
        {
            return;
        }

        final RandomSource random = minecraft.level.random;

        ticksUntilNext = MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP);

        play(minecraft, soul, settings, random);
    }

    private static void play(
            Minecraft minecraft, SyncSoulAmbienceMessage soul, AmbienceSettings settings, RandomSource random)
    {
        final SoulVoice voice = SoulAmbience.voiceFor(soul.character(), random.nextDouble());
        final SoundEvent sound = soundFor(voice, random);

        final double angle = random.nextDouble() * Math.PI * 2d;
        final double x = minecraft.player.getX() + Math.cos(angle) * DISTANCE;
        final double z = minecraft.player.getZ() + Math.sin(angle) * DISTANCE;
        final double y = minecraft.player.getY() + (random.nextDouble() * 2d - 1d) * 4d;

        // pitched down as a soul grows, so a rank V soul sounds like a larger room than a rank 0
        // one - the same cue the fog distance gives, in the one sense the fog cannot reach
        final float rankFraction = soul.getMaxRank() <= 0
                ? 1f
                : Math.min(1f, (float) soul.getRank() / (float) soul.getMaxRank());
        final float pitch = 0.9f - 0.25f * rankFraction + random.nextFloat() * 0.1f;
        final float volume = (float) settings.soundVolume() * (float) settings.intensity() * 0.6f;

        minecraft.level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, volume, pitch, false);
    }

    /**
     * Which vanilla sound a voice speaks with. Two per voice, chosen at random, which is enough
     * variety at this spacing that no two in an evening are alike.
     */
    private static SoundEvent soundFor(SoulVoice voice, RandomSource random)
    {
        final boolean first = random.nextBoolean();

        return switch (voice)
        {
            case WARM -> first ? SoundEvents.CAMPFIRE_CRACKLE : SoundEvents.LAVA_POP;
            case COLD -> first ? SoundEvents.POWDER_SNOW_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME;
            case STEAM -> first ? SoundEvents.LAVA_EXTINGUISH : SoundEvents.FIRE_EXTINGUISH;
            case ARCANE -> first ? SoundEvents.PORTAL_AMBIENT : SoundEvents.SOUL_ESCAPE;
            case WROUGHT -> first ? SoundEvents.CHAIN_PLACE : SoundEvents.ANVIL_LAND;
            case QUICKENED -> first ? SoundEvents.CONDUIT_AMBIENT : SoundEvents.ENCHANTMENT_TABLE_USE;
            case VERDANT -> first ? SoundEvents.CAVE_VINES_PICK_BERRIES : SoundEvents.MOSS_PLACE;
            case HOLLOW -> first ? SoundEvents.BONE_BLOCK_PLACE : SoundEvents.DEEPSLATE_PLACE;
            case OVERGROWN -> first ? SoundEvents.SCULK_CATALYST_BLOOM : SoundEvents.SCULK_BLOCK_SPREAD;
            case BASE -> first ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BEACON_AMBIENT;
        };
    }
}
