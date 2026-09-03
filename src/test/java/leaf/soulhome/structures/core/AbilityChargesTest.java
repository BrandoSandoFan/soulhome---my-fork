/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityChargesTest
{
    @Test
    @DisplayName("a freshly granted ability is full and has no clock running")
    void fullStartsWithNoClock()
    {
        AbilityCharges charges = AbilityCharges.full(3);

        assertEquals(3, charges.charges());
        assertEquals(0, charges.ticksToNextCharge());
        assertTrue(charges.canSpend());
    }

    @Test
    @DisplayName("ticking at full charges changes nothing - there is nothing to recharge")
    void tickingAtFullDoesNothing()
    {
        AbilityCharges full = AbilityCharges.full(2);

        assertSame(full, full.tick(2, 100));
    }

    @Test
    @DisplayName("spending from full starts the clock; spending again does not restart it")
    void spendingFromFullStartsTheClock()
    {
        AbilityCharges full = AbilityCharges.full(3);

        AbilityCharges once = full.spend(3, 100);
        assertEquals(2, once.charges());
        assertEquals(100, once.ticksToNextCharge());

        AbilityCharges ticked = once.tick(3, 100).tick(3, 100);
        assertEquals(98, ticked.ticksToNextCharge());

        // the second spend must not hand the player back the 2 ticks they had already waited
        AbilityCharges twice = ticked.spend(3, 100);
        assertEquals(1, twice.charges());
        assertEquals(98, twice.ticksToNextCharge());
    }

    @Test
    @DisplayName("a clock running out credits one charge and restarts while there is still room")
    void clockRunningOutCreditsOneCharge()
    {
        AbilityCharges state = new AbilityCharges(0, 1);

        AbilityCharges landed = state.tick(3, 60);
        assertEquals(1, landed.charges());
        assertEquals(60, landed.ticksToNextCharge());
    }

    @Test
    @DisplayName("the last charge to land clears the clock rather than starting another")
    void lastChargeClearsTheClock()
    {
        AbilityCharges state = new AbilityCharges(1, 1);

        AbilityCharges landed = state.tick(2, 60);
        assertEquals(2, landed.charges());
        assertEquals(0, landed.ticksToNextCharge());
    }

    @Test
    @DisplayName("spending with nothing banked is a no-op rather than an error")
    void spendingEmptyIsANoOp()
    {
        AbilityCharges empty = new AbilityCharges(0, 40);

        assertFalse(empty.canSpend());
        assertSame(empty, empty.spend(3, 100));
    }

    @Test
    @DisplayName("a ceiling lowered under a saved state settles instead of ticking against a passed bound")
    void loweredCeilingSettles()
    {
        AbilityCharges over = new AbilityCharges(5, 40);

        AbilityCharges settled = over.tick(2, 100);
        assertEquals(2, settled.charges());
        assertEquals(0, settled.ticksToNextCharge());
    }

    @Test
    @DisplayName("a state with no clock but room to recharge credits a charge rather than stalling forever")
    void stalledStateRecovers()
    {
        // what an older save, or a ceiling that just rose, can leave behind
        AbilityCharges stalled = new AbilityCharges(1, 0);

        assertEquals(2, stalled.tick(3, 60).charges());
    }

    @Test
    @DisplayName("recharge progress reads 1 when nothing is pending and ramps in between")
    void rechargeProgressRamps()
    {
        assertEquals(1d, AbilityCharges.full(2).rechargeProgress(2, 100), 1e-9);
        assertEquals(0d, new AbilityCharges(0, 100).rechargeProgress(2, 100), 1e-9);
        assertEquals(0.75d, new AbilityCharges(0, 25).rechargeProgress(2, 100), 1e-9);
    }

    @Test
    @DisplayName("death empties the bank and restarts the clock")
    void deathEmptiesTheBank()
    {
        AbilityCharges dead = AbilityCharges.afterDeath(80);

        assertEquals(0, dead.charges());
        assertEquals(80, dead.ticksToNextCharge());
    }

    @Test
    @DisplayName("negative charges or a negative clock are rejected rather than stored")
    void negativesAreRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCharges(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityCharges(0, -1));
    }
}
