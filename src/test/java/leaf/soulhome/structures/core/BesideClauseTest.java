/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BesideClauseTest
{
    private static final BlockMatcher A = BlockMatcher.ofBlocks("test:a");
    private static final BlockMatcher B = BlockMatcher.ofBlocks("test:b");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("a", A, "b", B);

    private static final BesideClauseType TYPE = new BesideClauseType();

    private static FormClause beside(int maxDrop)
    {
        return TYPE.create(ClauseParams.builder().put("of", "a").put("to", "b").put("max_drop", maxDrop).put("coverage", 1.0).build());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    @Test
    @DisplayName("a diagonal horizontal neighbour at the same height is beside")
    void diagonalNeighbourIsBeside()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:a"))
                .add(1, 0, 1, block("test:b"))
                .build();

        assertEquals(1.0, beside(0).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("stacked directly on top is not beside")
    void stackedIsNotBeside()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:a"))
                .add(0, 1, 0, block("test:b"))
                .build();

        assertEquals(0.0, beside(0).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a small height difference is tolerated up to max_drop")
    void smallDropIsToleratedUnderMaxDrop()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:a"))
                .add(1, 1, 0, block("test:b"))
                .build();

        assertEquals(0.0, beside(0).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
        assertEquals(1.0, beside(1).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }
}
