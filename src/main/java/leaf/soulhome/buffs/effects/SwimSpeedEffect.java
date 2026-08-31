/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.compat.ModAttributes;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Aquarium: you move through water the way you move through air.
 *
 * <p>Forge's own {@code forge:swim_speed} attribute, resolved by name through {@link ModAttributes}
 * the same way {@link ReachEffect} resolves {@code forge:block_reach} - it is a Forge attribute
 * rather than a vanilla one, but every Forge install has it, so this is really no more "inert
 * until a mod is installed" than reach is. {@code MULTIPLY_TOTAL}, the same operation
 * {@link SpeedEffect} uses on land, since the magnitude is a fraction of the player's own speed
 * rather than a flat number.
 */
public class SwimSpeedEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SWIM_SPEED;

    /** Forge's own swimming speed multiplier, base 1.0. */
    public static final String SWIM_SPEED = "forge:swim_speed";

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
}
