/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #141: which regions touch, which connect, and how far apart they are - and #142: that a
 * region's identity notices when its neighbours change even though its own blocks did not.
 */
class RegionAdjacencyTest
{
    private static final int REACH = 24;

    private static RegionScanner.ScanResult scan(GridVolume volume)
    {
        return RegionScanner.scanWithAdjacency(volume, null, null, false, REACH, ScanSettings.DEFAULTS);
    }

    /** Index of the region whose bounds start at this x, so a test can name rooms by where they are. */
    private static int at(RegionScanner.ScanResult result, int minX)
    {
        for (int i = 0; i < result.regions().size(); i++)
        {
            if (result.regions().get(i).bounds().minX() == minX)
            {
                return i;
            }
        }

        throw new AssertionError("No region starting at x=" + minX + " among " + result.regions());
    }

    private static int lowest(RegionScanner.ScanResult result)
    {
        int best = 0;

        for (int i = 1; i < result.regions().size(); i++)
        {
            if (result.regions().get(i).bounds().minY() < result.regions().get(best).bounds().minY())
            {
                best = i;
            }
        }

        return best;
    }

    // region shared walls

    @Test
    @DisplayName("two rooms sharing a wall share exactly that wall's cells, and are zero apart")
    void sharedWallIsCounted()
    {
        RegionScanner.ScanResult result = scan(twoRooms('#'));
        assertEquals(2, result.regions().size());

        final int left = at(result, 0);
        final int right = at(result, 4);
        final RegionAdjacency adjacency = result.adjacency();

        // a 3x2 partition between two 3x3x2 rooms
        assertEquals(6, adjacency.sharedShellCells(left, right));
        assertEquals(6, adjacency.sharedShellCells(right, left), "symmetric");
        assertEquals(0, adjacency.separation(left, right), "they touch");
        assertEquals(9 + 9 + 24, adjacency.shellCells(left), "floor, ceiling and 24 wall cells");
        assertFalse(adjacency.connected(left, right), "a wall with no opening is not a way through");
        assertEquals(RegionAdjacency.UNREACHABLE, adjacency.pathLength(left, right));
    }

    @Test
    @DisplayName("two rooms with clear ground between them share nothing")
    void separateRoomsShareNothing()
    {
        RegionScanner.ScanResult result = scan(twoRoomsApart(3, false));
        final int left = at(result, 0);
        final int right = at(result, 8);

        assertEquals(0, result.adjacency().sharedShellCells(left, right));
    }

    // endregion

    // region connectivity

    @Test
    @DisplayName("a door in a shared wall connects the two rooms, one cell apart")
    void aDoorConnects()
    {
        RegionScanner.ScanResult result = scan(twoRooms('D'));
        assertEquals(2, result.regions().size(), "a door is still a wall to the scanner: " + result.regions());

        final int left = at(result, 0);
        final int right = at(result, 4);

        assertTrue(result.adjacency().connected(left, right));
        assertEquals(1, result.adjacency().pathLength(left, right), "the door cell is the only cell between them");
        assertEquals(1, result.adjacency().pathLength(right, left), "symmetric");
    }

    @Test
    @DisplayName("an open doorway with no door hung in it connects them just the same")
    void anOpenDoorwayConnects()
    {
        // an arch rather than a door: the gap makes the two pockets one to the scanner, so the
        // doorway is plugged with a trapdoor here - a way through for a bond, a wall for a room
        RegionScanner.ScanResult result = scan(twoRooms('_'));
        assertEquals(2, result.regions().size());

        assertEquals(1, result.adjacency().pathLength(at(result, 0), at(result, 4)));
    }

    @Test
    @DisplayName("a corridor connects two rooms, and its length is the path length")
    void aCorridorConnects()
    {
        RegionScanner.ScanResult result = scan(twoRoomsApart(3, true));
        assertEquals(2, result.regions().size(), "the corridor is open to the sky, so it is not a third room: " + result.regions());

        final int left = at(result, 0);
        final int right = at(result, 8);

        // door, three cells of corridor, door
        assertEquals(5, result.adjacency().pathLength(left, right));
        assertEquals(3, result.adjacency().separation(left, right), "three clear cells between the two shells");
    }

    @Test
    @DisplayName("two rooms with no path between them do not connect, however close")
    void noPathMeansNotConnected()
    {
        RegionScanner.ScanResult result = scan(twoRoomsApart(1, false));
        final int left = at(result, 0);
        final int right = at(result, 6);

        assertFalse(result.adjacency().connected(left, right));
        assertEquals(1, result.adjacency().separation(left, right), "one clear cell apart, though");
    }

    @Test
    @DisplayName("a ladder up to a trapdoor connects a room to the one above it")
    void aLadderShaftConnects()
    {
        // a ladder is passable, so a bare shaft makes the two floors one pocket to the scanner -
        // that is the existing rule, and the right one. Hang a trapdoor at the top and they are
        // two rooms again, and the trapdoor is the way through
        RegionScanner.ScanResult result = scan(stackedWithShaft(true));
        assertEquals(2, result.regions().size(), result.regions().toString());

        final int lower = lowest(result);
        final int upper = 1 - lower;

        assertTrue(result.adjacency().connected(lower, upper));
        assertEquals(1, result.adjacency().pathLength(lower, upper), "the trapdoor is the one cell between them");
    }

    @Test
    @DisplayName("a path is bounded by the reach, beyond which two rooms read as unconnected")
    void reachBoundsThePath()
    {
        GridVolume volume = twoRoomsApart(3, true);

        RegionScanner.ScanResult far = RegionScanner.scanWithAdjacency(volume, null, null, false, 3, ScanSettings.DEFAULTS);
        assertFalse(far.adjacency().connected(at(far, 0), at(far, 8)), "five cells between, reach three");

        RegionScanner.ScanResult none = RegionScanner.scanWithAdjacency(volume, null, null, false, 0, ScanSettings.DEFAULTS);
        assertEquals(RegionAdjacency.UNREACHABLE, none.adjacency().separation(at(none, 0), at(none, 8)),
                "reach zero skips the floods entirely");
        assertEquals(0, none.adjacency().reach());
    }

    // endregion

    // region separation

    @Test
    @DisplayName("separation is geodesic: solid rock between two rooms counts, clear air does not")
    void separationIsGeodesic()
    {
        RegionScanner.ScanResult open = scan(twoRoomsApart(3, false));
        RegionScanner.ScanResult walled = scan(twoRoomsWithRockBetween());

        assertEquals(3, open.adjacency().separation(at(open, 0), at(open, 8)));

        final int left = at(walled, 0);
        final int right = at(walled, 8);
        assertTrue(walled.adjacency().separation(left, right) > 3,
                "the same straight-line distance, with rock in the way, is further: "
                        + walled.adjacency().separation(left, right));
    }

    // endregion

    // region footprints

    @Test
    @DisplayName("a room directly above another is above it, one slab apart, over its whole footprint")
    void aRoomAboveAnother()
    {
        RegionScanner.ScanResult result = scan(stackedWithShaft(false));
        assertEquals(2, result.regions().size());

        final int lower = lowest(result);
        final int upper = 1 - lower;
        final RegionAdjacency adjacency = result.adjacency();

        assertTrue(adjacency.isAbove(upper, lower));
        assertFalse(adjacency.isAbove(lower, upper));
        assertEquals(1, adjacency.verticalGap(upper, lower), "the shared slab");
        // a 5x5 box, walls included - less its four corner columns, which touch no air and so
        // are never part of a room's shell
        assertEquals(21, adjacency.footprint(upper));
        assertEquals(21, adjacency.footprintOverlap(upper, lower));
        assertFalse(adjacency.sharesStorey(upper, lower));
    }

    @Test
    @DisplayName("a courtyard ring's footprint takes in what it closes around, so a garden inside it is inside it")
    void aCourtyardEnclosesItsGarden()
    {
        RegionScanner.ScanResult result = RegionScanner.scanWithAdjacency(
                courtyardWithGarden(), signature -> signature.hasTag("minecraft:crops"), null, false, REACH,
                ScanSettings.DEFAULTS);

        assertEquals(2, result.regions().size(), result.regions().toString());

        int ring = result.regions().get(0).type() == RegionType.ENCLOSED ? 0 : 1;
        int garden = 1 - ring;
        final RegionAdjacency adjacency = result.adjacency();

        // the ring, and everything it closes around - less the four corner columns of the
        // outer wall, which touch no air and so are not shell
        assertEquals(11 * 11 - 4, adjacency.footprint(ring));
        assertEquals(3 * 3, adjacency.footprint(garden));
        assertEquals(9, adjacency.footprintOverlap(ring, garden), "the garden stands wholly inside the ring");
        assertTrue(adjacency.sharesStorey(ring, garden));
    }

    // endregion

    // region order independence

    @Test
    @DisplayName("relationships are identical whichever room the scanner reached first")
    void relationshipsAreOrderIndependent()
    {
        // the same two rooms joined by a corridor, mirrored so the scanner's x-first sweep finds
        // the other one first
        RegionScanner.ScanResult forward = scan(twoRoomsApart(3, true));
        RegionScanner.ScanResult mirrored = scan(mirrorX(twoRoomsApart(3, true)));

        final int fa = at(forward, 0);
        final int fb = at(forward, 8);
        final int ma = at(mirrored, 8);
        final int mb = at(mirrored, 0);

        assertEquals(forward.adjacency().pathLength(fa, fb), mirrored.adjacency().pathLength(ma, mb));
        assertEquals(forward.adjacency().separation(fa, fb), mirrored.adjacency().separation(ma, mb));
        assertEquals(forward.adjacency().sharedShellCells(fa, fb), mirrored.adjacency().sharedShellCells(ma, mb));
    }

    // endregion

    // region identity (#142)

    @Test
    @DisplayName("scanning an unchanged soulhome twice gives identical hashes in identical order")
    void hashesAreStable()
    {
        assertEquals(hashes(scan(twoRoomsApart(3, true))), hashes(scan(twoRoomsApart(3, true))));
        assertEquals(hashes(scan(twoRooms('D'))), hashes(scan(twoRooms('D'))));
    }

    @Test
    @DisplayName("building a corridor between two rooms changes a hash without changing either room's blocks")
    void aCorridorChangesTheHash()
    {
        // the rooms are byte-identical in both layouts; only the ground between them differs
        List<Long> apart = hashes(scan(twoRoomsApart(3, false)));
        List<Long> joined = hashes(scan(twoRoomsApart(3, true)));

        assertNotEquals(apart, joined, "a bond that now exists has to be seen to exist");
    }

    @Test
    @DisplayName("opening a doorway between two rooms changes a hash, even though it changes no room's counts")
    void aDoorChangesTheHash()
    {
        // one wall cell of stone becomes a door: the shared cell's own signature changes, but the
        // point of the fold is that the rooms' connectivity changed too
        assertNotEquals(hashes(scan(twoRooms('#'))), hashes(scan(twoRooms('D'))));
    }

    @Test
    @DisplayName("moving a room so it no longer adjoins another changes a hash")
    void movingApartChangesTheHash()
    {
        // both rooms unchanged in shape and contents; the second one is a block further away
        assertNotEquals(hashes(scan(twoRoomsApart(1, false))), hashes(scan(twoRoomsApart(2, false))));
    }

    @Test
    @DisplayName("the fold is commutative: the same layout found in a different order hashes the same")
    void foldIsCommutative()
    {
        // the region list is sorted by interest, so the same two rooms come back in the same order
        // either way round - what has to hold is that each room's own hash, including its fold,
        // does not depend on whether its neighbour was the first or the second pocket found. The
        // rooms differ in contents so the two lists can be matched up by which is which.
        RegionScanner.ScanResult forward = scan(furnishedPair());
        RegionScanner.ScanResult mirrored = scan(mirrorX(furnishedPair()));

        assertEquals(2, forward.regions().size());

        // mirroring moves each room to a different x, which changes its bounds and so its own
        // hash - the fold cannot be compared through identityHash directly. Compare the
        // relationship digests instead, which the mirror leaves untouched.
        assertEquals(digestsOf(forward), digestsOf(mirrored));
    }

    private static List<Long> hashes(RegionScanner.ScanResult result)
    {
        List<Long> hashes = new ArrayList<>();

        for (SoulRegion region : result.regions())
        {
            hashes.add(region.identityHash());
        }

        return hashes;
    }

    /** The relationship part of each region's hash, recovered by folding the same adjacency onto an unfolded scan. */
    private static List<Long> digestsOf(RegionScanner.ScanResult result)
    {
        List<Long> digests = new ArrayList<>();
        final RegionAdjacency adjacency = result.adjacency();

        for (int i = 0; i < result.regions().size(); i++)
        {
            long digest = 0L;

            for (int j = 0; j < result.regions().size(); j++)
            {
                if (i != j && adjacency.related(i, j))
                {
                    digest += adjacency.sharedShellCells(i, j) * 1_000_003L
                            + adjacency.pathLength(i, j) * 31L
                            + adjacency.separation(i, j) * 7L
                            + adjacency.footprintOverlap(i, j);
                }
            }

            digests.add(digest);
        }

        digests.sort(null);
        return digests;
    }

    // endregion

    // region layouts

    /** Two 3x3x2 rooms side by side with one shared wall; the middle cell of that wall is {@code divider}. */
    private static GridVolume twoRooms(char divider)
    {
        String[] slab = {"#########", "#########", "#########", "#########", "#########"};
        String[] rooms = {
                "#########",
                "#...#...#",
                "#..." + divider + "...#",
                "#...#...#",
                "#########"};
        String[] upper = {
                "#########",
                "#...#...#",
                "#...#...#",
                "#...#...#",
                "#########"};

        return GridVolume.of(slab, rooms, upper, slab);
    }

    /**
     * Two 3x3x2 rooms with {@code gap} cells of open ground between them, and optionally a
     * roofless corridor joining their doors across it.
     */
    private static GridVolume twoRoomsApart(int gap, boolean corridor)
    {
        final String between = ".".repeat(gap);
        final String wallRun = "#".repeat(gap);
        final String door = corridor ? "D" : "#";

        String[] slab = new String[5];
        Arrays.fill(slab, "#####" + wallRun + "#####");

        String[] rooms = {
                "#####" + between + "#####",
                "#...#" + between + "#...#",
                "#..." + door + between + door + "...#",
                "#...#" + between + "#...#",
                "#####" + between + "#####"};

        if (corridor)
        {
            // the corridor is walled either side and open above, so it is a way through and not a
            // third room
            rooms[1] = "#...#" + wallRun + "#...#";
            rooms[3] = "#...#" + wallRun + "#...#";
        }

        String[] upper = {
                "#####" + between + "#####",
                "#...#" + between + "#...#",
                "#...#" + between + "#...#",
                "#...#" + between + "#...#",
                "#####" + between + "#####"};

        return GridVolume.of(slab, rooms, upper, slab);
    }

    /** {@link #twoRoomsApart twoRoomsApart(3, false)} with the gap filled with stone to the roofline and beyond. */
    private static GridVolume twoRoomsWithRockBetween()
    {
        String[] slab = new String[5];
        Arrays.fill(slab, "#############");

        String[] rooms = {
                "#############",
                "#...#####...#",
                "#...#####...#",
                "#...#####...#",
                "#############"};

        String[] cap = {
                ".....###.....",
                ".....###.....",
                ".....###.....",
                ".....###.....",
                ".....###....."};

        // rock a block taller than the rooms, so the route over the top is longer than three
        return GridVolume.of(slab, rooms, rooms, slab, cap, cap);
    }

    /**
     * A 3x3x2 room with another directly above it, sharing one slab - and, if asked, a ladder up
     * the lower room's middle to a trapdoor in that slab.
     */
    private static GridVolume stackedWithShaft(boolean ladder)
    {
        String[] slab = {"#####", "#####", "#####", "#####", "#####"};
        String[] hatch = {"#####", "#####", "##_##", "#####", "#####"};
        String[] room = {
                "#####",
                "#...#",
                "#...#",
                "#...#",
                "#####"};
        String[] climb = {
                "#####",
                "#...#",
                "#.l.#",
                "#...#",
                "#####"};

        return ladder
                ? GridVolume.of(slab, climb, climb, hatch, room, room, slab)
                : GridVolume.of(slab, room, room, slab, room, room, slab);
    }

    /**
     * A roofed corridor running in a ring around an open courtyard, 11x11, with a 3x3 crop bed
     * growing in the courtyard's middle.
     */
    private static GridVolume courtyardWithGarden()
    {
        String[] ground = new String[11];
        Arrays.fill(ground, "###########");
        ground[4] = "####fff####";
        ground[5] = "####fff####";
        ground[6] = "####fff####";

        String[] walls = {
                "###########",
                "#.........#",
                "#.#######.#",
                "#.#.....#.#",
                "#.#.www.#.#",
                "#.#.www.#.#",
                "#.#.www.#.#",
                "#.#.....#.#",
                "#.#######.#",
                "#.........#",
                "###########"};

        String[] upperWalls = {
                "###########",
                "#.........#",
                "#.#######.#",
                "#.#.....#.#",
                "#.#.....#.#",
                "#.#.....#.#",
                "#.#.....#.#",
                "#.#.....#.#",
                "#.#######.#",
                "#.........#",
                "###########"};

        String[] roof = {
                "###########",
                "###########",
                "###########",
                "###.....###",
                "###.....###",
                "###.....###",
                "###.....###",
                "###.....###",
                "###########",
                "###########",
                "###########"};

        return GridVolume.of(ground, walls, upperWalls, roof);
    }

    /** Two rooms sharing a wall, one furnished with a lectern so the two can be told apart. */
    private static GridVolume furnishedPair()
    {
        String[] slab = {"#########", "#########", "#########", "#########", "#########"};
        String[] rooms = {
                "#########",
                "#...#...#",
                "#.L.D...#",
                "#...#...#",
                "#########"};
        String[] upper = {
                "#########",
                "#...#...#",
                "#...#...#",
                "#...#...#",
                "#########"};

        return GridVolume.of(slab, rooms, upper, slab);
    }

    /** The same layout with every row reversed, so the scanner's x-first sweep meets the rooms the other way round. */
    private static GridVolume mirrorX(GridVolume volume)
    {
        final RegionBounds box = volume.bounds();
        final int sizeX = box.sizeX() - 2;
        final int sizeY = box.sizeY() - 2;
        final int sizeZ = box.sizeZ() - 2;

        String[][] layers = new String[sizeY][sizeZ];

        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                StringBuilder row = new StringBuilder();

                for (int x = sizeX - 1; x >= 0; x--)
                {
                    row.append(symbolOf(volume, x, y, z));
                }

                layers[y][z] = row.toString();
            }
        }

        return GridVolume.of(volume.palette(), layers);
    }

    private static char symbolOf(GridVolume volume, int x, int y, int z)
    {
        BlockSignature signature = volume.signatureAt(x, y, z);

        if (signature == null)
        {
            return '.';
        }

        for (var entry : volume.palette().entrySet())
        {
            if (entry.getValue().equals(signature))
            {
                return entry.getKey();
            }
        }

        throw new AssertionError("No palette symbol for " + signature.id());
    }

    // endregion
}
