/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    @DisplayName("several different partial blocks, each closing its own gap, together seal a room")
    void severalPartialBlocksTogetherSealTheRoom()
    {
        // Passability#stopsFill() answers "does this seal a space" unconditionally for PARTIAL,
        // regardless of how many other gaps the shell has - a fence in one gap and a wall in
        // another each seal on their own, with no need to reason about them as a pair. A room
        // whose enclosure depends on several ambiguous blocks at once is exactly the case #62's
        // discussion worried would need special handling; it does not.
        List<SoulRegion> regions = scan(GridVolume.of(
                floor(),
                new String[]{
                        "#F###",
                        "#...#",
                        "|...#",
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
                hugeFloor(),
                new String[]{
                        "#########",
                        "#.......#",
                        "#.#####.#",
                        "#.#...#.#",
                        "#.#...#.#",
                        "#.#...#.#",
                        "#.#####.#",
                        "#.......#",
                        "#########"},
                hugeFloor()));

        assertEquals(2, regions.size());

        // sorted richest-first, so the outer ring comes back before the inner chamber
        assertEquals(24, regions.get(0).volume(), "the ring between the two shells");
        assertEquals(9, regions.get(1).volume(), "the chamber at the centre");
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
        assertEquals(3, room.boundary().count(BlockMatcher.ofTags("soulhome:bookshelves")),
                "the bookshelves are part of the wall");
        assertEquals(4, room.allBlocks().count(
                        new BlockMatcher(List.of("minecraft:lectern"), List.of("soulhome:bookshelves"))),
                "both feed the classifier");
    }

    @Test
    @DisplayName("an enclosed volume larger than the cap is not a room")
    void oversizedPocketIsDiscarded()
    {
        ScanSettings tiny = ScanSettings.DEFAULTS.withRoomVolumeRange(1, 4);
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

    // endregion

    // region telling one structure from the next (#60)

    /** Everything the shipped archetypes name between them, which is what the game passes in. */
    private static final Predicate<BlockSignature> ANY_SIGNAL = signature ->
            signature.hasTag("minecraft:crops")
                    || signature.hasTag("minecraft:rails")
                    || signature.hasTag("minecraft:fences")
                    || signature.hasTag("soulhome:lighting")
                    || signature.id().equals("minecraft:farmland")
                    || signature.id().equals("minecraft:hay_block")
                    || signature.id().equals("minecraft:water");

    private static List<SoulRegion> scanForAnySignal(GridVolume volume)
    {
        return RegionScanner.scan(volume, ANY_SIGNAL, ScanSettings.DEFAULTS);
    }

    private static int countIn(SoulRegion region, String tag)
    {
        return region.allBlocks().count(BlockMatcher.ofTags(tag));
    }

    @Test
    @DisplayName("a farm and a track with clear ground between them are two regions")
    void separateOpenBuildsAreSeparateRegions()
    {
        // the report this came from: three blocks of nothing between a field and a racetrack, and
        // the two came back as one region that then scored as neither. Every block either build is
        // made of is a signal, so nothing but the space between them tells them apart.
        List<SoulRegion> regions = scanForAnySignal(farmAndTrackThreeApart());

        assertEquals(2, regions.size());

        SoulRegion farm = regions.get(0);
        SoulRegion track = regions.get(1);

        assertEquals(16, countIn(farm, "minecraft:crops"));
        assertEquals(0, countIn(farm, "minecraft:rails"), "the track is not part of the farm");
        assertEquals(12, countIn(track, "minecraft:rails"));
        assertEquals(0, countIn(track, "minecraft:crops"), "nor the farm part of the track");
    }

    @Test
    @DisplayName("how far a cluster reaches is a setting, not a rule")
    void theClusterReachIsASetting()
    {
        // for anyone who wants the old grouping back, or who builds at a different scale
        GridVolume volume = farmAndTrackThreeApart();

        assertEquals(2, RegionScanner.scan(volume, ANY_SIGNAL, ScanSettings.DEFAULTS).size());
        assertEquals(1, RegionScanner.scan(
                        volume, ANY_SIGNAL, ScanSettings.DEFAULTS.withClusterRadius(4)).size(),
                "one step further and the two are near enough to be one thing again");
    }

    @Test
    @DisplayName("a gap narrow enough to step over leaves one field, not two")
    void aNarrowGapDoesNotSplitAField()
    {
        // the other half of the same question: a farm with a path through it is still one farm,
        // which is why the reach is a couple of blocks rather than none at all
        List<SoulRegion> regions = scanForAnySignal(wallOrGap(false));

        assertEquals(1, regions.size());
        assertEquals(12, countIn(regions.get(0), "minecraft:crops"));
        assertEquals(8, countIn(regions.get(0), "minecraft:rails"),
                "with nothing between them, two blocks apart is near enough to be one thing");
    }

    @Test
    @DisplayName("a wall between two open builds separates them")
    void aWallSeparatesOpenRegions()
    {
        // same layout, same distance, with a wall standing in the gap. Building a wall is the
        // thing a player reaches for to say "these are two different places", and before this it
        // did nothing at all: proximity was measured straight through solid rock.
        List<SoulRegion> regions = scanForAnySignal(wallOrGap(true));

        assertEquals(2, regions.size());

        SoulRegion farm = regions.get(0);
        SoulRegion track = regions.get(1);

        assertEquals(12, countIn(farm, "minecraft:crops"));
        assertEquals(0, countIn(farm, "minecraft:rails"));
        assertEquals(8, countIn(track, "minecraft:rails"));
        assertEquals(0, countIn(track, "minecraft:crops"));
    }

    @Test
    @DisplayName("a fence or wall around a build does not cut it off from what is just outside it")
    void aDecorativeWallDoesNotSeparate()
    {
        // a rail circuit ringed by cobblestone wall, with hay laid just outside the ring. A fence,
        // a wall, a pane or a slab is something a player puts inside one build - the track
        // archetype scores fencing as part of a track - so treating each of them as the edge of a
        // build would cut a fenced circuit off from its own trackside.
        List<SoulRegion> regions = scanForAnySignal(ringedTrack('|'));

        assertEquals(1, regions.size(), "the wall is decoration, not a boundary");
        assertEquals(16, countIn(regions.get(0), "minecraft:rails"));
        assertEquals(32, regions.get(0).allBlocks().count(BlockMatcher.ofBlocks("minecraft:hay_block")),
                "the hay outside the ring is part of the same build");
    }

    @Test
    @DisplayName("a solid wall between two builds still separates them, where a fence does not")
    void onlyFullBlocksSeparate()
    {
        // the same shape and the same height, with the ring built out of full blocks instead. Kept
        // next to the case above deliberately: the difference between the two is the whole rule.
        List<SoulRegion> regions = scanForAnySignal(ringedTrack('#'));

        assertEquals(2, regions.size(), "a wall of full blocks is a boundary");

        SoulRegion hay = regions.get(0);
        SoulRegion track = regions.get(1);

        assertEquals(0, countIn(hay, "minecraft:rails"));
        assertEquals(16, countIn(track, "minecraft:rails"));
        assertEquals(0, track.allBlocks().count(BlockMatcher.ofBlocks("minecraft:hay_block")));
    }

    @Test
    @DisplayName("a region takes in what it has closed around, rather than coming back as a ring")
    void aRegionHasNoInteriorHoles()
    {
        // a rail loop with a solid stone infield. Nothing in there is a signal, so nothing in there
        // was ever reached - and the clearance index a form reads is only written for cells the
        // region took in, so an infield of solid rock used to read back as clear open space.
        List<SoulRegion> regions = RegionScanner.scan(
                GridVolume.of(
                        new String[]{"#######", "#######", "#######", "#######", "#######", "#######", "#######"},
                        new String[]{
                                ".......",
                                ".=====.",
                                ".=YYY=.",
                                ".=YYY=.",
                                ".=YYY=.",
                                ".=====.",
                                "......."}),
                ANY_SIGNAL, ANY_SIGNAL, true, ScanSettings.DEFAULTS);

        assertEquals(1, regions.size());

        SoulRegion track = regions.get(0);
        assertTrue(track.geometry().isBlocked(3, 1, 3),
                "the middle of the infield is solid, and the region has to know it");
        assertEquals(9, track.allBlocks().count(BlockMatcher.ofBlocks("minecraft:gold_block")),
                "all nine infield blocks belong to the track, not just the ring the rails touch");
    }

    @Test
    @DisplayName("a solid mass of signal blocks is taken in whole, not skinned")
    void aMassOfSignalBlocksIsNotAHollowShell()
    {
        // hay bales, ice and farmland are all full blocks. If the spread stopped at a full block
        // without first asking whether it is a signal, a haystack would come back as a hollow
        // shell of its own outside faces with its middle unaccounted for.
        String[] solid = {"hhhhh", "hhhhh", "hhhhh", "hhhhh", "hhhhh"};

        List<SoulRegion> regions = scanForAnySignal(GridVolume.of(solid, solid, solid));

        assertEquals(1, regions.size());
        assertEquals(75, regions.get(0).allBlocks().count(BlockMatcher.ofBlocks("minecraft:hay_block")),
                "every bale, including the ones buried in the middle");
    }

    @Test
    @DisplayName("a sparse trail of signal blocks does not drag a region across the build")
    void aSparseTrailDoesNotStretchARegion()
    {
        // every torch is a signal, and each one used to be within reach of the last, so a lit path
        // leading away from a farm towed the farm's region along behind it - and the region then
        // scored badly for being mostly empty space
        List<SoulRegion> regions = scanForAnySignal(GridVolume.of(
                new String[]{
                        "ffff.......................",
                        "ffff.......................",
                        "ffff.......................",
                        "ffff......................."},
                new String[]{
                        "wwww....t....t....t....t...",
                        "wwww.......................",
                        "wwww.......................",
                        "wwww......................."}));

        assertEquals(1, regions.size(), "the torches are too far apart to be a structure of their own");
        assertTrue(regions.get(0).bounds().maxX() <= 5,
                "the farm's region stops at the farm, rather than reaching the last torch: "
                        + regions.get(0).bounds());
    }

    @Test
    @DisplayName("an open region takes in the ground under it without taking in its whole box")
    void openRegionSlackFollowsTheClusterNotItsBoundingBox()
    {
        // an L-shaped field with a stack of bookshelves standing in the corner the L does not
        // occupy. The farmland a block under the crops is part of the farm; the bookshelves are
        // not, and a bounding box could not tell the two apart.
        List<SoulRegion> regions = scanForAnySignal(GridVolume.of(
                new String[]{
                        "ffffff",
                        "ffffff",
                        "ff....",
                        "ff....",
                        "ff....",
                        "ff...."},
                new String[]{
                        "wwwwww",
                        "wwwwww",
                        "ww....",
                        "ww....",
                        "ww..BB",
                        "ww..BB"}));

        assertEquals(1, regions.size());

        SoulRegion field = regions.get(0);
        assertEquals(20, countIn(field, "minecraft:crops"));
        assertEquals(20, field.allBlocks().count(BlockMatcher.ofBlocks("minecraft:farmland")),
                "the ground the crops grow in is part of the farm");
        assertEquals(0, countIn(field, "soulhome:bookshelves"),
                "the bookshelves are in the field's bounding box but not in the field");
    }

    @Test
    @DisplayName("a building's roof does not sprout an open-air region of its own")
    void aBuildingsRoofBelongsToTheBuilding()
    {
        // a barn's shell is only the layer touching the room's air, so the hay laid over its
        // ceiling belonged to nothing and clustered into a second region floating on top of the
        // first - the "why are there two boxes here" the report opens with
        List<SoulRegion> regions = scanForAnySignal(GridVolume.of(
                new String[]{"#######", "#######", "#######", "#######", "#######"},
                new String[]{
                        "#######",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#######"},
                new String[]{"#######", "#######", "#######", "#######", "#######"},
                new String[]{"hhhhhhh", "hhhhhhh", "hhhhhhh", "hhhhhhh", "hhhhhhh"}));

        assertEquals(1, regions.size(), "the barn, and nothing else");
        assertEquals(RegionType.ENCLOSED, regions.get(0).type());
        assertEquals(0, regions.get(0).allBlocks().count(BlockMatcher.ofBlocks("minecraft:hay_block")),
                "the roof is the barn's, but it is not what the barn is scored on");
    }

    @Test
    @DisplayName("a room's own outer corners do not cluster into a region of their own")
    void aBuildingsCornersBelongToTheBuilding()
    {
        // a study built out of bookshelves. The edges and corners of the box touch no interior air,
        // so they are never part of the shell, and every one of them is a block some archetype
        // names - which is exactly what an open-air cluster forms around.
        GridVolume study = GridVolume.of(
                new String[]{"BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB"},
                new String[]{
                        "BBBBB",
                        "B...B",
                        "B...B",
                        "B...B",
                        "BBBBB"},
                new String[]{"BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB"});

        Predicate<BlockSignature> anyBookshelf = signature -> signature.hasTag("soulhome:bookshelves");

        List<SoulRegion> regions = RegionScanner.scan(study, anyBookshelf, ScanSettings.DEFAULTS);
        assertEquals(1, regions.size(), "the study, and nothing else");
        assertEquals(RegionType.ENCLOSED, regions.get(0).type());

        // and the shell depth is what is doing the work, rather than the case never arising
        List<SoulRegion> unclaimed =
                RegionScanner.scan(study, anyBookshelf, ScanSettings.DEFAULTS.withShellDepth(0));
        assertEquals(2, unclaimed.size(), "with nothing claiming them, the corners are loose blocks");
    }

    @Test
    @DisplayName("a farm in the crook of an L-shaped house is still a farm")
    void aFarmBesideAConcaveBuildingIsStillFound()
    {
        // the bounding box of an L-shaped room covers the ground the L wraps around. Excluding
        // that box - which is how a building used to claim its own footprint - meant a farm
        // planted in the corner of your own house was never reported at all.
        List<SoulRegion> regions = scanForAnySignal(GridVolume.of(
                new String[]{
                        "##########",
                        "##########",
                        "##########",
                        "##########",
                        "##########",
                        "####.ffff.",
                        "####.ffff.",
                        "####.ffff.",
                        "####......"},
                new String[]{
                        "##########",
                        "#........#",
                        "#........#",
                        "#........#",
                        "#..#######",
                        "#..#.wwww.",
                        "#..#.wwww.",
                        "#..#.wwww.",
                        "####......"},
                new String[]{
                        "##########",
                        "##########",
                        "##########",
                        "##########",
                        "##########",
                        "####......",
                        "####......",
                        "####......",
                        "####......"}));

        assertEquals(2, regions.size());

        SoulRegion house = regions.get(0);
        SoulRegion farm = regions.get(1);

        assertEquals(RegionType.ENCLOSED, house.type());
        assertEquals(RegionType.OPEN, farm.type());
        assertEquals(12, countIn(farm, "minecraft:crops"));
    }

    @Test
    @DisplayName("a sealed crevice too small to stand in is not a room")
    void tinySealedPocketsAreNotRooms()
    {
        // the void left inside a double-thick wall. Nothing can ever classify at this size, so
        // every one of them was a box drawn around nothing in the player's lens.
        List<SoulRegion> regions = scan(GridVolume.of(
                new String[]{"#####", "#####", "#####", "#####", "#####"},
                new String[]{"#####", "#####", "##.##", "#####", "#####"},
                new String[]{"#####", "#####", "#####", "#####", "#####"}));

        assertTrue(regions.isEmpty(), "a one-cell void inside a wall is not a room");
    }

    @Test
    @DisplayName("the minimum room volume is a setting, not a rule")
    void theMinimumRoomVolumeCanBeTurnedOff()
    {
        List<SoulRegion> regions = RegionScanner.scan(
                GridVolume.of(
                        new String[]{"#####", "#####", "#####", "#####", "#####"},
                        new String[]{"#####", "#####", "##.##", "#####", "#####"},
                        new String[]{"#####", "#####", "#####", "#####", "#####"}),
                null,
                ScanSettings.DEFAULTS.withRoomVolumeRange(1, 4096));

        assertEquals(1, regions.size());
        assertEquals(1, regions.get(0).volume());
    }

    /**
     * A rail circuit inside a three-block-high ring, with hay laid just outside it. Tall enough
     * that stepping over the ring is further than a cluster will reach, so what the ring is built
     * of is the only thing deciding whether the hay and the rails are one build or two.
     */
    private static GridVolume ringedTrack(char ring)
    {
        final String r = String.valueOf(ring);

        return GridVolume.of(
                new String[]{"#########", "#########", "#########", "#########", "#########",
                             "#########", "#########", "#########", "#########"},
                new String[]{
                        "hhhhhhhhh",
                        "h" + r.repeat(7) + "h",
                        "h" + r + "=====" + r + "h",
                        "h" + r + "=...=" + r + "h",
                        "h" + r + "=...=" + r + "h",
                        "h" + r + "=...=" + r + "h",
                        "h" + r + "=====" + r + "h",
                        "h" + r.repeat(7) + "h",
                        "hhhhhhhhh"},
                ringLayer(r),
                ringLayer(r),
                new String[]{".........", ".........", ".........", ".........", ".........",
                             ".........", ".........", ".........", "........."});
    }

    private static String[] ringLayer(String ring)
    {
        return new String[]{
                ".........",
                "." + ring.repeat(7) + ".",
                "." + ring + "....." + ring + ".",
                "." + ring + "....." + ring + ".",
                "." + ring + "....." + ring + ".",
                "." + ring + "....." + ring + ".",
                "." + ring + "....." + ring + ".",
                "." + ring.repeat(7) + ".",
                "........."};
    }

    /** A field and a track with three blocks of clear ground between them. */
    private static GridVolume farmAndTrackThreeApart()
    {
        return GridVolume.of(
                new String[]{
                        "ffff...###",
                        "ffff...###",
                        "ffff...###",
                        "ffff...###"},
                new String[]{
                        "wwww...===",
                        "wwww...===",
                        "wwww...===",
                        "wwww...==="});
    }

    /**
     * A field and a track two blocks apart, with and without a wall standing between them. The
     * wall runs past both builds in Z, so going around its ends is further than going over it.
     */
    private static GridVolume wallOrGap(boolean walled)
    {
        final String divider = walled ? "#" : ".";

        return GridVolume.of(
                new String[]{
                        "######",
                        "fff###",
                        "fff###",
                        "fff###",
                        "fff###",
                        "######"},
                new String[]{
                        "..." + divider + "..",
                        "www" + divider + "==",
                        "www" + divider + "==",
                        "www" + divider + "==",
                        "www" + divider + "==",
                        "..." + divider + ".."},
                new String[]{
                        "..." + divider + "..",
                        "..." + divider + "..",
                        "..." + divider + "..",
                        "..." + divider + "..",
                        "..." + divider + "..",
                        "..." + divider + ".."},
                new String[]{"......", "......", "......", "......", "......", "......"});
    }

    // endregion

    // region shared walls and floors (#136, #137)

    private static double creditIn(SoulRegion region, String blockId)
    {
        return region.allBlocks().credit(BlockMatcher.ofBlocks(blockId));
    }

    @Test
    @DisplayName("a slab between two stacked rooms is the upper room's floor, not the lower room's evidence (#136)")
    void aSharedSlabBelongsToTheRoomStandingOnIt()
    {
        // build a library, then a loft on top of it with a hay floor. The hay used to be six
        // blocks of the *lower* room's boundary, so the library beneath was a library holding
        // hay - and floor the loft in bookshelves instead and the cellar gained shelves it did
        // not contain
        List<SoulRegion> shared = scanWithoutSignals(stackedRooms(true));

        assertEquals(2, shared.size());

        SoulRegion upper = higherOf(shared.get(0), shared.get(1));
        SoulRegion lower = upper == shared.get(0) ? shared.get(1) : shared.get(0);

        assertEquals(9, creditIn(upper, "minecraft:hay_block"), 1e-9,
                "the loft keeps every block of the floor it is built out of");
        assertEquals(0, creditIn(lower, "minecraft:hay_block"), 1e-9,
                "the loft's floor says nothing about the library beneath it");
        assertEquals(6, creditIn(lower, "minecraft:bookshelf"), 1e-9);
        assertEquals(9 + 18, creditIn(lower, "minecraft:stone"), 1e-9,
                "its floor and its walls - the ceiling is the loft's, and is hay anyway");
    }

    @Test
    @DisplayName("two stacked rooms with their own floors score exactly as they always did (#136)")
    void stackedRoomsWithTheirOwnFloorsAreUntouched()
    {
        // case B from the report, kept beside case A so the rule reads as a comparison: give
        // the rooms their own slabs and nothing is shared, so nothing is split
        List<SoulRegion> separate = scanWithoutSignals(stackedRooms(false));

        assertEquals(2, separate.size());

        SoulRegion upper = higherOf(separate.get(0), separate.get(1));
        SoulRegion lower = upper == separate.get(0) ? separate.get(1) : separate.get(0);

        assertEquals(9, creditIn(upper, "minecraft:hay_block"), 1e-9);
        assertEquals(0, creditIn(lower, "minecraft:hay_block"), 1e-9);
        assertEquals(6, creditIn(lower, "minecraft:bookshelf"), 1e-9);

        // and the library's own ceiling, which nothing stands on, is still all its own: a 3x3
        // floor, a 3x3 ceiling, and 24 wall cells of which 6 are bookshelves
        assertEquals(9 + 9 + 18, creditIn(lower, "minecraft:stone"), 1e-9);
    }

    @Test
    @DisplayName("a wall shared between two rooms is credited half to each, so partitioning is break-even (#137)")
    void aSharedWallIsOneWall()
    {
        // one 15x3x2 space cut into four rooms by three bookshelf walls. 18 bookshelves placed;
        // every internal wall used to be scored twice, for 36 credits, so a player who partitioned
        // got four rooms each nearly clearing a gate the whole build could not clear once
        List<SoulRegion> rooms = scanWithoutSignals(partitionedRooms());

        assertEquals(4, rooms.size());

        double credited = 0d;

        for (SoulRegion room : rooms)
        {
            credited += creditIn(room, "minecraft:bookshelf");
        }

        assertEquals(18, credited, 1e-9, "total credit equals the number of bookshelves actually placed");

        List<SoulRegion> byX = new java.util.ArrayList<>(rooms);
        byX.sort(java.util.Comparator.comparingInt(room -> room.bounds().minX()));

        assertEquals(3, creditIn(byX.get(0), "minecraft:bookshelf"), 1e-9, "an end room sees one partition");
        assertEquals(6, creditIn(byX.get(1), "minecraft:bookshelf"), 1e-9, "a middle room sees two");
        assertEquals(6, creditIn(byX.get(2), "minecraft:bookshelf"), 1e-9);
        assertEquals(3, creditIn(byX.get(3), "minecraft:bookshelf"), 1e-9);

        // the whole-block view rounds down rather than promising a block that was not credited
        assertEquals(3, byX.get(0).allBlocks().count(BlockMatcher.ofBlocks("minecraft:bookshelf")));
    }

    @Test
    @DisplayName("a room's own outer walls are unaffected by having a neighbour (#137)")
    void outerWallsAreNotSplit()
    {
        List<SoulRegion> rooms = scanWithoutSignals(partitionedRooms());
        List<SoulRegion> byX = new java.util.ArrayList<>(rooms);
        byX.sort(java.util.Comparator.comparingInt(room -> room.bounds().minX()));

        // an end room: 3x3 floor and ceiling, a 3x2 end wall, two 3x2 side walls - all stone,
        // all its own. Its only shared cells are the partition's six bookshelves.
        assertEquals(9 + 9 + 6 + 6 + 6, creditIn(byX.get(0), "minecraft:stone"), 1e-9);
    }

    @Test
    @DisplayName("two standalone identical rooms score identically, and a single room is untouched")
    void standaloneRoomsAreUnchanged()
    {
        List<SoulRegion> two = scanWithoutSignals(GridVolume.of(
                new String[]{"###########", "###########", "###########", "###########", "###########", "###########"},
                new String[]{
                        "#BBB#.#BBB#",
                        "#...#.#...#",
                        "#...#.#...#",
                        "#...#.#...#",
                        "###########",
                        "..........."},
                new String[]{"###########", "###########", "###########", "###########", "###########", "###########"}));

        assertEquals(2, two.size());
        assertEquals(two.get(0).boundary().asMap(), two.get(1).boundary().asMap());
        assertEquals(two.get(0).allBlocks().totalCredit(), two.get(1).allBlocks().totalCredit(), 1e-9);

        SoulRegion alone = scan(sealedRoom()).get(0);
        assertEquals(30, alone.boundary().totalCredit(), 1e-9, "every one of its own blocks, in full");
        assertEquals(30, alone.boundary().total());
    }

    @Test
    @DisplayName("credit does not depend on which room the scanner reached first")
    void creditIsIndependentOfDiscoveryOrder()
    {
        // the same two stacked rooms, built the other way up: the hay-floored room below and the
        // library above. The scanner sweeps in x, y, z order, so the room it reaches first flips
        // - and what each room is credited must not
        List<SoulRegion> loftAbove = scanWithoutSignals(stackedRooms(true));
        List<SoulRegion> loftBelow = scanWithoutSignals(stackedRoomsFlipped());

        SoulRegion loftA = withHayFloor(loftAbove);
        SoulRegion loftB = withHayFloor(loftBelow);
        SoulRegion libraryA = loftA == loftAbove.get(0) ? loftAbove.get(1) : loftAbove.get(0);
        SoulRegion libraryB = loftB == loftBelow.get(0) ? loftBelow.get(1) : loftBelow.get(0);

        assertEquals(9, creditIn(loftA, "minecraft:hay_block"), 1e-9);
        assertEquals(9, creditIn(loftB, "minecraft:hay_block"), 1e-9);
        assertEquals(0, creditIn(libraryA, "minecraft:hay_block"), 1e-9);
        assertEquals(0, creditIn(libraryB, "minecraft:hay_block"), 1e-9);
        assertEquals(6, creditIn(libraryA, "minecraft:bookshelf"), 1e-9);
        assertEquals(6, creditIn(libraryB, "minecraft:bookshelf"), 1e-9);
    }

    private static SoulRegion higherOf(SoulRegion a, SoulRegion b)
    {
        return a.bounds().minY() > b.bounds().minY() ? a : b;
    }

    private static SoulRegion withHayFloor(List<SoulRegion> regions)
    {
        return creditIn(regions.get(0), "minecraft:hay_block") > 0 ? regions.get(0) : regions.get(1);
    }

    /**
     * A 3x3 library two blocks high with bookshelves on two walls, and a 3x3 loft floored in hay
     * above it. With {@code shareSlab} the hay is the library's ceiling; without it the library
     * has a stone ceiling of its own and the hay floor sits on top of that.
     */
    private static GridVolume stackedRooms(boolean shareSlab)
    {
        String[] slab = {"#####", "#####", "#####", "#####", "#####"};
        String[] hayFloor = {"#####", "#hhh#", "#hhh#", "#hhh#", "#####"};
        String[] library = {
                "#BBB#",
                "#...#",
                "#...#",
                "#...#",
                "#####"};
        String[] loft = {
                "#####",
                "#...#",
                "#...#",
                "#...#",
                "#####"};

        return shareSlab
                ? GridVolume.of(slab, library, library, hayFloor, loft, loft, slab)
                : GridVolume.of(slab, library, library, slab, hayFloor, loft, loft, slab);
    }

    /** {@link #stackedRooms(boolean) stackedRooms(true)} the other way up: the hay-floored room below. */
    private static GridVolume stackedRoomsFlipped()
    {
        String[] slab = {"#####", "#####", "#####", "#####", "#####"};
        String[] hayFloor = {"#####", "#hhh#", "#hhh#", "#hhh#", "#####"};
        String[] library = {
                "#BBB#",
                "#...#",
                "#...#",
                "#...#",
                "#####"};
        String[] loft = {
                "#####",
                "#...#",
                "#...#",
                "#...#",
                "#####"};

        // hay floor at the bottom this time: the loft stands on it, and the library above is
        // separated from the loft by a stone slab the library stands on
        return GridVolume.of(hayFloor, loft, loft, slab, library, library, slab);
    }

    /** One 15x3 space, two blocks high, partitioned into four rooms by three bookshelf walls. */
    private static GridVolume partitionedRooms()
    {
        String[] slab = new String[5];
        Arrays.fill(slab, "#################");
        String[] rooms = {
                "#################",
                "#...B...B...B...#",
                "#...B...B...B...#",
                "#...B...B...B...#",
                "#################"};

        return GridVolume.of(slab, rooms, rooms, slab);
    }

    // endregion

    // region what the ground is made of (#134, #135)

    /**
     * The real shipped archetypes and the real filters the game builds from them - these cases
     * reproduce reports from players entering a brand-new soul, and a hand-rolled predicate would
     * only prove the scanner agrees with itself.
     */
    private static List<ArchetypeDefinition> shipped()
    {
        try
        {
            return ArchetypeJsonReader.shipped();
        }
        catch (java.io.IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** What {@code ArchetypeManager} hands the scanner since #134. */
    private static List<SoulRegion> scanAsTheGameDoes(GridVolume volume)
    {
        List<ArchetypeDefinition> archetypes = shipped();
        return RegionScanner.scan(
                volume,
                ArchetypeSignals.openClusterFilterFor(archetypes),
                ArchetypeSignals.geometryFilterFor(archetypes),
                ArchetypeSignals.needsClearance(archetypes),
                ScanSettings.DEFAULTS);
    }

    @Test
    @DisplayName("two open-air builds on snow are two regions, exactly as they are on dirt (#134)")
    void twoBuildsOnSnowAreTwoRegions()
    {
        // the "snow soul is one giant room" report. Snow is a cold storage signal, cold storage is
        // enclosed-only, and the old filter seeded open-air clusters from every archetype's
        // palette regardless - so on a snowy island the ground itself was one island-spanning
        // cluster, and every build laid on it joined that one region. The builds here are
        // byte-identical between the two runs; only what they stand on differs.
        List<SoulRegion> onDirt = scanAsTheGameDoes(twoBuildsOn(TestBlocks.DIRT));
        List<SoulRegion> onSnow = scanAsTheGameDoes(twoBuildsOn(TestBlocks.SNOW_BLOCK));

        assertEquals(2, onDirt.size(), "the ground nothing names never chained anything");
        assertEquals(2, onSnow.size(), "and now neither does the ground cold storage names");

        for (SoulRegion region : onSnow)
        {
            assertEquals(RegionType.OPEN, region.type());
        }

        // a farm on snow is still a farm standing on snow: the fix stops snow chaining two builds
        // together, it does not stop a build from taking in the snow it is built on
        SoulRegion farm = onSnow.get(0).allBlocks().count(BlockMatcher.ofTags("minecraft:crops")) > 0
                ? onSnow.get(0) : onSnow.get(1);
        SoulRegion yard = farm == onSnow.get(0) ? onSnow.get(1) : onSnow.get(0);

        assertEquals(16, countIn(farm, "minecraft:crops"));
        assertEquals(0, countIn(farm, "minecraft:fences"), "the yard is not part of the farm");
        assertTrue(farm.allBlocks().count(BlockMatcher.ofBlocks("minecraft:snow_block")) > 0,
                "the snow under the farm is in the farm's contents");
        assertEquals(32, countIn(yard, "minecraft:fences"));
        assertEquals(0, countIn(yard, "minecraft:crops"), "nor the farm part of the yard");
    }

    @Test
    @DisplayName("the old all-archetypes filter is what made a snowy island one region")
    void theCountingFilterSeedsTheWholeIsland()
    {
        // kept beside the case above so the next person can see the two filters are not
        // interchangeable: cluster on everything the classifier can count and the snow chains
        // both builds into one region that then scores as neither
        List<SoulRegion> regions = RegionScanner.scan(
                twoBuildsOn(TestBlocks.SNOW_BLOCK), ArchetypeSignals.filterFor(shipped()), ScanSettings.DEFAULTS);

        assertEquals(1, regions.size(), "seeded on snow, the ground is one cluster");
        assertEquals(16, countIn(regions.get(0), "minecraft:crops"));
        assertEquals(32, countIn(regions.get(0), "minecraft:fences"));
    }

    @Test
    @DisplayName("a treed starter island with nothing built on it produces no open-air region (#135)")
    void aTreedIslandIsNotARegion()
    {
        // leaves are a greenhouse signal, greenhouse is enclosed-only, and a full-cube leaf block
        // used to seed a cluster that grew up the trunks, along the canopy, and then took in
        // everything the canopy closed around: 169 cells of ground became a 1014-cell region
        // reaching canopy height, and the advice was "cut the trees down before you build"
        List<SoulRegion> regions = scanAsTheGameDoes(treedIsland(false, false));

        assertTrue(regions.isEmpty(), "nothing on a bare treed island seeds anything: " + regions);
    }

    @Test
    @DisplayName("a build on a treed island is its own region, not merged with the trees (#135)")
    void aBuildOnATreedIslandIsItsOwnRegion()
    {
        List<SoulRegion> regions = scanAsTheGameDoes(treedIsland(true, false));

        assertEquals(1, regions.size(), "the farm, and nothing else: " + regions);

        SoulRegion farm = regions.get(0);
        assertEquals(RegionType.OPEN, farm.type());
        assertEquals(9, countIn(farm, "minecraft:crops"));
        assertEquals(0, countIn(farm, "minecraft:leaves"), "the canopy is not part of the farm");
        assertEquals(0, farm.allBlocks().count(BlockMatcher.ofTags("minecraft:logs")), "nor the trunks");
        assertTrue(farm.bounds().maxY() <= 2, "the farm's box stays at ground level: " + farm.bounds());
    }

    @Test
    @DisplayName("a sealed room on a treed island survives the trees intact (#135)")
    void aSealedRoomOnATreedIslandIsUnaffected()
    {
        // the fault was always confined to the open-air pass: the library came back the same with
        // and without the trees, and this pins that
        List<SoulRegion> regions = scanAsTheGameDoes(treedIsland(false, true));

        assertEquals(1, regions.size(), "the library, and nothing else: " + regions);
        assertEquals(RegionType.ENCLOSED, regions.get(0).type());
        assertEquals(3, countIn(regions.get(0), "soulhome:bookshelves"));
    }

    @Test
    @DisplayName("logs are not a cluster seed, and this is the test that notices if someone tags them")
    void logsDoNotSeedClusters()
    {
        // recorded rather than assumed (#135): a tag that pulled logs into an open archetype's
        // palette would put every tree trunk on a starter island back in the open-air pass
        assertFalse(ArchetypeSignals.openClusterFilterFor(shipped()).test(TestBlocks.OAK_LOG));
    }

    /**
     * A 15x15 island with two builds ten blocks apart: a 4x4 farm in one corner and a fenced yard,
     * two rails high, in the other. Everything either build is made of is an open archetype's
     * signal; the ground is whichever block the caller names.
     */
    private static GridVolume twoBuildsOn(TestBlocks.TestBlock ground)
    {
        String[] groundLayer = new String[15];
        Arrays.fill(groundLayer, "GGGGGGGGGGGGGGG");

        // the farm's farmland is sunk into the ground layer above, as a player would till it
        String[] tilled = new String[15];
        Arrays.fill(tilled, "GGGGGGGGGGGGGGG");

        for (int z = 0; z < 4; z++)
        {
            tilled[z] = "ffff" + "G".repeat(11);
        }

        String[] surface = new String[15];
        Arrays.fill(surface, "...............");

        for (int z = 0; z < 4; z++)
        {
            surface[z] = "wwww" + ".".repeat(11);
        }

        String[] yard = new String[15];
        Arrays.fill(yard, "...............");

        yard[9] = ".".repeat(10) + "FFFFF";
        yard[10] = ".".repeat(10) + "F...F";
        yard[11] = ".".repeat(10) + "F...F";
        yard[12] = ".".repeat(10) + "F...F";
        yard[13] = ".".repeat(10) + "FFFFF";

        // the fences stand on the ground, alongside the wheat; a second course above them
        String[] surfaceWithYard = surface.clone();

        for (int z = 9; z <= 13; z++)
        {
            surfaceWithYard[z] = yard[z];
        }

        return GridVolume.of(Map.of('G', ground), groundLayer, tilled, surfaceWithYard, yard);
    }

    /**
     * A 13x13 snowy island with a tree in each corner - a three-block trunk under a 3x3 canopy,
     * the shape of a vanilla oak - and optionally a 3x3 farm or a sealed stone library in the
     * middle.
     */
    private static GridVolume treedIsland(boolean withFarm, boolean withLibrary)
    {
        final int size = 13;
        String[] ground = new String[size];
        Arrays.fill(ground, "S".repeat(size));

        // y=1..3 trunks, y=3..5 canopy
        String[][] layers = new String[7][size];

        for (int y = 0; y < 7; y++)
        {
            Arrays.fill(layers[y], ".".repeat(size));
        }

        int[][] trunks = {{1, 1}, {1, 11}, {11, 1}, {11, 11}};

        for (int[] trunk : trunks)
        {
            for (int y = 1; y <= 3; y++)
            {
                layers[y][trunk[1]] = replaceAt(layers[y][trunk[1]], trunk[0], 'T');
            }

            for (int y = 3; y <= 5; y++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    for (int dx = -1; dx <= 1; dx++)
                    {
                        final boolean centre = dx == 0 && dz == 0;

                        if (centre && y == 3)
                        {
                            continue; // the trunk's top
                        }

                        layers[y][trunk[1] + dz] = replaceAt(layers[y][trunk[1] + dz], trunk[0] + dx, 'z');
                    }
                }
            }
        }

        if (withFarm)
        {
            for (int z = 5; z <= 7; z++)
            {
                ground[z] = ground[z].substring(0, 5) + "fff" + ground[z].substring(8);
                layers[1][z] = layers[1][z].substring(0, 5) + "www" + layers[1][z].substring(8);
            }
        }

        if (withLibrary)
        {
            // a 5x5 stone box, one block of air high inside, with three bookshelves on one wall
            for (int z = 4; z <= 8; z++)
            {
                layers[1][z] = layers[1][z].substring(0, 4) + (z == 4 ? "#BBB#" : z == 8 ? "#####" : "#...#")
                        + layers[1][z].substring(9);
                layers[2][z] = layers[2][z].substring(0, 4) + "#####" + layers[2][z].substring(9);
            }
        }

        // layers[0] is the ground's own height and holds nothing, so the ground row takes its place
        String[][] stacked = new String[7][];
        stacked[0] = ground;
        System.arraycopy(layers, 1, stacked, 1, 6);

        return GridVolume.of(
                Map.of('S', TestBlocks.SNOW_BLOCK, 'T', TestBlocks.OAK_LOG),
                stacked);
    }

    private static String replaceAt(String row, int x, char symbol)
    {
        return row.substring(0, x) + symbol + row.substring(x + 1);
    }

    // endregion

    // region open air

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
                "############",
                "############",
                "############",
                "############",
                "############"};

        GridVolume volume = GridVolume.of(
                slab,
                new String[]{
                        "############",
                        "#...##.....#",
                        "#...##.....#",
                        "#...##.....#",
                        "############"},
                slab);

        List<SoulRegion> both = RegionScanner.scan(volume, null, ScanSettings.DEFAULTS);
        assertEquals(2, both.size());
        assertEquals(15, both.get(0).volume(), "richest first");
        assertEquals(9, both.get(1).volume());

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
                signature.hasTag("soulhome:bookshelves") || signature.id().equals("minecraft:lectern");

        SoulRegion room = scanWithGeometry(furnishedRoom(), anyFurniture).get(0);

        BlockMatcher shelves = BlockMatcher.ofTags("soulhome:bookshelves");
        assertEquals(room.allBlocks().count(shelves), room.geometry().cellsMatching(shelves).size());

        BlockMatcher lectern = BlockMatcher.ofBlocks("minecraft:lectern");
        assertEquals(room.allBlocks().count(lectern), room.geometry().cellsMatching(lectern).size());
    }

    @Test
    @DisplayName("geometry is deterministic across two scans of the same volume")
    void geometryIsDeterministic()
    {
        Predicate<BlockSignature> anyFurniture = signature ->
                signature.hasTag("soulhome:bookshelves") || signature.id().equals("minecraft:lectern");

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
        Predicate<BlockSignature> anyBookshelf = signature -> signature.hasTag("soulhome:bookshelves");

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

    // region clearance (#29)

    @Test
    @DisplayName("an enclosed region's geometry carries its own bounds")
    void enclosedRegionGeometryCarriesBounds()
    {
        SoulRegion room = scanWithGeometry(furnishedRoom(), LECTERNS_ONLY).get(0);

        assertEquals(room.bounds(), room.geometry().bounds().orElseThrow());
    }

    @Test
    @DisplayName("an open-air region's geometry carries its own bounds too")
    void openRegionGeometryCarriesBounds()
    {
        GridVolume field = GridVolume.of(
                new String[]{"fffff", "fffff", "fffff", "fffff", "fffff"},
                new String[]{"wwwww", "wwwww", "wwwww", "wwwww", "wwwww"});

        List<SoulRegion> regions = RegionScanner.scan(field, ANY_CROP, LECTERNS_ONLY, ScanSettings.DEFAULTS);
        assertEquals(1, regions.size());

        SoulRegion cluster = regions.get(0);
        assertEquals(cluster.bounds(), cluster.geometry().bounds().orElseThrow());
    }

    @Test
    @DisplayName("without indexClearance, nothing is recorded as blocked even where a wall plainly is")
    void withoutIndexClearanceNothingIsRecordedBlocked()
    {
        SoulRegion room = RegionScanner.scan(furnishedRoom(), ANY_CROP, LECTERNS_ONLY, false, ScanSettings.DEFAULTS).get(0);

        // (0,1,1) is the left wall of furnishedRoom()'s middle row - definitely BLOCKING - yet
        // unrecorded, since clearance tracking was never asked for
        assertFalse(room.geometry().isBlocked(0, 1, 1));
    }

    @Test
    @DisplayName("with indexClearance, wall and furniture cells the scanner visits are recorded as blocked")
    void withIndexClearanceWallsAreRecordedBlocked()
    {
        SoulRegion room = RegionScanner.scan(furnishedRoom(), ANY_CROP, LECTERNS_ONLY, true, ScanSettings.DEFAULTS).get(0);

        // the lectern itself (indexed furniture, standing at x=1,z=2) and the room's left wall
        // (shell, at x=0) are both BLOCKING cells the scanner visits while building this room
        assertTrue(room.geometry().isBlocked(1, 1, 2), "the lectern");
        assertTrue(room.geometry().isBlocked(0, 1, 1), "the left wall");
        assertFalse(room.geometry().isBlocked(2, 1, 2), "open floor space has nothing blocked");
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

    private static String[] hugeFloor()
    {
        String[] rows = new String[9];
        Arrays.fill(rows, "#########");
        return rows;
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
