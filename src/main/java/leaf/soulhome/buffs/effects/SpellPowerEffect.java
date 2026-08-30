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
 * Ritual chamber: your spells hit harder.
 *
 * <p>Iron's Spells' spell power attribute is a multiplier with a base of 1, so a magnitude of
 * 0.15 applied as {@code MULTIPLY_BASE} is the "+15%" the player is shown - the same shape, and
 * the same tooltip wording, as the mod's own gear. Deliberately the general attribute rather than
 * one school's: a room in your soul should not decide that you are a fire mage.
 *
 * <p>Absent Iron's Spells there is no such attribute and nothing happens - see
 * {@link ModAttributes} for why that is a lookup miss rather than a crash.
 */
public class SpellPowerEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SPELL_POWER;

    /** Iron's Spells 'n Spellbooks' general spell power attribute, base 1.0. */
    public static final String ATTRIBUTE = "irons_spellbooks:spell_power";

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra spell power as a fraction of the spell's own";
    }

    @Override
    public List<Attribute> attributes()
    {
        return ModAttributes.find(ATTRIBUTE).map(List::of).orElseGet(List::of);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}
