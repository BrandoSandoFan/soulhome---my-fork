/*
 * File created ~ 28 - 9 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.structures.core.AbilityCharges;
import leaf.soulhome.structures.core.SoulBuffSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bank of charges belongs to the player and outlives the room that granted it (#275). Deleting it
 * when the room went dormant was what let a player unbind an ability room at the anchor and rebind it
 * on the next click for a full refill. These pin the replacement: the bank stays, keeps filling at
 * the pace it had, and is dropped only once it is full - at which point a fresh grant is the same.
 */
class PlayerSoulBuffsTest
{
    private static final String BARRAGE = "soulhome:barrage";
    private static final int MAX = 3;
    private static final int COOLDOWN = 100;

    @Test
    @DisplayName("unbinding and rebinding an ability room does not refill its charges")
    void unbindRebindKeepsTheBank()
    {
        PlayerSoulBuffs held = spentBarrage();

        held.set(SoulBuffSet.empty(), 0);
        assertTrue(held.hasChargesFor(BARRAGE), "a dormant bank must be kept, not deleted");

        held.set(granted(), 0);
        assertEquals(0, held.chargesOf(BARRAGE).charges());
    }

    @Test
    @DisplayName("a dormant bank keeps recharging at its last pace, and is dropped once full")
    void dormantBankRefillsThenGoes()
    {
        PlayerSoulBuffs held = spentBarrage();
        held.set(SoulBuffSet.empty(), 0);

        tick(held, COOLDOWN);
        assertEquals(1, held.chargesOf(BARRAGE).charges());

        tick(held, COOLDOWN);
        assertEquals(2, held.chargesOf(BARRAGE).charges());

        tick(held, COOLDOWN);
        assertFalse(held.hasChargesFor(BARRAGE), "a full dormant bank is the same as a fresh grant");
    }

    @Test
    @DisplayName("an owned bank is left to SoulAbilities, not ticked a second time")
    void ownedBankIsNotTickedHere()
    {
        PlayerSoulBuffs held = spentBarrage();

        for (int i = 0; i < COOLDOWN * MAX; i++)
        {
            held.tickDormant(List.of(BARRAGE));
        }

        assertEquals(new AbilityCharges(0, COOLDOWN), held.chargesOf(BARRAGE));
    }

    @Test
    @DisplayName("a relog keeps a dormant bank's pace, so it neither freezes nor resets to full")
    void paceSurvivesSave()
    {
        PlayerSoulBuffs held = spentBarrage();
        held.set(SoulBuffSet.empty(), 0);

        PlayerSoulBuffs reloaded = new PlayerSoulBuffs();
        reloaded.deserializeNBT(held.serializeNBT());

        tick(reloaded, COOLDOWN);
        assertEquals(1, reloaded.chargesOf(BARRAGE).charges());
    }

    @Test
    @DisplayName("a dormant bank with no known pace is dropped rather than frozen empty forever")
    void bankWithoutPaceIsDropped()
    {
        PlayerSoulBuffs held = new PlayerSoulBuffs();
        held.setCharges(BARRAGE, new AbilityCharges(0, COOLDOWN));

        held.tickDormant(List.of());
        assertFalse(held.hasChargesFor(BARRAGE));
    }

    /** Barrage granted, its pace noted as SoulAbilities#tick would, and every charge spent. */
    private static PlayerSoulBuffs spentBarrage()
    {
        PlayerSoulBuffs held = new PlayerSoulBuffs();
        held.set(granted(), 0);
        held.noteRate(BARRAGE, MAX, COOLDOWN);
        held.setCharges(BARRAGE, new AbilityCharges(0, COOLDOWN));
        return held;
    }

    private static SoulBuffSet granted()
    {
        return SoulBuffSet.of(Map.of(BARRAGE, 1d));
    }

    private static void tick(PlayerSoulBuffs held, int ticks)
    {
        for (int i = 0; i < ticks; i++)
        {
            held.tickDormant(List.of());
        }
    }
}
