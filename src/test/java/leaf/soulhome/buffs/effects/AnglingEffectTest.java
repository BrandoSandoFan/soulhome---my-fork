/*
 * File created ~ 19 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnglingEffectTest
{
    @Test
    @DisplayName("no magnitude, no levels")
    void nothingForNothing()
    {
        assertEquals(0, AnglingEffect.luckLevels(0d));
        assertEquals(0, AnglingEffect.lureLevels(0d));
        assertEquals(0, AnglingEffect.luckLevels(-1d));
    }

    @Test
    @DisplayName("tier 1 is Luck I and Lure I, tier 3 is Luck III and Lure II")
    void tiersMapToLevels()
    {
        assertEquals(1, AnglingEffect.luckLevels(1d));
        assertEquals(1, AnglingEffect.lureLevels(1d));
        assertEquals(3, AnglingEffect.luckLevels(3d));
        assertEquals(2, AnglingEffect.lureLevels(3d));
    }

    @Test
    @DisplayName("Lure never outruns Luck, because it is worth more per level")
    void lureStaysBehindLuck()
    {
        for (double m = 0.5d; m <= 6d; m += 0.25d)
        {
            assertTrue(AnglingEffect.lureLevels(m) <= AnglingEffect.luckLevels(m), "at " + m);
        }
    }
}
