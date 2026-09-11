/*
 * File created ~ 11 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code facing} (#36). */
class FacingClauseTest
{
    private static final BlockMatcher SEATING = BlockMatcher.ofBlocks("test:chair");
    private static final BlockMatcher FIRE = BlockMatcher.ofBlocks("test:fire");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("seating", SEATING, "fire", FIRE);

    private static final FacingClauseType TYPE = new FacingClauseType();

    private static FormClause facing(int maxDistance, String tolerance)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "seating").put("to", "fire")
                .put("max_distance", maxDistance).put("tolerance", tolerance)
                .build());
    }

    private static FormClause defaultFacing()
    {
        return facing(6, "adjacent_sector");
    }

    private static BlockSignature chair()
    {
        return new TestBlocks.TestBlock("test:chair", Set.of(), Passability.PARTIAL);
    }

    @Test
    @DisplayName("a ring of stairs facing inward outscores the same ring facing outward")
    void facingInwardOutscoresFacingOutward()
    {
        RegionGeometry inward = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:fire"))
                .add(-3, 0, 0, chair(), Facing.EAST)
                .add(3, 0, 0, chair(), Facing.WEST)
                .add(0, 0, -3, chair(), Facing.SOUTH)
                .add(0, 0, 3, chair(), Facing.NORTH)
                .build();

        RegionGeometry outward = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:fire"))
                .add(-3, 0, 0, chair(), Facing.WEST)
                .add(3, 0, 0, chair(), Facing.EAST)
                .add(0, 0, -3, chair(), Facing.NORTH)
                .add(0, 0, 3, chair(), Facing.SOUTH)
                .build();

        double inwardScore = defaultFacing().evaluate(inward, ELEMENTS).confidence();
        double outwardScore = defaultFacing().evaluate(outward, ELEMENTS).confidence();

        assertEquals(1.0, inwardScore, 1e-9);
        assertEquals(0.0, outwardScore, 1e-9);
        assertTrue(inwardScore > outwardScore);
    }

    @Test
    @DisplayName("a chair off by one compass sector still counts nearly in full")
    void offByOneSectorCountsNearlyInFull()
    {
        // a chair facing due north; the fire sits dead on axis, one 45-degree sector off, or
        // dead to the side (perpendicular) of that facing
        RegionGeometry onAxis = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(0, 0, -4, block("test:fire"))
                .build();

        RegionGeometry oneSectorOff = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(4, 0, -4, block("test:fire"))
                .build();

        RegionGeometry perpendicular = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(4, 0, 0, block("test:fire"))
                .build();

        double onAxisScore = defaultFacing().evaluate(onAxis, ELEMENTS).confidence();
        double oneSectorOffScore = defaultFacing().evaluate(oneSectorOff, ELEMENTS).confidence();
        double perpendicularScore = defaultFacing().evaluate(perpendicular, ELEMENTS).confidence();

        assertEquals(1.0, onAxisScore, 1e-9);
        assertTrue(oneSectorOffScore > 0.65, "one sector off should still count nearly in full, got " + oneSectorOffScore);
        assertTrue(perpendicularScore < oneSectorOffScore,
                "perpendicular should score well below one sector off");
    }

    @Test
    @DisplayName("a block with no facing property scores neither for nor against")
    void noFacingPropertyIsExcludedFromTheAverage()
    {
        // one chair with a facing pointed dead at the fire, one with no facing at all - the second
        // must be left out of the average entirely rather than pulling a perfect score down
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, -4, block("test:fire"))
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(3, 0, 0, chair(), null)
                .build();

        assertEquals(1.0, defaultFacing().evaluate(geometry, ELEMENTS).confidence(), 1e-9,
                "the faceless chair should not have dragged a perfect score down");
    }

    @Test
    @DisplayName("no matched 'of' cell carries a facing direction: scores 0, not 1")
    void allFacelessScoresZero()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, -4, block("test:fire"))
                .add(0, 0, 0, chair(), null)
                .build();

        assertEquals(0.0, defaultFacing().evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a target beyond max_distance is not considered")
    void beyondMaxDistanceIsIgnored()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(0, 0, -20, block("test:fire"))
                .build();

        FormResult result = facing(4, "adjacent_sector").evaluate(geometry, ELEMENTS);
        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("within 4 blocks"), result.diagnostic());
    }

    @Test
    @DisplayName("exact tolerance only credits a target dead ahead")
    void exactToleranceRequiresDeadAheadAlignment()
    {
        RegionGeometry deadAhead = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(0, 0, -4, block("test:fire"))
                .build();

        RegionGeometry slightlyOffAxis = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(1, 0, -4, block("test:fire"))
                .build();

        assertEquals(1.0, facing(6, "exact").evaluate(deadAhead, ELEMENTS).confidence(), 1e-9);
        assertEquals(0.0, facing(6, "exact").evaluate(slightlyOffAxis, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an unknown tolerance falls back to adjacent_sector")
    void unknownToleranceFallsBackToAdjacentSector()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, chair(), Facing.NORTH)
                .add(4, 0, -4, block("test:fire"))
                .build();

        double defaultScore = facing(6, "adjacent_sector").evaluate(geometry, ELEMENTS).confidence();
        double unknownScore = facing(6, "not_a_real_tolerance").evaluate(geometry, ELEMENTS).confidence();

        assertEquals(defaultScore, unknownScore, 1e-9);
    }

    @Test
    @DisplayName("BlockCounts totals are unaffected by facing - it rides on RegionGeometry only")
    void blockCountsUnaffectedByFacing()
    {
        BlockCounts.Builder counts = BlockCounts.builder();
        counts.add(chair());
        counts.add(chair());

        assertEquals(2, counts.build().total());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }
}
