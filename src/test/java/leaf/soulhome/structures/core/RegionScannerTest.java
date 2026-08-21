/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionScannerTest
{
    /** Every block is a potential open-air signal unless a test narrows it. */
    private static final Predicate<BlockSignature> ANY_CROP =
            signature -> signature.hasTag("minecraft:crops");

    private static List<SoulRegion> scan(GridVolume volume)
    {
        return RegionScanner.scan(volume, ANY_CROP, ScanSettings.DEFAULTS);
    }

    private static List<SoulRegion> scanWithoutSignals(GridVolume volume)
    {
        return RegionScanner.scan(volume, null, ScanSettings.DEFAULTS);
    }

    private static List<SoulRegion> scanWithGeometry(GridVolume volume, Predicate<BlockSignature> geometryFilter)
    {
        return RegionScanner.scan(volume, ANY_CROP, geometryFilter, ScanSettings.DEFAULTS);
    }

    // region enclosed rooms

    @Test
    @DisplayName("a sealed room is found, with its walls as boundary")
    void sealedRoomIsFound()
    {
        List<SoulRegion> regions = scan(sealedRoom());

        assertEquals(1, regions.size());

        SoulRegion room = regions.get(0);
        assertEquals(RegionType.ENCLOSED, room.type());
        assertEquals(9, room.volume(), "a 3x3x1 interior");

        // floor and ceiling are 3x3 each, the walls are four runs of three
        assertEquals(30, room.boundary().total());
        assertTrue(room.contents().isEmpty(), "an empty room has no contents");
    }

    @Test
    @DisplayName("an open door does not leak the room")
    void openDoorDoesNotLeakTheRoom()
    {
        // doors count as boundary whether open or shut. Otherwise a player's buffs would blink
        // out every time they walked through their own front door.
        List<SoulRegion> regions = scan(GridVolume.of(
                floor(),
                new String[]{
                        "#####",
                        "#...#",
                        "D...#",
                        "#...#",
                        "#####"},
                floor()));

        assertEquals(1, regions.size());
        assertEquals(9, regions.get(0).volume());
        assertEquals(RegionType.ENCLOSED, regions.get(0).type());
    }

    @Test
    @DisplayName("a room with a hole in the roof is not a room")
    void holeInTheRoofIsNotARoom()
    {
        List<SoulRegion> regions = scan(GridVolume.of(
                floor(),
                new String[]{
                        "#####",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#####"},
                new String[]{
                        "#####",
                        "#####",
                        "##.##",
                        "#####",
                        "#####"}));

        assertTrue(regions.isEmpty(), "open to the sky, so it is outdoors rather than a room");
    }

    @Test
    @DisplayName("a room inside a room yields both")
    void nestedRoomsAreBothFound()
    {
        List<SoulRegion> regions = scan(GridVolume.of(
                bigFloor(),
                new String[]{
                        "#######",
                        "#.....#",
                        "#.###.#",
                        "#.#.#.#",
                        "#.###.#",
                        "#.....#",
                        "#######"},
                bigFloor()));

        assertEquals(2, regions.size());

        // sorted richest-first, so the outer ring comes back before the closet
        assertEquals(16, regions.get(0).volume(), "the ring between the two shells");
        assertEquals(1, regions.get(1).volume(), "the single cell at the centre");
    }

    @Test
    @DisplayName("furniture inside a room is contents, walls are boundary")
    void furnitureIsSeparatedFromWalls()
    {
        // a lectern standing in the middle of the floor, bookshelves lining one wall
        List<SoulRegion> regions = scan(GridVolume.of(
                floor(),
                new String[]{
                        "#BBB#",
                        "#...#",
                        "#.L.#",
                        "#...#",
                        "#####"},
                floor()));

        assertEquals(1, regions.size());
        SoulRegion room = regions.get(0);

        assertEquals(1, room.contents().count(BlockMatcher.ofBlocks("minecraft:lectern")),
                "the lectern stands inside the room");
        assertEquals(3, room.boundary().count(BlockMatcher.ofTags("minecraft:bookshelves")),
                "the bookshelves are part of the wall");
        assertEquals(4, room.allBlocks().count(
                        new BlockMatcher(List.of("minecraft:lectern"), List.of("minecraft:bookshelves"))),
                "both feed the classifier");
    }

    @Test
    @DisplayName("an enclosed volume larger than the cap is not a room")
    void oversizedPocketIsDiscarded()
    {
        ScanSettings tiny = new ScanSettings(4, 4, 4, 64, 4_000_000L);
        List<SoulRegion> regions = RegionScanner.scan(sealedRoom(), null, tiny);

        assertTrue(regions.isEmpty(), "9 interior cells against a cap of 4");
    }

    // endregion

    // region open air

    @Test
    @DisplayName("a crop field under an open sky is found as an open region")
    void openAirCropFieldIsFound()
    {
        // the author's own headline example, and invisible to room detection: a farm has no roof
        List<SoulRegion> regions = scan(GridVolume.of(
                new String[]{
                        "fffff",
                        "fffff",
                        "fffff",
                        "fffff",
                        "fffff"},
                new String[]{
                        "wwwww",
                        "wwwww",
                        "wwwww",
                        "wwwww",
                        "wwwww"}));

        assertEquals(1, regions.size());

        SoulRegion field = regions.get(0);
        assertEquals(RegionType.OPEN, field.type());
        assertEquals(25, field.allBlocks().count(BlockMatcher.ofTags("minecraft:crops")));
        assertEquals(25, field.allBlocks().count(BlockMatcher.ofBlocks("minecraft:farmland")),
                "the ground the crops grow in counts as part of the farm");
        assertTrue(field.boundary().isEmpty(), "an open region has no shell");
    }

    @Test
    @DisplayName("a lone crop is too sparse to be a farm")
    void singleSignalBlockIsNotAStructure()
    {
        List<SoulRegion> regions = scan(GridVolume.of(
                new String[]{
                        ".....",
                        ".....",
                        "..f..",
                        ".....",
                        "....."},
                new String[]{
                        ".....",
                        ".....",
                        "..w..",
                        ".....",
                        "....."}));

        assertTrue(regions.isEmpty(), "one planted seed is not a farm");
    }

    @Test
    @DisplayName("crops sealed inside a room are not also clustered as open air")
    void roomBlocksAreNotReusedByClustering()
    {
        // an indoor planter: the crops belong to the room, and must not be double counted
        List<SoulRegion> regions = scan(GridVolume.of(
                floor(),
                new String[]{
                        "#####",
                        "#fff#",
                        "#fff#",
                        "#fff#",
                        "#####"},
                new String[]{
                        "#####",
                        "#www#",
                        "#www#",
                        "#www#",
                        "#####"},
                floor()));

        assertEquals(1, regions.size(), "one room, no separate open-air farm");
        assertEquals(RegionType.ENCLOSED, regions.get(0).type());
    }

    @Test
    @DisplayName("open-air detection is skipped entirely without a signal filter")
    void noSignalFilterMeansNoOpenRegions()
    {
        List<SoulRegion> regions = scanWithoutSignals(GridVolume.of(
                new String[]{"fffff", "fffff", "fffff"},
                new String[]{"wwwww", "wwwww", "wwwww"}));

        assertTrue(regions.isEmpty());
    }

    // endregion

    @Test
    @DisplayName("an empty dimension yields nothing")
    void emptyDimensionYieldsNothing()
    {
        List<SoulRegion> regions = scan(GridVolume.of(
                new String[]{".....", ".....", "....."},
                new String[]{".....", ".....", "....."},
                new String[]{".....", ".....", "....."}));

        assertTrue(regions.isEmpty());
    }

    @Test
    @DisplayName("the region cap keeps the richest regions")
    void regionCapKeepsTheRichest()
    {
        // a small room and a large one, side by side
        String[] slab = {
                "###########",
                "###########",
                "###########",
                "###########",
                "###########"};

        GridVolume volume = GridVolume.of(
                slab,
                new String[]{
                        "###########",
                        "#..##.....#",
                        "#..##.....#",
                        "#..##.....#",
                        "###########"},
                slab);

        List<SoulRegion> both = RegionScanner.scan(volume, null, ScanSettings.DEFAULTS);
        assertEquals(2, both.size());
        assertEquals(15, both.get(0).volume(), "richest first");
        assertEquals(6, both.get(1).volume());

        ScanSettings cappedToOne = new ScanSettings(4096, 4, 4, 1, 4_000_000L);
        List<SoulRegion> capped = RegionScanner.scan(volume, null, cappedToOne);

        assertEquals(1, capped.size());
        assertEquals(both.get(0).identityHash(), capped.get(0).identityHash(),
                "the cap drops the least interesting region, not an arbitrary one");
    }

    @Test
    @DisplayName("identity hashes are stable across scans and move when a block does")
    void identityHashTracksContent()
    {
        long first = scan(sealedRoom()).get(0).identityHash();
        long second = scan(sealedRoom()).get(0).identityHash();

        assertEquals(first, second, "an untouched build must be able to skip a rescan");

        long furnished = scan(GridVolume.of(
                floor(),
                new String[]{
                        "#####",
                        "#...#",
                        "#.L.#",
                        "#...#",
                        "#####"},
                floor())).get(0).identityHash();

        assertNotEquals(first, furnished, "placing a lectern has to invalidate the cache");
    }

    @Test
    @DisplayName("an over-large scan volume is refused rather than attempted")
    void oversizedVolumeIsRefused()
    {
        RegionBounds huge = new RegionBounds(0, 0, 0, 999, 999, 999);
        assertFalse(RegionScanner.isScannable(huge, ScanSettings.DEFAULTS));
    }

    // region geometry

    private static final Predicate<BlockSignature> LECTERNS_ONLY =
            signature -> signature.hasTag("minecraft:lectern") || signature.id().equals("minecraft:lectern");

    @Test
    @DisplayName("without a geometry filter, nothing is indexed")
    void noGeometryFilterMeansNoGeometry()
    {
        SoulRegion room = scan(furnishedRoom()).get(0);

        assertTrue(room.geometry().isEmpty());
        assertFalse(room.geometry().isTruncated());
    }

    @Test
    @DisplayName("only blocks the geometry filter names are indexed")
    void onlyFilteredBlocksAreIndexed()
    {
        SoulRegion room = scanWithGeometry(furnishedRoom(), LECTERNS_ONLY).get(0);

        assertEquals(1, room.geometry().size(), "the room also has bookshelves, which the filter excludes");
        assertEquals("minecraft:lectern", room.geometry().cells().get(0).signature().id());
    }

    @Test
    @DisplayName("cellsMatching agrees with BlockCounts.count for a matcher within the filter")
    void cellsMatchingAgreesWithBlockCounts()
    {
        Predicate<BlockSignature> anyFurniture = signature ->
                signature.hasTag("minecraft:bookshelves") || signature.id().equals("minecraft:lectern");

        SoulRegion room = scanWithGeometry(furnishedRoom(), anyFurniture).get(0);

        BlockMatcher shelves = BlockMatcher.ofTags("minecraft:bookshelves");
        assertEquals(room.allBlocks().count(shelves), room.geometry().cellsMatching(shelves).size());

        BlockMatcher lectern = BlockMatcher.ofBlocks("minecraft:lectern");
        assertEquals(room.allBlocks().count(lectern), room.geometry().cellsMatching(lectern).size());
    }

    @Test
    @DisplayName("geometry is deterministic across two scans of the same volume")
    void geometryIsDeterministic()
    {
        Predicate<BlockSignature> anyFurniture = signature ->
                signature.hasTag("minecraft:bookshelves") || signature.id().equals("minecraft:lectern");

        List<RegionGeometry.Cell> first = scanWithGeometry(furnishedRoom(), anyFurniture).get(0).geometry().cells();
        List<RegionGeometry.Cell> second = scanWithGeometry(furnishedRoom(), anyFurniture).get(0).geometry().cells();

        assertEquals(first, second);
    }

    @Test
    @DisplayName("identity hash changes when a filtered block moves without any count changing")
    void identityHashChangesOnMoveAlone()
    {
        long before = scanWithGeometry(furnishedRoom(), LECTERNS_ONLY).get(0).identityHash();
        long after = scanWithGeometry(furnishedRoomWithLecternMoved(), LECTERNS_ONLY).get(0).identityHash();

        assertNotEquals(before, after, "the lectern moved to a different cell, so the hash must move too");
    }

    @Test
    @DisplayName("identity hash is stable across two scans when nothing moves")
    void identityHashStableWhenNothingMoves()
    {
        long first = scanWithGeometry(furnishedRoom(), LECTERNS_ONLY).get(0).identityHash();
        long second = scanWithGeometry(furnishedRoom(), LECTERNS_ONLY).get(0).identityHash();

        assertEquals(first, second);
    }

    @Test
    @DisplayName("without geometry indexing, moving a block leaves the identity hash unchanged")
    void identityHashIgnoresMovementWithoutAGeometryFilter()
    {
        // the regression #26 exists to close: before geometry, sliding furniture around changed no
        // count, so the hash never moved and a rearranged soulhome's buffs never refreshed
        long before = scan(furnishedRoom()).get(0).identityHash();
        long after = scan(furnishedRoomWithLecternMoved()).get(0).identityHash();

        assertEquals(before, after, "with nothing indexing position, the hash can only see the counts");
    }

    @Test
    @DisplayName("truncation past maxGeometryCells sets the flag rather than throwing")
    void truncationSetsFlagRatherThanThrowing()
    {
        ScanSettings tinyGeometry = new ScanSettings(4096, 4, 4, 64, 4_000_000L, 2);
        Predicate<BlockSignature> anyBookshelf = signature -> signature.hasTag("minecraft:bookshelves");

        List<SoulRegion> regions = RegionScanner.scan(furnishedRoom(), ANY_CROP, anyBookshelf, tinyGeometry);

        assertEquals(1, regions.size());
        SoulRegion room = regions.get(0);

        assertTrue(room.geometry().isTruncated(), "the room holds three bookshelves against a cap of two");
        assertEquals(2, room.geometry().size());
    }

    @Test
    @DisplayName("an index under its cap is not marked truncated")
    void geometryUnderCapIsNotTruncated()
    {
        SoulRegion room = scanWithGeometry(furnishedRoom(), LECTERNS_ONLY).get(0);
        assertFalse(room.geometry().isTruncated());
    }

    // endregion

    // region layouts

    private static GridVolume sealedRoom()
    {
        return GridVolume.of(
                floor(),
                new String[]{
                        "#####",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#####"},
                floor());
    }

    private static String[] floor()
    {
        return new String[]{"#####", "#####", "#####", "#####", "#####"};
    }

    private static String[] bigFloor()
    {
        return new String[]{"#######", "#######", "#######", "#######", "#######", "#######", "#######"};
    }

    /** Three bookshelves on the back wall (boundary) and one lectern standing in the room (contents). */
    private static GridVolume furnishedRoom()
    {
        return GridVolume.of(
                floor(),
                new String[]{
                        "#BBB#",
                        "#...#",
                        "#L..#",
                        "#...#",
                        "#####"},
                floor());
    }

    /**
     * {@link #furnishedRoom()} with the lectern swapped to the mirror cell in the same row, so the
     * block counts are identical - this is a pure move, not a move that also changes what shell
     * cells the room happens to claim.
     */
    private static GridVolume furnishedRoomWithLecternMoved()
    {
        return GridVolume.of(
                floor(),
                new String[]{
                        "#BBB#",
                        "#...#",
                        "#..L#",
                        "#...#",
                        "#####"},
                floor());
    }

    // endregion
}
