/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #144, #145 and #146: the seven shipped relations, each graded off a real scan's
 * {@link RegionAdjacency} rather than a hand-built one, so the layouts read as the shapes they
 * describe.
 */
class BondRelationsTest
{
    private static final int REACH = 24;

    private static RegionScanner.ScanResult scan(GridVolume volume)
    {
        return RegionScanner.scanWithAdjacency(
                volume, signature -> signature.hasTag("minecraft:crops"), null, false, REACH, ScanSettings.DEFAULTS);
    }

    private static ClauseParams defaults(BondRelation relation)
    {
        ClauseParams.Builder params = ClauseParams.builder();

        for (ClauseParamSpec spec : relation.params())
        {
            params.put(spec.name(), spec.defaultValue());
        }

        return params.build();
    }

    private static ClauseParams with(BondRelation relation, String name, Object value)
    {
        ClauseParams.Builder params = ClauseParams.builder();

        for (ClauseParamSpec spec : relation.params())
        {
            params.put(spec.name(), spec.name().equals(name) ? value : spec.defaultValue());
        }

        return params.build();
    }

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

    // region adjoins (#144)

    @Test
    @DisplayName("adjoins grades continuously: a quarter of a wall shared is about a quarter adjoined")
    void adjoinsIsGraded()
    {
        BondRelation adjoins = new BondRelations.Adjoins();

        // two 4x4x2 rooms sharing a 4x2 wall of eight cells; a quarter of it is two cells
        RegionScanner.ScanResult whole = scan(sharingWall(4));
        RegionScanner.ScanResult quarter = scan(sharingWall(1));

        double wholeGrade = adjoins.grade(at(whole, 0), at(whole, 5), whole.adjacency(), defaults(adjoins)).confidence();
        double quarterGrade = adjoins.grade(at(quarter, 0), at(quarter, 5), quarter.adjacency(), defaults(adjoins)).confidence();

        assertEquals(1.0d, wholeGrade, 1e-9, "the whole wall");
        assertEquals(0.25d, quarterGrade, 1e-9, "a quarter of it");
    }

    @Test
    @DisplayName("adjoins grades against the smaller room, so a closet off a hall is not penalised for the hall's size")
    void adjoinsGradesAgainstTheSmallerRoom()
    {
        BondRelation adjoins = new BondRelations.Adjoins();
        RegionScanner.ScanResult result = scan(closetOffHall());

        int closet = at(result, 0);
        int hall = at(result, 4);

        BondGrade fromCloset = adjoins.grade(closet, hall, result.adjacency(), defaults(adjoins));
        BondGrade fromHall = adjoins.grade(hall, closet, result.adjacency(), defaults(adjoins));

        assertEquals(1.0d, fromCloset.confidence(), 1e-9, "the closet's whole wall is the hall's: " + fromCloset);
        assertEquals(fromCloset.confidence(), fromHall.confidence(), 1e-9, "symmetric");
    }

    @Test
    @DisplayName("two rooms touching at a single corner do not adjoin")
    void cornerContactIsNotAdjoining()
    {
        BondRelation adjoins = new BondRelations.Adjoins();
        RegionScanner.ScanResult result = scan(cornerToCorner());

        assertEquals(2, result.regions().size(), result.regions().toString());

        BondGrade grade = adjoins.grade(0, 1, result.adjacency(), defaults(adjoins));
        assertEquals(0d, grade.confidence(), 1e-9, grade.diagnostic());
    }

    // endregion

    // region near (#144)

    @Test
    @DisplayName("near is geodesic: the same straight-line distance with rock between grades lower")
    void nearIsGeodesic()
    {
        BondRelation near = new BondRelations.Near();

        RegionScanner.ScanResult open = scan(twoRoomsApart(3, false));
        RegionScanner.ScanResult walled = scan(twoRoomsWithRockBetween());

        double clear = near.grade(at(open, 0), at(open, 8), open.adjacency(), defaults(near)).confidence();
        double rock = near.grade(at(walled, 0), at(walled, 8), walled.adjacency(), defaults(near)).confidence();

        assertEquals(1d - 3d / 12d, clear, 1e-9);
        assertTrue(rock < clear, "rock in the way: " + rock + " vs " + clear);
    }

    @Test
    @DisplayName("near names the gap and the threshold when it misses")
    void nearExplainsAMiss()
    {
        BondRelation near = new BondRelations.Near();
        RegionScanner.ScanResult result = scan(twoRoomsApart(3, false));

        BondGrade grade = near.grade(at(result, 0), at(result, 8), result.adjacency(), with(near, "max_distance", 2));

        assertEquals(0d, grade.confidence(), 1e-9);
        assertEquals("3 blocks away; within 2 would count", grade.diagnostic());
    }

    // endregion

    // region connects (#145)

    @Test
    @DisplayName("a door in a shared wall connects at 1.0; the same wall with no opening adjoins but does not connect")
    void aDoorConnectsAndAWallDoesNot()
    {
        BondRelation connects = new BondRelations.Connects();
        BondRelation adjoins = new BondRelations.Adjoins();

        RegionScanner.ScanResult doored = scan(twoRooms('D'));
        RegionScanner.ScanResult walled = scan(twoRooms('#'));

        assertEquals(1.0d, connects.grade(at(doored, 0), at(doored, 4), doored.adjacency(), defaults(connects)).confidence(), 1e-9);

        BondGrade wall = connects.grade(at(walled, 0), at(walled, 4), walled.adjacency(), defaults(connects));
        assertEquals(0d, wall.confidence(), 1e-9);
        assertEquals("shares a wall with it but there is no way through", wall.diagnostic());
        assertTrue(adjoins.grade(at(walled, 0), at(walled, 4), walled.adjacency(), defaults(adjoins)).confidence() > 0.5d,
                "still fully adjoined");
    }

    @Test
    @DisplayName("a corridor connects, falling off with its length")
    void aCorridorConnectsLessTheLongerItIs()
    {
        BondRelation connects = new BondRelations.Connects();

        RegionScanner.ScanResult shorter = scan(twoRoomsApart(3, true));
        RegionScanner.ScanResult longer = scan(twoRoomsApart(7, true));

        double shortGrade = connects.grade(at(shorter, 0), at(shorter, 8), shorter.adjacency(), defaults(connects)).confidence();
        double longGrade = connects.grade(at(longer, 0), at(longer, 12), longer.adjacency(), defaults(connects)).confidence();

        assertTrue(shortGrade > 0d && shortGrade < 1d, "a corridor is between a doorway and nothing: " + shortGrade);
        assertTrue(longGrade < shortGrade, "and a longer one is worth less: " + longGrade);
    }

    @Test
    @DisplayName("two rooms with no path between them grade 0 on connects however close")
    void noPathGradesZero()
    {
        BondRelation connects = new BondRelations.Connects();
        RegionScanner.ScanResult result = scan(twoRoomsApart(1, false));

        assertEquals(0d, connects.grade(at(result, 0), at(result, 6), result.adjacency(), defaults(connects)).confidence(), 1e-9);
    }

    // endregion

    // region above / beneath (#146)

    @Test
    @DisplayName("a cellar under a kitchen is beneath it, and the kitchen is above the cellar")
    void aCellarIsBeneathAKitchen()
    {
        RegionScanner.ScanResult result = scan(stacked(1));
        int lower = lowest(result);
        int upper = 1 - lower;

        BondRelation above = new BondRelations.Above();
        BondRelation beneath = new BondRelations.Beneath();

        assertEquals(1.0d, above.grade(upper, lower, result.adjacency(), defaults(above)).confidence(), 1e-9);
        assertEquals(1.0d, beneath.grade(lower, upper, result.adjacency(), defaults(beneath)).confidence(), 1e-9);
        assertEquals(0d, above.grade(lower, upper, result.adjacency(), defaults(above)).confidence(), 1e-9,
                "the cellar is not above the kitchen");
    }

    @Test
    @DisplayName("above grades 0 past max_vertical_gap")
    void aboveRespectsTheVerticalGap()
    {
        BondRelation above = new BondRelations.Above();
        RegionScanner.ScanResult result = scan(stacked(6));
        int lower = lowest(result);
        int upper = 1 - lower;

        BondGrade grade = above.grade(upper, lower, result.adjacency(), defaults(above));
        assertEquals(0d, grade.confidence(), 1e-9);
        assertEquals("6 blocks between them; at most 4 would count", grade.diagnostic());

        assertEquals(1.0d, above.grade(upper, lower, result.adjacency(), with(above, "max_vertical_gap", 6)).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a tower over half of a hall is half above it")
    void partialOverlapGradesBetween()
    {
        BondRelation above = new BondRelations.Above();
        RegionScanner.ScanResult result = scan(towerOverHalfAHall());
        int lower = lowest(result);
        int upper = 1 - lower;

        double grade = above.grade(upper, lower, result.adjacency(), defaults(above)).confidence();
        assertTrue(grade > 0.3d && grade < 0.7d, "about half: " + grade);
    }

    // endregion

    // region encloses / within (#146)

    @Test
    @DisplayName("a garden in a courtyard is within it, and the courtyard encloses the garden")
    void aGardenIsWithinItsCourtyard()
    {
        RegionScanner.ScanResult result = scan(courtyardWithGarden());
        assertEquals(2, result.regions().size(), result.regions().toString());

        int ring = result.regions().get(0).type() == RegionType.ENCLOSED ? 0 : 1;
        int garden = 1 - ring;

        BondRelation within = new BondRelations.Within();
        BondRelation encloses = new BondRelations.Encloses();

        assertEquals(1.0d, within.grade(garden, ring, result.adjacency(), defaults(within)).confidence(), 1e-9);
        assertEquals(1.0d, encloses.grade(ring, garden, result.adjacency(), defaults(encloses)).confidence(), 1e-9);
        assertEquals(0d, within.grade(ring, garden, result.adjacency(), defaults(within)).confidence(), 1e-9,
                "the courtyard is not inside its garden");
    }

    @Test
    @DisplayName("a shrine in the infield of a track is still its own region, and within the track")
    void aShrineInATrackIsWithinIt()
    {
        // the case #146 asked to confirm survives #133's fixes: the open-air rework leaves a build
        // standing inside another's ring as its own structure, and the track's footprint takes in
        // the infield the shrine stands in
        RegionScanner.ScanResult result = RegionScanner.scanWithAdjacency(
                shrineInTrack(),
                signature -> signature.hasTag("minecraft:rails") || signature.id().equals("minecraft:lodestone"),
                null, false, REACH, ScanSettings.DEFAULTS);

        assertEquals(2, result.regions().size(), result.regions().toString());

        int track = result.regions().get(0).allBlocks().count(BlockMatcher.ofTags("minecraft:rails")) > 0 ? 0 : 1;
        int shrine = 1 - track;

        BondRelation within = new BondRelations.Within();
        assertEquals(1.0d, within.grade(shrine, track, result.adjacency(), defaults(within)).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a region half inside another's footprint is half within it")
    void halfInsideGradesHalf()
    {
        // built by hand rather than scanned: two builds can only share ground by touching, and
        // two touching open-air builds are one build to the scanner. The formula is what is
        // under test - overlap against the inner footprint - and the 1.0 and 0 ends of it are
        // pinned above against real scans
        RegionAdjacency adjacency = new RegionAdjacency(
                0, new int[]{60, 0}, new int[2][2],
                new int[][]{{0, RegionAdjacency.UNREACHABLE}, {RegionAdjacency.UNREACHABLE, 0}},
                new int[][]{{0, 0}, {0, 0}},
                new int[]{121, 36}, new int[][]{{121, 18}, {18, 36}},
                new int[]{1, 0}, new int[]{2, 1}, new int[]{20, 0});

        BondRelation within = new BondRelations.Within();
        BondRelation encloses = new BondRelations.Encloses();

        assertEquals(0.5d, within.grade(1, 0, adjacency, defaults(within)).confidence(), 1e-9);
        assertEquals(0.5d, encloses.grade(0, 1, adjacency, defaults(encloses)).confidence(), 1e-9);
        assertEquals(0d, within.grade(1, 0, adjacency, with(within, "min_coverage", 0.75d)).confidence(), 1e-9,
                "and below min_coverage it grades nothing");
    }

    // endregion

    // region descriptions, for the book (#150)

    @Test
    @DisplayName("every relation describes itself with its numbers stated")
    void everyRelationDescribesItself()
    {
        for (BondRelation relation : BondRelations.all())
        {
            String description = relation.describe(defaults(relation));
            assertTrue(description != null && !description.isBlank(), relation.id());
        }

        assertEquals("within 12 blocks", new BondRelations.Near().describe(defaults(new BondRelations.Near())));
    }

    // endregion

    // region layouts

    private static int lowest(RegionScanner.ScanResult result)
    {
        return result.regions().get(0).bounds().minY() <= result.regions().get(1).bounds().minY() ? 0 : 1;
    }

    /** Two 4x4x2 rooms sharing a wall two blocks high; {@code openness} of its four columns... are still wall, the rest is offset to touch only that many. */
    private static GridVolume sharingWall(int sharedColumns)
    {
        // the right-hand room is shifted along z so only sharedColumns of the wall's four columns
        // face both rooms; the rest of each room's wall faces open ground
        final int shift = 4 - sharedColumns;
        final int depth = 4 + shift;
        String[] slab = new String[depth + 2];
        Arrays.fill(slab, "###########");

        String[] rooms = new String[depth + 2];

        for (int z = 0; z < depth + 2; z++)
        {
            StringBuilder row = new StringBuilder(".".repeat(11));
            // left room occupies z 0..5 (walls at 0 and 5), x 0..5
            if (z <= 5)
            {
                row.replace(0, 6, z == 0 || z == 5 ? "######" : "#....#");
            }
            // right room occupies z shift..shift+5, x 5..10, sharing the wall at x=5
            if (z >= shift && z <= shift + 5)
            {
                boolean end = z == shift || z == shift + 5;
                row.replace(5, 11, end ? "######" : "#....#");
            }
            rooms[z] = row.toString();
        }

        return GridVolume.of(slab, rooms, rooms, slab);
    }

    /** A 3x3x2 closet against a 7x5x2 hall, the closet's whole wall being the hall's. */
    private static GridVolume closetOffHall()
    {
        String[] slab = new String[7];
        Arrays.fill(slab, "#############");
        String[] rooms = {
                "#############",
                "#...#.......#",
                "#...#.......#",
                "#...#.......#",
                "#####.......#",
                "....#.......#",
                "....#########"};

        return GridVolume.of(slab, rooms, rooms, slab);
    }

    /** Two 3x3x2 rooms touching only at one vertical edge of their outer corners. */
    private static GridVolume cornerToCorner()
    {
        String[] slab = new String[9];
        Arrays.fill(slab, "#########");
        String[] rooms = {
                "#####....",
                "#...#....",
                "#...#....",
                "#...#....",
                "#########",
                "....#...#",
                "....#...#",
                "....#...#",
                "....#####"};

        return GridVolume.of(slab, rooms, rooms, slab);
    }

    private static GridVolume twoRooms(char divider)
    {
        String[] slab = {"#########", "#########", "#########", "#########", "#########"};
        String[] rooms = {
                "#########",
                "#...#...#",
                "#..." + divider + "...#",
                "#...#...#",
                "#########"};
        String[] upper = {"#########", "#...#...#", "#...#...#", "#...#...#", "#########"};

        return GridVolume.of(slab, rooms, upper, slab);
    }

    private static GridVolume twoRoomsApart(int gap, boolean corridor)
    {
        final String between = ".".repeat(gap);
        final String wallRun = "#".repeat(gap);
        final String door = corridor ? "D" : "#";

        String[] slab = new String[5];
        Arrays.fill(slab, "#####" + wallRun + "#####");

        String[] rooms = {
                "#####" + between + "#####",
                "#...#" + (corridor ? wallRun : between) + "#...#",
                "#..." + door + between + door + "...#",
                "#...#" + (corridor ? wallRun : between) + "#...#",
                "#####" + between + "#####"};
        String[] upper = {
                "#####" + between + "#####",
                "#...#" + between + "#...#",
                "#...#" + between + "#...#",
                "#...#" + between + "#...#",
                "#####" + between + "#####"};

        return GridVolume.of(slab, rooms, upper, slab);
    }

    private static GridVolume twoRoomsWithRockBetween()
    {
        String[] slab = new String[5];
        Arrays.fill(slab, "#############");
        String[] rooms = {"#############", "#...#####...#", "#...#####...#", "#...#####...#", "#############"};
        String[] cap = {".....###.....", ".....###.....", ".....###.....", ".....###.....", ".....###....."};

        return GridVolume.of(slab, rooms, rooms, slab, cap, cap);
    }

    /** A 3x3x2 room with another the same directly above it, {@code gap} blocks of stone between their interiors. */
    private static GridVolume stacked(int gap)
    {
        String[] slab = {"#####", "#####", "#####", "#####", "#####"};
        String[] room = {"#####", "#...#", "#...#", "#...#", "#####"};

        String[][] layers = new String[gap + 6][];
        layers[0] = slab;
        layers[1] = room;
        layers[2] = room;

        for (int i = 0; i < gap; i++)
        {
            layers[3 + i] = slab;
        }

        layers[3 + gap] = room;
        layers[4 + gap] = room;
        layers[5 + gap] = slab;

        return GridVolume.of(layers);
    }

    /** A 3x3x2 tower whose footprint half overhangs the end of a 5x3x2 hall, the rest on air. */
    private static GridVolume towerOverHalfAHall()
    {
        String[] hallSlab = {"#######...", "#######...", "#######...", "#######...", "#######..."};
        String[] hall = {"#######...", "#.....#...", "#.....#...", "#.....#...", "#######..."};
        // the tower's box is x 4..8; the hall's is x 0..6, so three of the tower's five
        // columns stand over the hall and two hang out past its end wall
        String[] towerSlab = {"....#####", "....#####", "....#####", "....#####", "....#####"};
        String[] tower = {"....#####", "....#...#", "....#...#", "....#...#", "....#####"};

        String[] slab = new String[5];
        String[] hallRow = new String[5];
        String[] tSlab = new String[5];
        String[] tRow = new String[5];

        for (int z = 0; z < 5; z++)
        {
            slab[z] = hallSlab[z].substring(0, 9);
            hallRow[z] = hall[z].substring(0, 9);
            tSlab[z] = towerSlab[z];
            tRow[z] = tower[z];
        }

        return GridVolume.of(slab, hallRow, hallRow, slab, tSlab, tRow, tRow, tSlab);
    }

    private static GridVolume courtyardWithGarden()
    {
        String[] ground = new String[11];
        Arrays.fill(ground, "###########");
        ground[4] = "####fff####";
        ground[5] = "####fff####";
        ground[6] = "####fff####";

        String[] walls = {
                "###########", "#.........#", "#.#######.#", "#.#.....#.#", "#.#.www.#.#", "#.#.www.#.#",
                "#.#.www.#.#", "#.#.....#.#", "#.#######.#", "#.........#", "###########"};
        String[] upper = {
                "###########", "#.........#", "#.#######.#", "#.#.....#.#", "#.#.....#.#", "#.#.....#.#",
                "#.#.....#.#", "#.#.....#.#", "#.#######.#", "#.........#", "###########"};
        String[] roof = {
                "###########", "###########", "###########", "###.....###", "###.....###", "###.....###",
                "###.....###", "###.....###", "###########", "###########", "###########"};

        return GridVolume.of(ground, walls, upper, roof);
    }

    /**
     * An 11x11 rail loop with a lodestone shrine standing in its infield, three clear cells in
     * from the rails - further than a cluster reaches, so the shrine is its own build.
     */
    private static GridVolume shrineInTrack()
    {
        String[] ground = new String[13];
        Arrays.fill(ground, "#############");
        String[] track = {
                ".............",
                ".===========.",
                ".=.........=.",
                ".=.........=.",
                ".=.........=.",
                ".=...QQQ...=.",
                ".=...QQQ...=.",
                ".=...QQQ...=.",
                ".=.........=.",
                ".=.........=.",
                ".=.........=.",
                ".===========.",
                "............."};

        return GridVolume.of(ground, track);
    }

    // endregion
}
