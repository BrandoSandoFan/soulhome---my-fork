/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a brand-new soul reports - #139, the regression corpus for the region detection epic.
 *
 * <p>Every case runs against all three shipped starter islands, read from the templates the game
 * places (see {@link SoulIslandVolume}), with the real shipped archetypes and the real filters
 * {@code ArchetypeManager} builds from them. The assertions are properties a fresh soul must
 * have rather than exact numbers, so the corpus does not become a change-detector nobody trusts;
 * the one number in here, the region cap, is a loose upper bound so that a change which
 * fragments the terrain is noticed.
 *
 * <p>Two of these - the "no large region" and "two builds are two regions" cases - are the
 * assertions that would have caught #134 and #135 on the day the islands shipped.
 */
class SoulIslandCorpusTest
{
    /**
     * An untouched island's regions must each cover less than this share of its footprint. The
     * fault this guards against covered the whole of it.
     */
    private static final double MAX_REGION_FOOTPRINT_SHARE = 0.10d;

    /**
     * Loose upper bound on regions per untouched island. Today's islands produce a handful at
     * most; a change that fragments the terrain into many small regions trips this.
     */
    private static final int MAX_REGIONS_PER_ISLAND = 6;

    private static List<ArchetypeDefinition> shipped;
    private static ArchetypeClassifier classifier;
    private static Predicate<BlockSignature> clusterFilter;
    private static Predicate<BlockSignature> geometryFilter;
    private static boolean needsClearance;

    @BeforeAll
    static void loadShippedArchetypes() throws IOException
    {
        shipped = ArchetypeJsonReader.shipped();
        classifier = new ArchetypeClassifier(shipped);
        clusterFilter = ArchetypeSignals.openClusterFilterFor(shipped);
        geometryFilter = ArchetypeSignals.geometryFilterFor(shipped);
        needsClearance = ArchetypeSignals.needsClearance(shipped);
    }

    /** Exactly what {@code StructureScanService} does with a snapshot. */
    private static List<SoulRegion> scan(SoulIslandVolume island)
    {
        return RegionScanner.scan(island, clusterFilter, geometryFilter, needsClearance, ScanSettings.DEFAULTS);
    }

    @Test
    @DisplayName("an untouched island produces no classified rooms - a soul grants nothing before you build")
    void anUntouchedIslandGrantsNothing()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            for (ClassificationResult result : classifier.classify(scan(island)))
            {
                assertEquals(ClassificationResult.Status.UNCLASSIFIED, result.status(),
                        island + " awards " + result.best().archetypeId() + " to " + result.region()
                                + " before anything has been built on it");
            }
        }
    }

    @Test
    @DisplayName("no region on an untouched island covers more than a small fraction of it")
    void anUntouchedIslandHasNoIslandSpanningRegion()
    {
        // the assertion that would have caught #134 and #135: the snowy island came back as one
        // open-air region the size of the island, seeded on its own snow and leaves
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            for (SoulRegion region : scan(island))
            {
                final double share = (double) footprintOf(region) / island.footprintArea();

                assertTrue(share < MAX_REGION_FOOTPRINT_SHARE,
                        island + " has a region covering " + (int) (share * 100) + "% of its footprint: " + region
                                + " " + region.allBlocks());
            }
        }
    }

    @Test
    @DisplayName("an untouched island yields at most a handful of regions")
    void anUntouchedIslandIsNotFragmented()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            List<SoulRegion> regions = scan(island);

            assertTrue(regions.size() <= MAX_REGIONS_PER_ISLAND,
                    island + " fragments into " + regions.size() + " regions: " + regions);
        }
    }

    @Test
    @DisplayName("two small open-air builds a modest distance apart are two regions on every island")
    void twoBuildsAreTwoRegions()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            // whatever the island reads as on its own - the snowy island has a small campsite of
            // fences and a campfire that is, fairly, a region - is the baseline the builds add to
            final int before = openRegionsIn(scan(island)).size();

            final int centreX = island.sizeX() / 2;
            final int centreZ = island.sizeZ() / 2;

            // a farm one side of the spawn column and a fenced yard the other, ten blocks apart,
            // each dropped onto whatever ground is under it
            placeOnGround(island, farm(), centreX - 8, centreZ - 2);
            placeOnGround(island, fencedYard(), centreX + 4, centreZ - 2);

            List<SoulRegion> open = openRegionsIn(scan(island));

            assertEquals(before + 2, open.size(),
                    island + " should hold the farm and the yard as two new open regions: " + open);

            SoulRegion farm = null;
            SoulRegion yard = null;

            for (SoulRegion region : open)
            {
                if (countIn(region, "minecraft:crops") > 0)
                {
                    assertEquals(null, farm, island + ": the farm reads as more than one region");
                    farm = region;
                }

                if (countIn(region, "minecraft:fences") >= 16)
                {
                    assertEquals(null, yard, island + ": the yard reads as more than one region");
                    yard = region;
                }
            }

            assertTrue(farm != null && yard != null, island + ": both builds should be found - " + open);
            assertTrue(farm != yard, island + ": the farm and the yard read as one region - " + farm);
            assertEquals(9, countIn(farm, "minecraft:crops"), island + ": the farm");
            assertEquals(0, countIn(farm, "minecraft:fences"), island + ": the yard is not part of the farm");
            assertEquals(16, countIn(yard, "minecraft:fences"), island + ": the yard");
            assertEquals(0, countIn(yard, "minecraft:crops"), island + ": the farm is not part of the yard");
        }
    }

    @Test
    @DisplayName("a small sealed room built on an island is one enclosed region, and classifies as what it was built as")
    void aSealedRoomClassifiesOnEveryIsland()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            placeOnGround(island, library(), island.sizeX() / 2 - 3, island.sizeZ() / 2 - 3);

            List<ClassificationResult> results = classifier.classify(scan(island));
            List<ClassificationResult> enclosed = new ArrayList<>();

            for (ClassificationResult result : results)
            {
                if (result.region().type() == RegionType.ENCLOSED)
                {
                    enclosed.add(result);
                }
                else
                {
                    assertEquals(ClassificationResult.Status.UNCLASSIFIED, result.status(),
                            island + " awards something to the ground around the library: " + result.region());
                }
            }

            assertEquals(1, enclosed.size(), island + " should hold exactly one room: " + results.stream()
                    .map(ClassificationResult::region).toList());
            assertEquals("soulhome:library", enclosed.get(0).awardedArchetypeId().orElse("nothing"),
                    island + ": the library should classify as a library - " + enclosed.get(0).best());
        }
    }

    @Test
    @DisplayName("scanning the same island twice yields identical identity hashes in identical order")
    void regionOutputIsStable()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            placeOnGround(island, library(), island.sizeX() / 2 - 3, island.sizeZ() / 2 - 3);
            placeOnGround(island, farm(), island.sizeX() / 2 - 12, island.sizeZ() / 2 - 2);

            List<Long> first = hashesOf(scan(island));
            List<Long> second = hashesOf(scan(island));

            assertEquals(first, second, island + ": an untouched build must hash the same twice");
        }
    }

    // region helpers

    private static List<SoulRegion> openRegionsIn(List<SoulRegion> regions)
    {
        List<SoulRegion> open = new ArrayList<>();

        for (SoulRegion region : regions)
        {
            if (region.type() == RegionType.OPEN)
            {
                open.add(region);
            }
        }

        return open;
    }

    private static int countIn(SoulRegion region, String tag)
    {
        return region.allBlocks().count(BlockMatcher.ofTags(tag));
    }

    private static int footprintOf(SoulRegion region)
    {
        return region.bounds().sizeX() * region.bounds().sizeZ();
    }

    private static List<Long> hashesOf(List<SoulRegion> regions)
    {
        List<Long> hashes = new ArrayList<>(regions.size());

        for (SoulRegion region : regions)
        {
            hashes.add(region.identityHash());
        }

        return hashes;
    }

    /**
     * Drop a layout onto the island so that its bottom layer replaces the ground's top layer at
     * the layout's far corner - the highest ground under the whole footprint, so nothing of the
     * island pokes up through the build.
     */
    private static void placeOnGround(SoulIslandVolume island, GridVolume build, int originX, int originZ)
    {
        final RegionBounds box = build.bounds();
        int ground = Integer.MIN_VALUE;

        for (int x = box.minX() + 1; x < box.maxX(); x++)
        {
            for (int z = box.minZ() + 1; z < box.maxZ(); z++)
            {
                ground = Math.max(ground, island.highestSolidY(originX + x, originZ + z));
            }
        }

        assertTrue(ground != Integer.MIN_VALUE, island + " has no ground under (" + originX + ", " + originZ + ")");

        island.place(build, originX, ground, originZ);
    }

    /** A 3x3 wheat field: tilled ground with wheat on it. */
    private static GridVolume farm()
    {
        return GridVolume.of(
                new String[]{"fff", "fff", "fff"},
                new String[]{"www", "www", "www"},
                new String[]{"...", "...", "..."});
    }

    /** A 5x5 fenced yard, one fence high, on a dirt pad. */
    private static GridVolume fencedYard()
    {
        return GridVolume.of(
                java.util.Map.of('G', TestBlocks.DIRT),
                new String[]{"GGGGG", "GGGGG", "GGGGG", "GGGGG", "GGGGG"},
                new String[]{"FFFFF", "F...F", "F...F", "F...F", "FFFFF"},
                new String[]{".....", ".....", ".....", ".....", "....."});
    }

    /** The classifier test's canonical library: a 7x7x5 shell with shelves, seating, a lectern and candles. */
    private static GridVolume library()
    {
        String[] slab = {"#######", "#######", "#######", "#######", "#######", "#######", "#######"};

        return GridVolume.of(
                slab,
                new String[]{
                        "#BBBBB#",
                        "#S...S#",
                        "#.....#",
                        "#..L..#",
                        "#.....#",
                        "#S...S#",
                        "#BBBBB#"},
                new String[]{
                        "#BBBBB#",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#BBBBB#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                slab);
    }

    // endregion
}
