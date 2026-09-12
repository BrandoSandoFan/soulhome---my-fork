/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #169: a colonnade is not a pile of pillars, and a solid slab is not a perfectly spaced one. */
class SpacingClauseTest
{
    private static final BlockMatcher PILLARS = BlockMatcher.ofBlocks("test:pillar");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("pillars", PILLARS);

    private static final SpacingClauseType TYPE = new SpacingClauseType();

    private static FormClause spacing(int minCount)
    {
        return spacing(minCount, 1, 0.5d);
    }

    private static FormClause spacing(int minCount, int groupRadius, double tolerance)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "pillars").put("min_count", minCount)
                .put("group_radius", groupRadius).put("tolerance", tolerance).build());
    }

    private static BlockSignature pillar()
    {
        return new TestBlocks.TestBlock("test:pillar", Set.of(), Passability.BLOCKING);
    }

    /** {@code count} pillars {@code height} blocks tall, every {@code interval} cells along X. */
    private static RegionGeometry colonnade(int count, int interval, int height)
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        for (int i = 0; i < count; i++)
        {
            for (int y = 0; y < height; y++)
            {
                builder.add(i * interval, y, 0, pillar());
            }
        }

        return builder.build();
    }

    private static double confidenceOf(FormClause clause, RegionGeometry geometry)
    {
        return clause.evaluate(geometry, ELEMENTS).confidence();
    }

    @Test
    @DisplayName("five evenly spaced pillars score 1.0, however tall each one is")
    void evenlySpacedPillarsScoreFull()
    {
        assertEquals(1.0, confidenceOf(spacing(3), colonnade(5, 3, 1)), 1e-9);
        assertEquals(1.0, confidenceOf(spacing(3), colonnade(5, 3, 4)), 1e-9,
                "a pillar is one group whatever its height - grouping is what makes this about intervals");
    }

    @Test
    @DisplayName("a solid clump is not perfectly spaced - it is one mass, and grades 0")
    void solidClumpIsNotPerfectlySpaced()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        for (int x = 0; x < 4; x++)
        {
            for (int z = 0; z < 4; z++)
            {
                builder.add(x, 0, z, pillar());
            }
        }

        // the naive per-cell measure scores this 1.0: every cell's nearest neighbour is exactly one
        // away, and the variance of a constant is zero. See #169's second trap.
        assertEquals(0.0, confidenceOf(spacing(3), builder.build()), 1e-9);
    }

    @Test
    @DisplayName("two groups grade 0 - they are always perfectly spaced, which is no evidence at all")
    void twoGroupsSayNothing()
    {
        assertEquals(0.0, confidenceOf(spacing(3), colonnade(2, 5, 3)), 1e-9);
    }

    @Test
    @DisplayName("one pillar out of line grades just under 1 - near-regular is not irregular")
    void onePillarOutOfLineGradesNearlyFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        // six pillars at intervals of 4, except the fourth, which sits a block off its mark
        int[] positions = { 0, 4, 8, 13, 16, 20 };

        for (int x : positions)
        {
            builder.add(x, 0, 0, pillar());
        }

        final double confidence = confidenceOf(spacing(3), builder.build());

        // 0.74: the six bays measure 4, 4, 4, 3, 3, 4, a coefficient of variation of 0.13, and the
        // default tolerance of 0.5 turns that into a quarter off the clause. Deliberately that
        // sensitive - the tolerance is the cv of a genuinely random scatter (about 0.52 for a Poisson
        // point process), which is what makes a scatter grade 0 rather than merely low.
        assertTrue(confidence > 0.7d, "one pillar off its mark is still plainly a colonnade, got " + confidence);
        assertTrue(confidence < 1d, "but it is not perfectly regular either, got " + confidence);
    }

    @Test
    @DisplayName("a scatter grades 0 - unevenly placed things are not a pattern")
    void scatterGradesZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        builder.add(0, 0, 0, pillar());
        builder.add(2, 0, 1, pillar());
        builder.add(3, 0, 9, pillar());
        builder.add(12, 0, 2, pillar());
        builder.add(13, 0, 3, pillar());
        builder.add(4, 0, 20, pillar());

        assertEquals(0.0, confidenceOf(spacing(3), builder.build()), 1e-9);
    }

    @Test
    @DisplayName("wide shelving is not punished for being solid - a slab is one group, not a clump of singles")
    void wideShelvingIsOneGroup()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        // four bookcases, each three blocks wide and two high, with a two-cell gap between them
        for (int unit = 0; unit < 4; unit++)
        {
            for (int dx = 0; dx < 3; dx++)
            {
                for (int y = 0; y < 2; y++)
                {
                    builder.add(unit * 5 + dx, y, 0, pillar());
                }
            }
        }

        assertEquals(1.0, confidenceOf(spacing(3), builder.build()), 1e-9);
    }

    @Test
    @DisplayName("group_radius decides what one unit is, and so how many units there are to count")
    void groupRadiusDecidesWhatOneUnitIs()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        // three fixtures, each a block with another two cells above it - a lantern hung on a chain
        for (int unit = 0; unit < 3; unit++)
        {
            builder.add(unit * 6, 0, 0, pillar());
            builder.add(unit * 6, 2, 0, pillar());
        }

        assertEquals(0.0, confidenceOf(spacing(4, 2, 0.5d), builder.build()), 1e-9,
                "at a group_radius of 2 the halves join, so this is three fixtures - under a min_count of 4");
        assertEquals(1.0, confidenceOf(spacing(4, 1, 0.5d), builder.build()), 1e-9,
                "at a radius of 1 they are six separate groups, evenly spaced two apart in pairs");
    }

    @Test
    @DisplayName("windows up a shaft count - the measure is not horizontal")
    void spacingIsThreeDimensional()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(500);

        for (int level = 0; level < 4; level++)
        {
            builder.add(0, level * 4, 0, pillar());
        }

        assertEquals(1.0, confidenceOf(spacing(3), builder.build()), 1e-9);
    }

    @Test
    @DisplayName("mirroring a layout does not change what it scores")
    void mirroredLayoutScoresTheSame()
    {
        RegionGeometry.Builder left = RegionGeometry.builder(500);
        RegionGeometry.Builder right = RegionGeometry.builder(500);

        int[] positions = { 0, 4, 8, 13, 16, 20 };

        for (int x : positions)
        {
            left.add(x, 0, 0, pillar());
            right.add(-x, 0, 0, pillar());
        }

        assertEquals(
                confidenceOf(spacing(3), left.build()),
                confidenceOf(spacing(3), right.build()),
                1e-9);
    }

    @Test
    @DisplayName("an element that matches nothing scores 0 and says which one was missing")
    void missingElementIsNamed()
    {
        FormResult result = spacing(3).evaluate(RegionGeometry.EMPTY, ELEMENTS);

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("pillars"));
    }

    @Test
    @DisplayName("a min_count below three is rejected at load, because a pair proves nothing")
    void validationRejectsAMeaninglessMinimum()
    {
        assertFalse(spacing(2).validationErrors(Set.of("pillars")).isEmpty());
        assertTrue(spacing(3).validationErrors(Set.of("pillars")).isEmpty());
        assertFalse(spacing(3, 0, 0.5d).validationErrors(Set.of("pillars")).isEmpty(), "group_radius of 0");
        assertFalse(spacing(3, 1, 0d).validationErrors(Set.of("pillars")).isEmpty(), "tolerance of 0");
    }

    @Test
    @DisplayName("min_count above the floor is honoured - three lanterns are not a lit hall")
    void minCountIsHonoured()
    {
        assertEquals(0.0, confidenceOf(spacing(6), colonnade(4, 3, 1)), 1e-9);
        assertEquals(1.0, confidenceOf(spacing(6), colonnade(6, 3, 1)), 1e-9);
    }
}
