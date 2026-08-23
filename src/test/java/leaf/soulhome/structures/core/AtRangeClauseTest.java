/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtRangeClauseTest
{
    private static final BlockMatcher A = BlockMatcher.ofBlocks("test:a");
    private static final BlockMatcher B = BlockMatcher.ofBlocks("test:b");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("a", A, "b", B);

    private static final AtRangeClauseType TYPE = new AtRangeClauseType();

    private static FormClause atRange(int minDistance, int maxDistance)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "a").put("to", "b")
                .put("min_distance", minDistance).put("max_distance", maxDistance)
                .put("metric", "chebyshev").put("coverage", 1.0)
                .build());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    @Test
    @DisplayName("A touching B scores 0 at min_distance 2")
    void touchingScoresZero()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:a"))
                .add(1, 0, 0, block("test:b"))
                .build();

        assertEquals(0.0, atRange(2, 4).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("A at distance 3 scores 1.0")
    void middleDistanceScoresFull()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:a"))
                .add(3, 0, 0, block("test:b"))
                .build();

        assertEquals(1.0, atRange(2, 4).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("A beyond max_distance also scores 0")
    void tooFarScoresZero()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:a"))
                .add(10, 0, 0, block("test:b"))
                .build();

        assertEquals(0.0, atRange(2, 4).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }
}
