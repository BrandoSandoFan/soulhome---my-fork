/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The buff-magnitude ramp: continuous in the room's score rather than stepped by tier, so
 * arrangement is felt as soon as it improves the score rather than only at a tier boundary.
 */
class BuffSpecTest
{
    private static final double ENTRY = 20d;
    private static final double MID_TIER = 60d;
    private static final double TOP = 100d;
    private static final double MAX = 30d;

    @Test
    @DisplayName("below the entry threshold, magnitude is zero at every exponent")
    void belowEntryYieldsNothing()
    {
        for (double exponent : new double[]{0.5d, 1d, 1.5d, 2d, 3d})
        {
            assertEquals(0d, magnitudeAt(ENTRY - 0.01d, exponent), 1e-9, "exponent " + exponent);
        }
    }

    @Test
    @DisplayName("at the entry threshold, magnitude is exactly entryFraction times max, at every exponent")
    void atEntryYieldsEntryFraction()
    {
        for (double exponent : new double[]{0.5d, 1d, 1.5d, 2d, 3d})
        {
            assertEquals(MAX * 0.10d, magnitudeAt(ENTRY, exponent), 1e-9, "exponent " + exponent);
        }
    }

    @Test
    @DisplayName("at or above the top of the ladder, magnitude is exactly max, at every exponent")
    void atOrAboveTopYieldsMax()
    {
        for (double exponent : new double[]{0.5d, 1d, 1.5d, 2d, 3d})
        {
            assertEquals(MAX, magnitudeAt(TOP, exponent), 1e-9, "exponent " + exponent);
            assertEquals(MAX, magnitudeAt(TOP + 500d, exponent), 1e-9, "should clamp above the top too");
        }
    }

    @Test
    @DisplayName("a score below entry yields no buff at all")
    void belowEntryYieldsNoBreakdownSource()
    {
        ArchetypeDefinition archetype = archetype();
        List<AwardedRoom> rooms = List.of(new AwardedRoom(archetype.id(), 1, ENTRY - 5d));

        BuffBreakdown breakdown = BuffCalculator.explain(rooms, List.of(archetype), settings(0.10d, 1.5d));

        assertTrue(breakdown.totals().isEmpty());
        assertTrue(breakdown.sources().isEmpty(), "a room that has not earned tier 1 is not a source");
    }

    @Test
    @DisplayName("rampExponent of 1.0 reproduces a straight linear interpolation")
    void exponentOneIsLinear()
    {
        // 40% of the way from entry to top
        final double score = ENTRY + (TOP - ENTRY) * 0.4d;
        final double expected = MAX * (0.10d + 0.90d * 0.4d);

        assertEquals(expected, magnitudeAt(score, 1.0d), 1e-9);
    }

    @Test
    @DisplayName("raising the exponent lowers the magnitude strictly between the endpoints, and moves neither endpoint")
    void higherExponentLowersTheMiddleOnly()
    {
        final double atExp1 = magnitudeAt(MID_TIER, 1.0d);
        final double atExp2 = magnitudeAt(MID_TIER, 2.0d);
        final double atExp3 = magnitudeAt(MID_TIER, 3.0d);

        assertTrue(atExp1 > atExp2, "exp1=" + atExp1 + " should exceed exp2=" + atExp2);
        assertTrue(atExp2 > atExp3, "exp2=" + atExp2 + " should exceed exp3=" + atExp3);

        for (double exponent : new double[]{1d, 2d, 3d})
        {
            assertEquals(MAX * 0.10d, magnitudeAt(ENTRY, exponent), 1e-9, "entry endpoint, exponent " + exponent);
            assertEquals(MAX, magnitudeAt(TOP, exponent), 1e-9, "top endpoint, exponent " + exponent);
        }
    }

    @Test
    @DisplayName("an exponent below 1 front-loads rather than throwing")
    void exponentBelowOneFrontLoads()
    {
        final double atExp1 = magnitudeAt(MID_TIER, 1.0d);
        final double atExpHalf = magnitudeAt(MID_TIER, 0.5d);

        assertTrue(atExpHalf > atExp1,
                "a sub-1 exponent should pay out more partway through, not less: " + atExpHalf + " vs " + atExp1);
    }

    @Test
    @DisplayName("magnitude is monotonic in score, and continuous across the tier ladder's own boundaries")
    void monotonicAndContinuousAcrossTierBoundaries()
    {
        double previous = -1d;

        for (double score = ENTRY; score <= TOP; score += 1d)
        {
            final double magnitude = magnitudeAt(score, 1.5d);
            assertTrue(magnitude >= previous - 1e-9,
                    "should never decrease as score rises: " + previous + " -> " + magnitude + " at score " + score);
            previous = magnitude;
        }

        // MID_TIER (60) is a tier boundary in the fixture archetype below - the whole point of the
        // ramp is that it no longer jumps there
        final double justBelow = magnitudeAt(MID_TIER - 0.01d, 1.5d);
        final double justAbove = magnitudeAt(MID_TIER + 0.01d, 1.5d);
        assertEquals(justBelow, justAbove, 0.05d);
    }

    @Test
    @DisplayName("an archetype with no tiers grants nothing, rather than dividing by zero")
    void noTiersYieldsNothing()
    {
        ArchetypeDefinition noTiers = new ArchetypeDefinition(
                "soulhome:no_tiers", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:test_buff", 0d, MAX)),
                List.of());

        assertEquals(0d, noTiers.buffs().get(0).magnitudeAt(500d, noTiers, settings(0.10d, 1.5d)), 1e-9);
    }

    @Test
    @DisplayName("a single-tier archetype (entry equals top) grants max as soon as it qualifies")
    void singleTierArchetypeGrantsMaxAtEntry()
    {
        ArchetypeDefinition oneTier = new ArchetypeDefinition(
                "soulhome:one_tier", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(ENTRY, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:test_buff", 0d, MAX)),
                List.of());

        ArchetypeDefinition.BuffSpec spec = oneTier.buffs().get(0);

        assertEquals(MAX, spec.magnitudeAt(ENTRY, oneTier, settings(0.10d, 1.5d)), 1e-9);
        assertEquals(MAX, spec.magnitudeAt(ENTRY + 1000d, oneTier, settings(0.10d, 1.5d)), 1e-9);
        assertEquals(0d, spec.magnitudeAt(ENTRY - 1d, oneTier, settings(0.10d, 1.5d)), 1e-9);
    }

    // region helpers

    private static double magnitudeAt(double score, double rampExponent)
    {
        ArchetypeDefinition archetype = archetype();
        return archetype.buffs().get(0).magnitudeAt(score, archetype, settings(0.10d, rampExponent));
    }

    private static BuffSettings settings(double entryFraction, double rampExponent)
    {
        return new BuffSettings(0.5d, 3, 1.0d, java.util.Map.of(), BuffSettings.DEFAULT_TYPE_CAPS,
                entryFraction, rampExponent);
    }

    /** Entry at 20, a middle tier at 60, top at 100 - a realistic three-tier ladder. */
    private static ArchetypeDefinition archetype()
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(
                        new ArchetypeDefinition.Tier(ENTRY, 1),
                        new ArchetypeDefinition.Tier(MID_TIER, 2),
                        new ArchetypeDefinition.Tier(TOP, 3)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:test_buff", 0d, MAX)),
                List.of());
    }

    // endregion
}
