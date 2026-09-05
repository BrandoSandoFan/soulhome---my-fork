/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.compat.ModAttributes;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aquarium: you move through water the way you move through air - up to a point (#86).
 *
 * <p>Forge's own {@code forge:swim_speed} attribute, resolved by name through {@link ModAttributes}
 * the same way {@link ReachEffect} resolves {@code forge:block_reach} - it is a Forge attribute
 * rather than a vanilla one, but every Forge install has it, so this is really no more "inert
 * until a mod is installed" than reach is. {@code MULTIPLY_TOTAL}, the same operation
 * {@link SpeedEffect} uses on land, since the magnitude is a fraction of the player's own speed
 * rather than a flat number.
 *
 * <h2>Past +40%</h2>
 *
 * The same overshoot problem as {@link SpeedEffect}, in a medium with less control, so
 * {@link #softCeiling} stops the attribute at the same forty percent. {@link #onOverflowTick} spends
 * the rest on breath and on seeing where you are going instead: less air lost while submerged, and
 * enough Night Vision - reapplied every tick rather than granted once, and never shown on the HUD -
 * to make out what is in front of you.
 */
public class SwimSpeedEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SWIM_SPEED;

    /** Forge's own swimming speed multiplier, base 1.0. */
    public static final String SWIM_SPEED = "forge:swim_speed";

    /** Past this, more raw swim speed is the same overshoot problem as land speed - see #86. */
    private static final double SOFT_CEILING = 0.4d;

    /** Extra air points restored per tick, per point of overflow, while submerged. */
    private static final double AIR_TOPUP_PER_OVERFLOW = 4d;

    /** Reapplied every tick rather than granted once, so it never outlives the dive that earned it. */
    private static final int VISIBILITY_DURATION_TICKS = 20;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra swim speed as a fraction of the player's own";
    }

    @Override
    public double softCeiling()
    {
        return SOFT_CEILING;
    }

    @Override
    public String describeOverflow(double overflow)
    {
        return "extra breath and clearer sight underwater";
    }

    @Override
    public List<Attribute> attributes()
    {
        List<Attribute> attributes = new ArrayList<>(1);
        ModAttributes.find(SWIM_SPEED).ifPresent(attributes::add);
        return attributes;
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.MULTIPLY_TOTAL;
    }

    /** The overflow (#86): the water becomes less of a problem instead of moving through it faster. */
    @SubscribeEvent
    public void onOverflowTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;

        if (!appliesTo(player) || !player.isUnderWater())
        {
            return;
        }

        final double overflow = overflowFor(player);

        if (overflow <= 0d)
        {
            return;
        }

        if (player.getAirSupply() < player.getMaxAirSupply())
        {
            final int extra = Math.max(1, (int) Math.round(overflow * AIR_TOPUP_PER_OVERFLOW));
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + extra));
        }

        // ambient, no particles, no icon - the same "off the HUD" promise every soulhome buff makes
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION, VISIBILITY_DURATION_TICKS, 0, true, false, false));
    }
}
