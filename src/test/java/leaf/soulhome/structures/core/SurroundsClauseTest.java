/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code surrounds} (#30). See {@link InsideClauseTest} for its footprint-containment sibling. */
class SurroundsClauseTest
{
    private static final BlockMatcher CHAIRS = BlockMatcher.ofBlocks("test:chair");
    private static final BlockMatcher FIRE = BlockMatcher.ofBlocks("test:fire");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("chairs", CHAIRS, "fire", FIRE);

    private static final SurroundsClauseType TYPE = new SurroundsClauseType();

    private static FormClause surrounds(int minRadius, int maxRadius, int maxHeightDelta, int sectors, int minSectors)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "chairs").put("to", "fire")
                .put("min_radius", minRadius).put("max_radius", maxRadius).put("max_height_delta", maxHeightDelta)
                .put("sectors", sectors).put("min_sectors", minSectors)
                .build());
    }

    private static FormClause defaultSurrounds()
    {
        return surrounds(1, 5, 2, 8, 3);
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    /** 8 points around the origin, one comfortably inside each of the 8 default 45-degree sectors. */
    private static final int[][] EIGHT_SECTOR_OFFSETS = {
            {2, 1}, {1, 2}, {-1, 2}, {-2, 1}, {-2, -1}, {-1, -2}, {1, -2}, {2, -1}
    };

    @Test
    @DisplayName("8 chairs evenly spaced at radius ~2 scores 1.0")
    void evenlySpacedScoresFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100).add(0, 0, 0, block("test:fire"));

        for (int[] offset : EIGHT_SECTOR_OFFSETS)
        {
            builder.add(offset[0], 0, offset[1], block("test:chair"));
        }

        assertEquals(1.0, defaultSurrounds().evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("3 chairs bunched on one side scores well under 0.5")
    void bunchedChairsScoreWellUnderHalf()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:fire"))
                .add(2, 0, 0, block("test:chair"))
                .add(2, 0, 1, block("test:chair"))
                .add(3, 0, 1, block("test:chair"))
                .build();

        assertTrue(defaultSurrounds().evaluate(geometry, ELEMENTS).confidence() < 0.5);
    }

    @Test
    @DisplayName("the same 3 chairs spread to three sectors score higher than bunched, but still under 1.0")
    void spreadChairsScoreHigherButNotFull()
    {
        RegionGeometry bunched = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:fire"))
                .add(2, 0, 0, block("test:chair"))
                .add(2, 0, 1, block("test:chair"))
                .add(3, 0, 1, block("test:chair"))
                .build();

        RegionGeometry spread = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:fire"))
                .add(EIGHT_SECTOR_OFFSETS[0][0], 0, EIGHT_SECTOR_OFFSETS[0][1], block("test:chair"))
                .add(EIGHT_SECTOR_OFFSETS[1][0], 0, EIGHT_SECTOR_OFFSETS[1][1], block("test:chair"))
                .add(EIGHT_SECTOR_OFFSETS[2][0], 0, EIGHT_SECTOR_OFFSETS[2][1], block("test:chair"))
                .build();

        double bunchedConfidence = defaultSurrounds().evaluate(bunched, ELEMENTS).confidence();
        double spreadConfidence = defaultSurrounds().evaluate(spread, ELEMENTS).confidence();

        assertTrue(spreadConfidence > bunchedConfidence, "spread=" + spreadConfidence + " bunched=" + bunchedConfidence);
        assertTrue(spreadConfidence < 1.0);
    }

    @Test
    @DisplayName("chairs at radius 9 with max_radius 5 scores 0")
    void tooFarScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100).add(0, 0, 0, block("test:fire"));

        for (int[] offset : EIGHT_SECTOR_OFFSETS)
        {
            builder.add(offset[0] * 4, 0, offset[1] * 4, block("test:chair"));
        }

        assertEquals(0.0, defaultSurrounds().evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("chairs on the floor above, past max_height_delta, are not counted")
    void tooHighIsNotCounted()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100).add(0, 0, 0, block("test:fire"));

        for (int[] offset : EIGHT_SECTOR_OFFSETS)
        {
            // 3 cells up, past the default max_height_delta of 2
            builder.add(offset[0], 3, offset[1], block("test:chair"));
        }

        assertEquals(0.0, defaultSurrounds().evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("two campfires, one well-ringed and one bare, scores the good one (best-cluster semantics)")
    void bestClusterWins()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200)
                .add(0, 0, 0, block("test:fire"))
                .add(100, 0, 0, block("test:fire"));

        for (int[] offset : EIGHT_SECTOR_OFFSETS)
        {
            builder.add(offset[0], 0, offset[1], block("test:chair"));
        }

        assertEquals(1.0, defaultSurrounds().evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a square of 8 chairs scores the same as a circle of 8 chairs - shape families, not shapes")
    void squareAndCircleScoreTheSame()
    {
        RegionGeometry.Builder circleBuilder = RegionGeometry.builder(100).add(0, 0, 0, block("test:fire"));

        for (int[] offset : EIGHT_SECTOR_OFFSETS)
        {
            circleBuilder.add(offset[0], 0, offset[1], block("test:chair"));
        }

        // a larger, more rectangular frame than the circle's points - still one chair per 45-degree
        // sector, deliberately away from the exact sector boundaries so the comparison is not at
        // the mercy of floating-point rounding on an atan2 result of precisely 45 degrees
        RegionGeometry square = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:fire"))
                .add(3, 0, 2, block("test:chair"))
                .add(2, 0, 3, block("test:chair"))
                .add(-2, 0, 3, block("test:chair"))
                .add(-3, 0, 2, block("test:chair"))
                .add(-3, 0, -2, block("test:chair"))
                .add(-2, 0, -3, block("test:chair"))
                .add(2, 0, -3, block("test:chair"))
                .add(3, 0, -2, block("test:chair"))
                .build();

        double circleConfidence = defaultSurrounds().evaluate(circleBuilder.build(), ELEMENTS).confidence();
        double squareConfidence = defaultSurrounds().evaluate(square, ELEMENTS).confidence();

        assertEquals(circleConfidence, squareConfidence, 1e-9);
    }

    @Test
    @DisplayName("chairs pressed against the fire score full surrounds and zero at_range from #29, independently")
    void surroundsAndAtRangeAreIndependent()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100).add(0, 0, 0, block("test:fire"));

        // the 8 direct/diagonal neighbours of the fire: pressed right against it
        int[][] neighbours = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

        for (int[] offset : neighbours)
        {
            builder.add(offset[0], 0, offset[1], block("test:chair"));
        }

        RegionGeometry geometry = builder.build();

        double surroundsConfidence = defaultSurrounds().evaluate(geometry, ELEMENTS).confidence();

        AtRangeClauseType atRangeType = new AtRangeClauseType();
        FormClause atRange = atRangeType.create(ClauseParams.builder()
                .put("of", "chairs").put("to", "fire")
                .put("min_distance", 2).put("max_distance", 4).put("metric", "chebyshev").put("coverage", 1.0)
                .build());

        double atRangeConfidence = atRange.evaluate(geometry, ELEMENTS).confidence();

        assertEquals(1.0, surroundsConfidence, 1e-9);
        assertEquals(0.0, atRangeConfidence, 1e-9);
    }
}
