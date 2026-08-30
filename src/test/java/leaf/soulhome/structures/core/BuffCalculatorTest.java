/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuffCalculatorTest
{
    private static final String XP = "soulhome:xp_gain";
    private static final String SATURATION = "soulhome:saturation";

    /**
     * A linear ramp (no entry floor, exponent 1) over the {@link #archetype} tier ladder's own
     * 0-to-100 range, so a test can predict a magnitude as {@code max * score / 100} by hand
     * instead of fighting the default curve's {@code entryFraction} and {@code pow}.
     */
    private static final BuffSettings LINEAR = new BuffSettings(
            0.5d, 3, 1.0d, Map.of(), BuffSettings.DEFAULT_TYPE_CAPS, 0d, 1d);

    @Test
    @DisplayName("one classified room grants its archetype's buff proportionally to its score")
    void singleRoomGrantsItsBuff()
    {
        SoulBuffSet buffs = compute(List.of(awarded("soulhome:library", 2, 60d)));

        // max 0.30, 60% of the way from the archetype's entry (0) to its top (100)
        assertEquals(0.18d, buffs.magnitude(XP), 1e-9);
        assertTrue(buffs.has(XP));
    }

    @Test
    @DisplayName("a second room of the same archetype is worth half")
    void repeatedRoomsFallOff()
    {
        // one good library should beat four mediocre ones
        SoulBuffSet buffs = compute(List.of(
                awarded("soulhome:library", 1, 60d),
                awarded("soulhome:library", 1, 40d)));

        // 0.30 * 0.60 + (0.30 * 0.40) * 0.5
        assertEquals(0.24d, buffs.magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("only the best rooms count, and only so many of them")
    void roomCountIsCapped()
    {
        List<ClassificationResult> many = new ArrayList<>();

        for (int room = 0; room < 10; room++)
        {
            many.add(awarded("soulhome:library", 1, 30d - room));
        }

        // a high ceiling so this test is about the count cap and falloff, not the archetype max
        SoulBuffSet buffs = BuffCalculator.compute(
                many, List.of(archetype("soulhome:library", XP, 1.0d)), LINEAR);

        // only the top three rooms count - scores 30, 29, 28 - falling off by half each time:
        // 0.30 + 0.29 * 0.5 + 0.28 * 0.25
        assertEquals(0.515d, buffs.magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("the best-scoring room is the one that counts fully")
    void bestRoomLeadsRegardlessOfOrder()
    {
        SoulBuffSet best = compute(List.of(
                awarded("soulhome:library", 3, 100d),
                awarded("soulhome:library", 1, 50d)));

        SoulBuffSet reversed = compute(List.of(
                awarded("soulhome:library", 1, 50d),
                awarded("soulhome:library", 3, 100d)));

        assertEquals(best.magnitude(XP), reversed.magnitude(XP), 1e-9);

        // full value (score at the top of the ladder) plus a half-score room at half falloff:
        // 0.30 + 0.15 * 0.5 = 0.375, clamped to the archetype's own max of 0.30
        assertEquals(0.30d, best.magnitude(XP), 1e-9, "clamped to the archetype's own max of 0.30");
    }

    @Test
    @DisplayName("an archetype cannot exceed its own declared ceiling")
    void archetypeCeilingApplies()
    {
        List<ClassificationResult> many = new ArrayList<>();

        for (int room = 0; room < 3; room++)
        {
            many.add(awarded("soulhome:library", 3, 200d - room));
        }

        // every room scores at or above the top of the ladder, so each is worth the full 0.30:
        // 0.30 + 0.15 + 0.075 would be 0.525, but the archetype declares max 0.30
        assertEquals(0.30d, compute(many).magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("two archetypes granting the same buff stack, up to the global cap")
    void separateArchetypesStackButAreCapped()
    {
        BuffSettings tightCap = new BuffSettings(0.5d, 3, 0.40d);

        List<ArchetypeDefinition> archetypes = List.of(
                archetype("soulhome:library", XP, 0.30d),
                archetype("soulhome:scriptorium", XP, 0.30d));

        // both rooms score at the top of their ladder, so each is worth its full 0.30 regardless
        // of tightCap's default ramp shape
        SoulBuffSet buffs = BuffCalculator.compute(
                List.of(awarded("soulhome:library", 3, 100d), awarded("soulhome:scriptorium", 3, 100d)),
                archetypes,
                tightCap);

        // 0.30 from each, held to the global ceiling
        assertEquals(0.40d, buffs.magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("different buff types are accumulated separately")
    void differentBuffTypesDoNotInterfere()
    {
        SoulBuffSet buffs = compute(List.of(
                awarded("soulhome:library", 1, 60d),
                awarded("soulhome:farm", 2, 60d)));

        // 0.30 * 0.60 and 0.45 * 0.60
        assertEquals(0.18d, buffs.magnitude(XP), 1e-9);
        assertEquals(0.27d, buffs.magnitude(SATURATION), 1e-9);
    }

    @Test
    @DisplayName("ambiguous and unclassified rooms grant nothing")
    void unawardedRoomsGrantNothing()
    {
        SoulBuffSet buffs = compute(List.of(
                unawarded(ClassificationResult.Status.AMBIGUOUS, "soulhome:library", 2, 60d),
                unawarded(ClassificationResult.Status.UNCLASSIFIED, "soulhome:library", 0, 5d)));

        assertTrue(buffs.isEmpty(), "a room the classifier could not decide about is not earned");
        assertEquals(0d, buffs.magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("a room whose archetype has since been unloaded is ignored")
    void unknownArchetypeIsIgnored()
    {
        SoulBuffSet buffs = BuffCalculator.compute(
                List.of(awarded("soulhome:removed_by_a_datapack", 3, 100d)),
                List.of(archetype("soulhome:library", XP, 0.30d)),
                BuffSettings.DEFAULTS);

        assertTrue(buffs.isEmpty());
    }

    @Test
    @DisplayName("no rooms means no buffs")
    void emptySoulhomeGrantsNothing()
    {
        assertTrue(compute(List.of()).isEmpty());
        assertEquals(0d, compute(List.of()).magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("persisted rooms give the same answer as a fresh classification")
    void awardedRoomsRoundTripThroughPersistence()
    {
        // a player logging in should get exactly the buffs their last scan earned, without the
        // server having to sweep their soulhome again
        List<ClassificationResult> results = List.of(
                awarded("soulhome:library", 2, 60d),
                awarded("soulhome:library", 1, 30d),
                awarded("soulhome:farm", 1, 25d),
                unawarded(ClassificationResult.Status.AMBIGUOUS, "soulhome:library", 2, 55d));

        List<AwardedRoom> persisted = AwardedRoom.from(results);

        assertEquals(3, persisted.size(), "the ambiguous room is not persisted as earned");

        List<ArchetypeDefinition> archetypes = List.of(
                archetype("soulhome:library", XP, 0.30d),
                archetype("soulhome:farm", SATURATION, 0.45d));

        assertEquals(
                BuffCalculator.compute(results, archetypes, BuffSettings.DEFAULTS),
                BuffCalculator.computeFromAwarded(persisted, archetypes, BuffSettings.DEFAULTS));
    }

    @Test
    @DisplayName("a per-archetype config multiplier scales everything that archetype grants")
    void archetypeMultiplierApplies()
    {
        // linear, so the multiplier's effect is not tangled up with the default curve's shape
        BuffSettings halved = new BuffSettings(
                0.5d, 3, 1.0d, Map.of("soulhome:library", 0.5d), BuffSettings.DEFAULT_TYPE_CAPS, 0d, 1d);

        SoulBuffSet buffs = BuffCalculator.compute(
                List.of(awarded("soulhome:library", 2, 60d), awarded("soulhome:farm", 2, 60d)),
                List.of(
                        archetype("soulhome:library", XP, 0.30d),
                        archetype("soulhome:farm", SATURATION, 0.45d)),
                halved);

        // 0.30 * 0.60 halved
        assertEquals(0.09d, buffs.magnitude(XP), 1e-9, "the library is turned down");
        // 0.45 * 0.60, untouched
        assertEquals(0.27d, buffs.magnitude(SATURATION), 1e-9, "an archetype not listed is untouched");
    }

    @Test
    @DisplayName("an archetype multiplied to zero grants nothing at all")
    void archetypeMultiplierOfZeroSwitchesItOff()
    {
        BuffSettings off = new BuffSettings(0.5d, 3, 1.0d, Map.of("soulhome:library", 0d));

        BuffBreakdown breakdown = BuffCalculator.explain(
                List.of(new AwardedRoom("soulhome:library", 3, 100d)),
                List.of(archetype("soulhome:library", XP, 0.30d)),
                off);

        assertTrue(breakdown.totals().isEmpty());
        assertTrue(breakdown.sources().isEmpty(), "a switched-off archetype is not a source worth naming");
    }

    @Test
    @DisplayName("a buff type measured in levels is capped in levels, not as a fraction")
    void perTypeCapsOverrideTheGlobalOne()
    {
        // the global default of 1.0 is a doubling for a proportional buff, and would be almost
        // nothing at an enchanting table - so enchanting power carries its own ceiling
        final String enchanting = SoulBuffTypes.ENCHANTMENT_POWER;

        SoulBuffSet buffs = BuffCalculator.compute(
                List.of(awarded("soulhome:enchanting_room", 3, 100d)),
                List.of(archetype("soulhome:enchanting_room", enchanting, 6.0d)),
                BuffSettings.DEFAULTS);

        assertEquals(6.0d, buffs.magnitude(enchanting), 1e-9);
    }

    @Test
    @DisplayName("the breakdown names the rooms behind each buff, and says when the cap is biting")
    void breakdownExplainsWhereBuffsCameFrom()
    {
        BuffSettings tightCap = new BuffSettings(0.5d, 3, 0.40d);

        // both rooms score at the top of their ladder, so each is worth its full 0.30 regardless
        // of tightCap's default ramp shape
        BuffBreakdown breakdown = BuffCalculator.explain(
                List.of(
                        new AwardedRoom("soulhome:library", 3, 100d),
                        new AwardedRoom("soulhome:scriptorium", 3, 100d)),
                List.of(
                        archetype("soulhome:library", XP, 0.30d),
                        archetype("soulhome:scriptorium", XP, 0.30d)),
                tightCap);

        assertEquals(0.40d, breakdown.totals().magnitude(XP), 1e-9);
        assertEquals(2, breakdown.sourcesOf(XP).size(), "both rooms are named");
        assertTrue(breakdown.isCapped(XP), "0.30 + 0.30 was held to 0.40, and a player should be told");
    }

    @Test
    @DisplayName("the breakdown's totals are the buffs actually granted")
    void breakdownAgreesWithTheBuffsGranted()
    {
        // what a player is told and what a player is given come from one call, so that they
        // cannot drift apart as the rules change
        List<AwardedRoom> rooms = List.of(
                new AwardedRoom("soulhome:library", 2, 60d),
                new AwardedRoom("soulhome:library", 1, 30d));

        List<ArchetypeDefinition> archetypes = List.of(archetype("soulhome:library", XP, 0.30d));

        assertEquals(
                BuffCalculator.computeFromAwarded(rooms, archetypes, BuffSettings.DEFAULTS),
                BuffCalculator.explain(rooms, archetypes, BuffSettings.DEFAULTS).totals());
    }

    @Test
    @DisplayName("a zero magnitude is dropped rather than carried around")
    void zeroMagnitudesAreDropped()
    {
        SoulBuffSet buffs = SoulBuffSet.of(Map.of(XP, 0d, SATURATION, 0.2d));

        assertFalse(buffs.has(XP));
        assertEquals(Map.of(SATURATION, 0.2d), buffs.asMap());
    }

    // region helpers

    private static SoulBuffSet compute(List<ClassificationResult> results)
    {
        return BuffCalculator.compute(
                results,
                List.of(
                        archetype("soulhome:library", XP, 0.30d),
                        archetype("soulhome:farm", SATURATION, 0.45d)),
                LINEAR);
    }

    /**
     * A test archetype whose tier ladder runs from a score of 0 (tier 1, the entry) to a score of
     * 100 (tier 3, the top) - the range {@link #LINEAR} ramps magnitude across.
     */
    private static ArchetypeDefinition archetype(String id, String buffType, double max)
    {
        return new ArchetypeDefinition(
                id,
                "archetype.soulhome.test",
                List.of(RegionType.ENCLOSED),
                1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(0d, 1), new ArchetypeDefinition.Tier(100d, 3)),
                // perTier is no longer read by magnitudeAt - see ArchetypeDefinition.BuffSpec
                List.of(new ArchetypeDefinition.BuffSpec(buffType, 0d, max)),
                List.of());
    }

    private static ClassificationResult awarded(String archetypeId, int tier, double score)
    {
        return result(ClassificationResult.Status.CLASSIFIED, archetypeId, tier, score);
    }

    private static ClassificationResult unawarded(ClassificationResult.Status status, String archetypeId, int tier, double score)
    {
        return result(status, archetypeId, tier, score);
    }

    private static ClassificationResult result(ClassificationResult.Status status, String archetypeId, int tier, double score)
    {
        ArchetypeScore best = new ArchetypeScore(
                archetypeId, "archetype.soulhome.test", score, score, 1d, 1d, tier,
                OptionalDouble.empty(), List.of(), List.of(), List.of(), null, List.of(), List.of(), false);

        SoulRegion region = SoulRegion.create(
                RegionType.ENCLOSED,
                new RegionBounds(0, 0, 0, 4, 4, 4),
                BlockCounts.empty(),
                BlockCounts.empty(),
                27);

        return new ClassificationResult(region, status, best, null, List.of(best));
    }

    // endregion
}
