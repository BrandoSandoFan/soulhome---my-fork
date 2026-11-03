/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Training yard: falls hurt less - the other half of learning to jump higher.
 *
 * <p>Scales the damage multiplier rather than the fall distance itself. Shortening the distance
 * would also quiet whatever else cares how far the player actually fell - particles, sound,
 * advancement criteria - where this only changes what the fall costs.
 *
 * <h2>Past 75% (#303)</h2>
 *
 * At rank IX a top-tier yard earned 1.06 of a reduction, which clamped to a complete immunity to
 * falls, when rank VI already gave 85%. {@link #softCeiling} stops the reduction at three quarters;
 * what rank adds past that is spent on the safe fall distance instead, so the climb still pays
 * without ever reaching "falls cannot hurt you".
 */
public class FallProtectionEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FALL_PROTECTION;

    @Override
    public String type()
    {
        return TYPE;
    }

    /** Past this, rank buys safe fall distance rather than a bigger reduction - see #303. */
    private static final double SOFT_CEILING = 0.75d;

    /** Blocks of extra safe fall per point of overflow: the rank IX overshoot of about 0.3 is some three blocks. */
    static final double BLOCKS_PER_OVERFLOW = 10d;

    @Override
    public double softCeiling()
    {
        return SOFT_CEILING;
    }

    @Override
    public String describeOverflow(double overflow)
    {
        return "a longer safe fall";
    }

    /** The distance the overflow forgives, in blocks. Pure, so the rule is testable without a player. */
    static float forgivenDistance(double overflow)
    {
        return (float) (Math.max(0d, overflow) * BLOCKS_PER_OVERFLOW);
    }

    @Override
    public String describeMagnitude()
    {
        return "less fall damage, as a fraction of the fall";
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        // magnitudeFor is already held at the soft ceiling; the min only guards a datapack that raises it
        final double reduction = Math.min(1d, magnitudeFor(player));

        final float forgiven = forgivenDistance(overflowFor(player));

        if (forgiven > 0f)
        {
            event.setDistance(Math.max(0f, event.getDistance() - forgiven));
        }

        event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1d - reduction)));
    }
}
