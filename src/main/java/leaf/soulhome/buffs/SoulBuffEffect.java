/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

/**
 * One buff type, and the hook it acts through.
 *
 * <p>Each buff reaches into a different corner of the game - eating, damage, experience,
 * enchanting - so rather than collecting every hook into one growing class of unrelated event
 * handlers, an effect owns its own. An implementation carries its own {@code @SubscribeEvent}
 * methods and is registered on the Forge bus by {@link #register()}; adding a fifth buff means
 * adding a class, not editing a sixth one.
 *
 * <p>Implementations must not read the capability directly. Go through
 * {@link SoulBuffs#magnitude}, which is where the fake-player and clamping guard rails live.
 */
public interface SoulBuffEffect
{
    /** The buff id archetypes name in their {@code buffs} block, e.g. {@code soulhome:xp_gain}. */
    String type();

    /**
     * A human-readable note on what this buff's magnitude means, for logs and the feedback work.
     * Magnitudes are unitless in the data, so something has to say whether 0.2 is a fifth or a
     * fifth of a level.
     */
    String describeMagnitude();

    /**
     * Whether this effect's magnitude is a fraction of something rather than a flat amount. The
     * feedback UX has to decide between showing "+20%" and "+2", and guessing from the number
     * would eventually show a player the wrong unit for their own buff.
     */
    default boolean isFraction()
    {
        return SoulBuffTypes.isFraction(type());
    }

    /** Subscribe this effect's hooks. Called once, during common setup. */
    default void register()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** No soft ceiling - the default for every buff that only ever gets better as it grows. */
    double NO_SOFT_CEILING = Double.MAX_VALUE;

    /**
     * The magnitude past which more of this buff stops being a reward (#86) - speed, mining speed,
     * reach and swim speed are the four that override this; every other buff keeps {@link
     * #NO_SOFT_CEILING}, growing without limit exactly as it does today. What sits above the
     * ceiling is {@link #overflowFor}, not lost - an effect that has somewhere to put it converts
     * it (see {@link #describeOverflow}); one that does not simply drops it, reported as such
     * rather than silently.
     */
    default double softCeiling()
    {
        return NO_SOFT_CEILING;
    }

    /**
     * This player's magnitude before {@link #softCeiling()} - what the room actually earned, and
     * what {@link #overflowFor} measures against. Effects should act on {@link #magnitudeFor}
     * instead; this exists for the handful that also read their own overflow.
     */
    default double rawMagnitudeFor(Player player)
    {
        return SoulBuffs.magnitude(player, type());
    }

    /** Convenience for implementations: this player's clamped magnitude for this effect. */
    default double magnitudeFor(Player player)
    {
        return Math.min(rawMagnitudeFor(player), softCeiling());
    }

    /** How much of this player's magnitude sits past the soft ceiling (#86) - zero when there is none. */
    default double overflowFor(Player player)
    {
        return Math.max(0d, rawMagnitudeFor(player) - softCeiling());
    }

    /**
     * What an amount past the soft ceiling becomes, in a player's own words - or {@code null} if
     * there is nothing good to convert it into, in which case {@code /soulhome buffs} reports it as
     * dropped rather than converted. Never called with zero or less.
     */
    default String describeOverflow(double overflow)
    {
        return null;
    }

    /**
     * Whether this effect should act at all for this player. Checks the cheap conditions first -
     * most players, most of the time, have no buff and this returns false immediately.
     */
    default boolean appliesTo(Player player)
    {
        return player != null && !player.level().isClientSide && magnitudeFor(player) > 0d;
    }
}
