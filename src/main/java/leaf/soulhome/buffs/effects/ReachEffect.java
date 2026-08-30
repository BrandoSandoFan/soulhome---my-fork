/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.compat.ModAttributes;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Workshop: everything is within arm's reach.
 *
 * <p>Blocks and entities both, in blocks of distance. Forge splits reach in two - 4.5 for placing
 * and breaking, 3 for hitting - and a workshop buff that extended only one of them would be a
 * surprise the first time the player swung at something they could clearly reach.
 *
 * <p>These are Forge's own attributes rather than Create's; Create has none to offer, and the
 * point of the room is what a person can do at a workbench, not what a machine can. So this buff
 * works with or without Create installed - it is only the room that grants it that needs Create's
 * blocks to exist. Looked up by name all the same, so there is one way of reaching an attribute
 * in this package rather than two.
 */
public class ReachEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.REACH;

    /** Forge's placing-and-breaking reach, base 4.5. */
    public static final String BLOCK_REACH = "forge:block_reach";

    /** Forge's hitting reach, base 3.0. */
    public static final String ENTITY_REACH = "forge:entity_reach";

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra reach in blocks, for placing and for hitting alike";
    }

    @Override
    public List<Attribute> attributes()
    {
        List<Attribute> attributes = new ArrayList<>(2);

        ModAttributes.find(BLOCK_REACH).ifPresent(attributes::add);
        ModAttributes.find(ENTITY_REACH).ifPresent(attributes::add);

        return attributes;
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.ADDITION;
    }
}
