/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.structures.core.SoulBuffTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one place a buff is turned into words, so that {@code /soulhome buffs} and the two Soul Lens
 * screens cannot answer "what am I carrying" with different names or different units - which is
 * exactly what they used to do, the lens printing "sword_damage +0.2" against the command's
 * "Sword damage +20%".
 */
class BuffNamesTest
{
    @Test
    @DisplayName("a namespaced buff id becomes the lang key the file actually holds")
    void namespacedIdBecomesALangKey()
    {
        assertEquals("buff.soulhome.xp_gain", BuffNames.key(SoulBuffTypes.XP_GAIN));
    }

    @Test
    @DisplayName("an id with no namespace keys off itself rather than losing a segment")
    void bareIdKeysOffItself()
    {
        assertEquals("buff.xp_gain", BuffNames.key("xp_gain"));
    }

    @Test
    @DisplayName("a fractional buff reads as a percentage")
    void fractionReadsAsAPercentage()
    {
        assertEquals("+20%", BuffNames.magnitude(SoulBuffTypes.XP_GAIN, 0.2d));
    }

    @Test
    @DisplayName("a counted buff reads as a flat amount, since '+20%' of a level is a lie")
    void countReadsAsAFlatAmount()
    {
        assertEquals("+0.2", BuffNames.magnitude(SoulBuffTypes.ENCHANTMENT_POWER, 0.2d));
    }

    @Test
    @DisplayName("a buff type this mod does not ship falls back to a fraction, not to a crash")
    void unknownTypeFallsBackToAFraction()
    {
        assertEquals("+50%", BuffNames.magnitude("someothermod:whatever", 0.5d));
    }
}
