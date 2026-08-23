/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WithinClauseTest
{
    private static final BlockMatcher A = BlockMatcher.ofBlocks("test:a");
    private static final BlockMatcher B = BlockMatcher.ofBlocks("test:b");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("a", A, "b", B);

    private static final WithinClauseType TYPE = new WithinClauseType();

    private static FormClause within(int maxDistance, double coverage)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "a").put("to", "b")
                .put("max_distance", maxDistance).put("metric", "chebyshev").put("coverage", coverage)
                .build());
    }

    @Test
    @DisplayName("A adjacent to B scores 1.0")
    void adjacentScoresFull()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, testBlock("test:a"))
                .add(1, 0, 0, testBlock("test:b"))
                .build();

        assertEquals(1.0, within(1, 1.0).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("A across the room from B scores 0")
    void farAwayScoresZero()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, testBlock("test:a"))
                .add(20, 0, 0, testBlock("test:b"))
                .build();

        assertEquals(0.0, within(1, 1.0).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a diagonal neighbour counts at max_distance 1 (Chebyshev)")
    void diagonalCountsUnderChebyshev()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, testBlock("test:a"))
                .add(1, 0, 1, testBlock("test:b"))
                .build();

        assertEquals(1.0, within(1, 1.0).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("coverage is over 'of' cells, not over pairs: one A against a wall of twenty B scores the same as against one B")
    void coverageIsPerOfCellNotPerPair()
    {
        RegionGeometry.Builder wallOfTwenty = RegionGeometry.builder(100).add(0, 0, 0, testBlock("test:a"));

        // a whole wall of B, one cell away from A on the X axis - every one of them is within range
        for (int z = 0; z < 20; z++)
        {
            wallOfTwenty.add(1, 0, z, testBlock("test:b"));
        }

        RegionGeometry oneB = RegionGeometry.builder(100)
                .add(0, 0, 0, testBlock("test:a"))
                .add(1, 0, 0, testBlock("test:b"))
                .build();

        double againstTwenty = within(1, 1.0).evaluate(wallOfTwenty.build(), ELEMENTS).confidence();
        double againstOne = within(1, 1.0).evaluate(oneB, ELEMENTS).confidence();

        assertEquals(1.0, againstOne, 1e-9);
        assertEquals(againstOne, againstTwenty, 1e-9);
    }

    @Test
    @DisplayName("coverage: 0.6 reaches confidence 1.0 once 60% of 'of' is satisfied, ramped linearly below that")
    void coverageRampsLinearly()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100).add(10, 0, 0, testBlock("test:b"));

        // 5 A cells: 3 within range of B (satisfied), 2 far away (not satisfied) -> 60% satisfied
        builder.add(9, 0, 0, testBlock("test:a"));
        builder.add(10, 0, 1, testBlock("test:a"));
        builder.add(11, 0, 0, testBlock("test:a"));
        builder.add(50, 0, 0, testBlock("test:a"));
        builder.add(51, 0, 0, testBlock("test:a"));

        RegionGeometry geometry = builder.build();

        assertEquals(1.0, within(1, 0.6).evaluate(geometry, ELEMENTS).confidence(), 1e-9);

        // 40% satisfied against the same 0.6 coverage should land at 0.4 / 0.6 on the ramp
        RegionGeometry.Builder fortyPercent = RegionGeometry.builder(100).add(10, 0, 0, testBlock("test:b"));
        fortyPercent.add(9, 0, 0, testBlock("test:a"));
        fortyPercent.add(10, 0, 1, testBlock("test:a"));
        fortyPercent.add(50, 0, 0, testBlock("test:a"));
        fortyPercent.add(51, 0, 0, testBlock("test:a"));
        fortyPercent.add(52, 0, 0, testBlock("test:a"));

        double expected = (0.4 / 0.6);
        assertEquals(expected, within(1, 0.6).evaluate(fortyPercent.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an absent 'of' scores 0 and names 'of' in the diagnostic")
    void absentOfNamesOf()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, testBlock("test:b")).build();

        FormResult result = within(1, 1.0).evaluate(geometry, Map.of("b", B));

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'a'"), result.diagnostic());
    }

    @Test
    @DisplayName("an absent 'to' scores 0 and names 'to' in the diagnostic")
    void absentToNamesTo()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, testBlock("test:a")).build();

        FormResult result = within(1, 1.0).evaluate(geometry, Map.of("a", A));

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'b'"), result.diagnostic());
    }

    @Test
    @DisplayName("'to' declared but matching nothing in the region also scores 0 naming 'to'")
    void toDeclaredButAbsentFromRegion()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, testBlock("test:a")).build();

        FormResult result = within(1, 1.0).evaluate(geometry, ELEMENTS);

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'b'"), result.diagnostic());
    }

    private static BlockSignature testBlock(String id)
    {
        return new TestBlocks.TestBlock(id, java.util.Set.of(), Passability.PASSABLE);
    }
}
