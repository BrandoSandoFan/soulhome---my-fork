/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

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

    /** Subscribe this effect's hooks. Called once, during common setup. */
    default void register()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Convenience for implementations: this player's clamped magnitude for this effect. */
    default double magnitudeFor(Player player)
    {
        return SoulBuffs.magnitude(player, type());
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
