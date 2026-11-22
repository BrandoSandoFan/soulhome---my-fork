/*
 * File created ~ 22 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChorusEffectTest
{
    private final ChorusEffect chorus = new ChorusEffect();

    @Test
    @DisplayName("tier 1 carries 7 blocks for 12 seconds; the top tier 11 blocks for 20")
    void reachAndLengthScaleWithTier()
    {
        assertEquals(7d, ChorusEffect.radiusFor(2d), 1e-9);
        assertEquals(240, ChorusEffect.durationFor(2d));
        assertEquals(11d, ChorusEffect.radiusFor(6d), 1e-9);
        assertEquals(400, ChorusEffect.durationFor(6d));
    }

    @Test
    @DisplayName("Speed II only at the top tier, and never Regeneration II")
    void amplifiersStayModest()
    {
        assertEquals(0, ChorusEffect.speedAmplifier(2d));
        assertEquals(0, ChorusEffect.speedAmplifier(4d));
        assertEquals(1, ChorusEffect.speedAmplifier(6d));
        assertEquals(0, ChorusEffect.REGENERATION_AMPLIFIER);
    }

    @Test
    @DisplayName("one charge, and a better room is ready sooner")
    void rechargeShortensWithTier()
    {
        assertEquals(1, this.chorus.chargesFor(6d));
        assertTrue(this.chorus.rechargeTicksFor(6d) < this.chorus.rechargeTicksFor(2d));
        assertTrue(this.chorus.rechargeTicksFor(6d) > 0);
    }
}
