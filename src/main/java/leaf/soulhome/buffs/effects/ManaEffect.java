/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.compat.ModAttributes;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * Arcane sanctum: you hold more magic.
 *
 * <p>A flat addition to Iron's Spells' maximum mana, in the same units the mod's own robes and
 * rings are measured in - a player who has read one tooltip already knows what "+40" means here,
 * and a percentage would make a soulhome worth more the more spell gear you had, which is the
 * opposite of what a room you built yourself should reward.
 *
 * <p>Iron's Spells is not a dependency and is not present in most installs. The attribute is
 * looked up by name (see {@link ModAttributes}), so without the mod this class resolves nothing,
 * writes nothing, and costs a map lookup every half second.
 */
public class ManaEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.MAX_MANA;

    /** Iron's Spells 'n Spellbooks' own maximum mana attribute, base 100. */
    public static final String ATTRIBUTE = "irons_spellbooks:max_mana";

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra maximum mana, as a flat amount";
    }

    @Override
    public List<Attribute> attributes()
    {
        return ModAttributes.find(ATTRIBUTE).map(List::of).orElseGet(List::of);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.ADDITION;
    }
}
