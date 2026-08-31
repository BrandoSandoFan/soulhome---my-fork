/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Trophy room: pikes hold their ground, and so, a little, do you.
 *
 * <p>Vanilla's own knockback resistance attribute is already a 0-1 fraction rather than a
 * percentage of something else, so the magnitude is added to it directly - {@code ADDITION} on
 * {@link Attributes#KNOCKBACK_RESISTANCE}, the same operation a Wolf's or a Piglin Brute's own
 * innate resistance uses.
 */
public class KnockbackResistanceEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.KNOCKBACK_RESISTANCE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra knockback resistance, as a fraction added to the player's own";
    }

    @Override
    public List<Attribute> attributes()
    {
        return List.of(Attributes.KNOCKBACK_RESISTANCE);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.ADDITION;
    }
}
