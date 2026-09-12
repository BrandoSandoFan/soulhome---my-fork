/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #168. Two halves: the clause's own grading against hand-built geometry, and the end-to-end path
 * through {@link RegionScanner}, which is the half that matters for the fault #168 warns about - the
 * clearance index only covers cells a region took in, so a region that were a ring rather than a
 * solid would read its own infield as open air with no floor under it.
 */
class ClearanceClauseTest
{
    private static final ClearanceClauseType TYPE = new ClearanceClauseType();

    /** Everything the archetypes in here could index - clearance itself names no element. */
    private static final Predicate<BlockSignature> EVERYTHING = signature -> true;

    private static FormClause clearance(int minVolume, int idealVolume)
    {
        return TYPE.create(ClauseParams.builder()
                .put("min_volume", minVolume).put("ideal_volume", idealVolume).put("headroom", 2).build());
    }

    private static FormClause clearance(int minVolume, int idealVolume, int headroom)
    {
        return TYPE.create(ClauseParams.builder()
                .put("min_volume", minVolume).put("ideal_volume", idealVolume).put("headroom", headroom).build());
    }

    private static double confidenceOf(FormClause clause, RegionGeometry geometry)
    {
        return clause.evaluate(geometry, Map.of()).confidence();
    }

    // region the clause's own grading

    /**
     * A hollow box of solid blocks with an interior of {@code sizeX * sizeZ} floor cells and a
     * ceiling {@code height} cells above the floor, built the way the scanner would hand it over:
     * every solid cell blocked, the region's bounds around the whole thing.
     */
    private static RegionGeometry room(int sizeX, int sizeZ, int height)
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(4096);

        for (int x = -1; x <= sizeX; x++)
        {
            for (int z = -1; z <= sizeZ; z++)
            {
                builder.addBlocked(x, 0, z);
                builder.addBlocked(x, height + 1, z);

                for (int y = 1; y <= height; y++)
                {
                    final boolean wall = x < 0 || x >= sizeX || z < 0 || z >= sizeZ;

                    if (wall)
                    {
                        builder.addBlocked(x, y, z);
                    }
                }
            }
        }

        return builder.bounds(new RegionBounds(-1, 0, -1, sizeX, height + 1, sizeZ)).build();
    }

    @Test
    @DisplayName("a room with the wanted floor space scores 1.0")
    void roomWithTheWantedFloorScoresFull()
    {
        // 6x6 interior, 3 cells of air above the floor - 36 standing positions
        assertEquals(1.0, confidenceOf(clearance(9, 36), room(6, 6, 3)), 1e-9);
    }

    @Test
    @DisplayName("half the wanted clearance grades near half - the grading is continuous, not a gate")
    void halfTheWantedClearanceGradesNearHalf()
    {
        // 18 standing positions against an ideal of 36
        assertEquals(0.5, confidenceOf(clearance(9, 36), room(6, 3, 3)), 1e-9);
    }

    @Test
    @DisplayName("below min_volume the clause grades 0 - a cupboard is not somewhere to move")
    void belowMinimumGradesZero()
    {
        // 2x2 interior: 4 standing positions, under a min_volume of 9
        assertEquals(0.0, confidenceOf(clearance(9, 36), room(2, 2, 3)), 1e-9);
    }

    @Test
    @DisplayName("a cavity inside a wall is not somewhere to stand - it has no floor under it")
    void cavityInAWallIsNotClearance()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(4096);

        // a 6x6x6 block of solid stone with a 2x1x2 pocket hollowed out of its middle. The pocket
        // has a solid floor - it is surrounded by stone - so the floor rule alone would pass it; what
        // disqualifies it is that there is stone directly on top of it, which is the headroom half of
        // the rule doing the work.
        for (int x = 0; x < 6; x++)
        {
            for (int y = 0; y < 6; y++)
            {
                for (int z = 0; z < 6; z++)
                {
                    final boolean pocket = x >= 2 && x <= 3 && y == 3 && z >= 2 && z <= 3;

                    if (!pocket)
                    {
                        builder.addBlocked(x, y, z);
                    }
                }
            }
        }

        RegionGeometry geometry = builder.bounds(new RegionBounds(0, 0, 0, 5, 5, 5)).build();

        assertEquals(0.0, confidenceOf(clearance(1, 16, 2), geometry),
                1e-9, "one cell of air with stone right on top of it is a crevice, not headroom");
        assertTrue(confidenceOf(clearance(1, 16, 1), geometry) > 0d,
                "asking for one cell of headroom instead does find the pocket - it is the headroom rule"
                        + " doing the work, not an accident of the layout");
    }

    @Test
    @DisplayName("two rooms either side of a wall are two spaces, and the larger one is what grades")
    void aWallSeparatesTwoSpaces()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(4096);

        // 9x3 footprint split by a wall at x = 6: a 6x3 room and a 2x3 room
        for (int x = -1; x <= 9; x++)
        {
            for (int z = -1; z <= 3; z++)
            {
                builder.addBlocked(x, 0, z);
                builder.addBlocked(x, 4, z);

                for (int y = 1; y <= 3; y++)
                {
                    if (x < 0 || x >= 9 || z < 0 || z >= 3 || x == 6)
                    {
                        builder.addBlocked(x, y, z);
                    }
                }
            }
        }

        RegionGeometry geometry = builder.bounds(new RegionBounds(-1, 0, -1, 9, 4, 3)).build();

        // 18 on one side, 6 on the other; the larger space is the one that counts
        assertEquals(0.5, confidenceOf(clearance(4, 36), geometry), 1e-9);
    }

    @Test
    @DisplayName("a one-cell step does not divide a space - a sunken floor is still one room")
    void aStepDoesNotDivideASpace()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(4096);

        // two 3x3 floors, one a cell lower than the other, meeting at x = 3
        for (int x = -1; x <= 6; x++)
        {
            for (int z = -1; z <= 3; z++)
            {
                final int floor = x < 3 ? 0 : 1;
                builder.addBlocked(x, floor, z);
                builder.addBlocked(x, 6, z);

                for (int y = floor + 1; y <= 5; y++)
                {
                    if (x < 0 || x >= 6 || z < 0 || z >= 3)
                    {
                        builder.addBlocked(x, y, z);
                    }
                }
            }
        }

        RegionGeometry geometry = builder.bounds(new RegionBounds(-1, 0, -1, 6, 6, 3)).build();

        assertEquals(1.0, confidenceOf(clearance(4, 18), geometry), 1e-9,
                "9 standing positions on each side of a single step, read as one 18-cell space");
    }

    @Test
    @DisplayName("an index nobody wrote reads as unknown, not as a perfectly clear floor")
    void untrackedClearanceIsNotFreeCredit()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        builder.add(0, 1, 0, TestBlocks.RAIL);

        RegionGeometry geometry = builder.bounds(new RegionBounds(0, 0, 0, 10, 4, 10)).build();

        assertFalse(geometry.hasClearanceData());
        assertEquals(0.0, confidenceOf(clearance(4, 36), geometry), 1e-9);
    }

    @Test
    @DisplayName("an empty geometry says so rather than dividing by a bounds it does not have")
    void emptyGeometryIsSafe()
    {
        FormResult result = clearance(4, 36).evaluate(RegionGeometry.EMPTY, Map.of());

        assertEquals(0.0, result.confidence(), 1e-9);
        assertFalse(result.diagnostic().isBlank(), "a form's report should say why there is nothing to measure");
    }

    @Test
    @DisplayName("the clause is what turns the scanner's clearance index on")
    void theClauseAsksForTheIndex()
    {
        assertTrue(clearance(4, 36).needsClearance());
    }

    @Test
    @DisplayName("parameters that cannot mean anything are rejected at load, not scored as zero")
    void validationRejectsNonsense()
    {
        assertFalse(clearance(4, 0).validationErrors(Set.of()).isEmpty(), "ideal_volume of 0");
        assertFalse(clearance(4, 36, 0).validationErrors(Set.of()).isEmpty(), "headroom of 0");
        assertTrue(clearance(0, 36, 2).validationErrors(Set.of()).isEmpty(), "a min_volume of 0 is legitimate");
    }

    // endregion

    // region end-to-end through the scanner

    /** A sealed 7x3 room with a 3-high ceiling, scanned the way the game would scan it. */
    @Test
    @DisplayName("end-to-end: a scanned room's clear floor is its own interior, wall to wall")
    void endToEndEnclosedRoom()
    {
        GridVolume volume = GridVolume.of(
                new String[]{
                        "#########",
                        "#########",
                        "#########",
                        "#########",
                        "#########"},
                new String[]{
                        "#########",
                        "#.......#",
                        "#.......#",
                        "#.......#",
                        "#########"},
                new String[]{
                        "#########",
                        "#.......#",
                        "#.......#",
                        "#.......#",
                        "#########"},
                new String[]{
                        "#########",
                        "#########",
                        "#########",
                        "#########",
                        "#########"});

        List<SoulRegion> regions = RegionScanner.scan(volume, null, EVERYTHING, true, ScanSettings.DEFAULTS);

        assertEquals(1, regions.size());

        // 7x3 of floor, and the only standing level is the lower of the two air layers
        assertEquals(1.0, confidenceOf(clearance(9, 21), regions.get(0).geometry()), 1e-9);
        assertEquals(0.5, confidenceOf(clearance(9, 42), regions.get(0).geometry()), 1e-9);
    }

    /**
     * The case #168 asks for by name: a fenced circuit with an infield. The infield's ground is
     * inside the ring rather than under the fence line, so it is only in the clearance index at all
     * because #25 made a region a solid rather than a ring ({@code fillInteriorHoles}). Were it
     * absent, the air over the infield would have no floor under it and the track would grade as
     * having nowhere to run - the exact backwards answer #168 warns this clause is most exposed to.
     */
    @Test
    @DisplayName("end-to-end: a fenced circuit's infield is floor to run on, because the region took it in")
    void endToEndCircuitWithAnInfield()
    {
        GridVolume volume = GridVolume.of(
                new String[]{
                        "hhhhhhhhh",
                        "hhhhhhhhh",
                        "hhhhhhhhh",
                        "hhhhhhhhh",
                        "hhhhhhhhh",
                        "hhhhhhhhh",
                        "hhhhhhhhh"},
                new String[]{
                        "FFFFFFFFF",
                        "F.......F",
                        "F.FFFFF.F",
                        "F.F...F.F",
                        "F.FFFFF.F",
                        "F.......F",
                        "FFFFFFFFF"});

        RegionScanner.ScanResult result = RegionScanner.scanWithAdjacency(
                volume, signature -> signature != null && signature.hasTag("minecraft:fences"),
                EVERYTHING, true, 0, ScanSettings.DEFAULTS);

        assertEquals(1, result.regions().size(), "one fenced circuit, infield and all");
        RegionGeometry geometry = result.regions().get(0).geometry();

        assertTrue(geometry.isBlocked(4, 0, 3),
                "the hay under the middle of the infield is in the index - the region is a solid, not a ring");

        // the lane between the two fence rings is 1 wide and runs the whole way round: 20 standing
        // positions, plus the 3 inside the inner ring, which the inner fence cuts off from the lane
        assertEquals(1.0, confidenceOf(clearance(8, 20), geometry), 1e-9);

        FormResult short_ = clearance(8, 40).evaluate(geometry, Map.of());
        assertEquals(0.5, short_.confidence(), 1e-9);
        assertTrue(short_.diagnostic().contains("20"), "the report should name the floor it actually found");
    }

    @Test
    @DisplayName("end-to-end: equipment stacked to the ceiling costs a yard its floor; the same blocks laid flat do not")
    void endToEndStackedEquipmentCostsTheFloor()
    {
        // eighteen slime blocks, laid flat over the whole floor: a player stands on top of them, so
        // the room keeps every cell of the space it had
        GridVolume laidFlat = GridVolume.of(
                new String[]{"########", "########", "########", "########", "########"},
                new String[]{"########", "#MMMMMM#", "#MMMMMM#", "#MMMMMM#", "########"},
                new String[]{"########", "#......#", "#......#", "#......#", "########"},
                new String[]{"########", "#......#", "#......#", "#......#", "########"},
                new String[]{"########", "########", "########", "########", "########"});

        // the same eighteen, stacked into a block three high against one wall: the floor they stand
        // on is gone, and there is no headroom over the top of the stack either
        GridVolume stacked = GridVolume.of(
                new String[]{"########", "########", "########", "########", "########"},
                new String[]{"########", "#MM....#", "#MM....#", "#MM....#", "########"},
                new String[]{"########", "#MM....#", "#MM....#", "#MM....#", "########"},
                new String[]{"########", "#MM....#", "#MM....#", "#MM....#", "########"},
                new String[]{"########", "########", "########", "########", "########"});

        FormClause clause = clearance(4, 18);

        RegionGeometry flatGeometry = onlyRegion(laidFlat).geometry();
        RegionGeometry stackedGeometry = onlyRegion(stacked).geometry();

        assertEquals(1.0, confidenceOf(clause, flatGeometry), 1e-9,
                "18 cells of floor on top of the slime - the whole room is still there to move in");
        assertEquals(12d / 18d, confidenceOf(clause, stackedGeometry), 1e-9,
                "the stack takes its own six columns out of the room, and offers no standing room on top");
        assertTrue(confidenceOf(clause, stackedGeometry) < confidenceOf(clause, flatGeometry),
                "this is the ordering #168 exists to fix: a yard filled with equipment should not"
                        + " out-read one with room to move");
    }

    private static SoulRegion onlyRegion(GridVolume volume)
    {
        List<SoulRegion> regions = RegionScanner.scan(volume, null, EVERYTHING, true, ScanSettings.DEFAULTS);
        assertEquals(1, regions.size(), "the layout should scan as exactly one room");
        return regions.get(0);
    }

    // endregion
}
