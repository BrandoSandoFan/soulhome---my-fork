/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Track: you move faster, everywhere, all the time.
 *
 * <p>Applied as a transient {@code MULTIPLY_TOTAL} modifier on the movement speed attribute - the
 * same operation vanilla's own Speed effect uses - rather than as a potion effect. That keeps it
 * off the effects HUD, uncurable by milk, and free to stack cleanly with an actual Speed potion
 * rather than fighting it for the same slot. The reconciliation, and the reason it happens on a
 * tick rather than on a buff-change event, live in {@link AttributeBuffEffect}.
 */
public class SpeedEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SPEED;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra movement speed as a fraction of the player's own";
    }

    @Override
    public List<Attribute> attributes()
    {
        return List.of(Attributes.MOVEMENT_SPEED);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.MULTIPLY_TOTAL;
    }
}
