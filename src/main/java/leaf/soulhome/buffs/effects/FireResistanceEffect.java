/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Cold storage: fire and lava hurt less.
 *
 * <p>Deliberately not the vanilla Fire Resistance status effect - that would zero the damage
 * outright, show up on the effects HUD, and be cured by milk. Scaling the damage event instead
 * keeps this the same shape as every other soulhome buff: a fraction that ramps with the room's
 * own tier, invisible until it matters, and immune to anything that clears potion effects.
 *
 * <p>Scoped to {@link DamageTypeTags#IS_FIRE} rather than any specific damage type, so a hit from
 * fire, lava, a blaze fireball or a burning arrow are all reduced alike - the room is cold
 * storage against heat in general, not against one particular way of catching alight.
 *
 * <h2>Past 75% (#303)</h2>
 *
 * At rank IX a top-tier room earned 1.06 of a reduction, which clamped to complete immunity to
 * fire and lava. {@link #softCeiling} stops the reduction at three quarters; what rank adds past
 * that makes a player burn out faster once alight, so the climb still pays and fire still hurts.
 */
public class FireResistanceEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FIRE_RESISTANCE;

    @Override
    public String type()
    {
        return TYPE;
    }

    /** Past this, rank shortens burning rather than deepening the reduction - see #303. */
    private static final double SOFT_CEILING = 0.75d;

    /** Extra ticks of burning shed per tick, per point of overflow (rounded up, so any overflow helps). */
    static final double EXTRA_TICKS_PER_OVERFLOW = 4d;

    @Override
    public double softCeiling()
    {
        return SOFT_CEILING;
    }

    @Override
    public String describeOverflow(double overflow)
    {
        return "burning out faster once alight";
    }

    /** Extra burn ticks shed this tick for an overflow; zero when there is none. */
    static int extraBurnTicksShed(double overflow)
    {
        return overflow <= 0d ? 0 : (int) Math.ceil(overflow * EXTRA_TICKS_PER_OVERFLOW);
    }

    @Override
    public String describeMagnitude()
    {
        return "less damage taken from fire and lava, as a fraction of the hit";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        if (!event.getSource().is(DamageTypeTags.IS_FIRE))
        {
            return;
        }

        final double reduction = Math.min(1d, magnitudeFor(player));
        event.setAmount((float) (event.getAmount() * (1d - reduction)));
    }

    /** The overflow (#303): the fire goes out sooner instead of hurting less per hit. */
    @SubscribeEvent
    public void onOverflowTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;

        if (!appliesTo(player) || player.getRemainingFireTicks() <= 0)
        {
            return;
        }

        final int shed = extraBurnTicksShed(overflowFor(player));

        if (shed > 0)
        {
            player.setRemainingFireTicks(Math.max(0, player.getRemainingFireTicks() - shed));
        }
    }
}
