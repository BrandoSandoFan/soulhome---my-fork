/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Apiary: a well-kept hive draws a little more luck out of the world, the way a hoard drew
 * {@code soulhome:fortune} out of the ground for a treasury.
 *
 * <p>Vanilla's own luck attribute, not a bespoke roll - {@code Attributes.LUCK} already feeds
 * every loot table's {@code luck} parameter, which is what a fisherman's or a looter's own actual
 * fortune runs on. {@code ADDITION}, the same as {@link KnockbackResistanceEffect}, because a
 * potion of luck is itself a flat point on this same attribute rather than a percentage of
 * anything - stacking with one is exactly what a player already expects.
 */
public class ForagersLuckEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FORAGERS_LUCK;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra luck, as flat points on the player's own luck attribute";
    }

    @Override
    public List<Attribute> attributes()
    {
        return List.of(Attributes.LUCK);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.ADDITION;
    }
}
