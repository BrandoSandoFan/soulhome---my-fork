/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulBoundsTest
{
    @Test
    @DisplayName("guest passage is reachable on the shipped ladder, and near the top of it rather than halfway")
    void guestRankIsReachableAndHigh()
    {
        assertTrue(SoulBounds.DEFAULT_GUEST_RANK_REQUIRED <= SoulBounds.MAX_RANK);
        assertTrue(SoulBounds.DEFAULT_GUEST_RANK_REQUIRED * 2 > SoulBounds.MAX_RANK);
    }

    @Test
    @DisplayName("rank 0 is one storey: a floor, four of air, a ceiling")
    void rankZeroIsOneStorey()
    {
        SoulBounds bounds = SoulBounds.forRank(0);

        assertEquals(SoulBounds.DEFAULT_FLOOR_Y, bounds.floorY());
        assertEquals(SoulBounds.DEFAULT_FLOOR_Y + 6, bounds.ceilingY());
        assertEquals(6, bounds.buildLayers());
        assertEquals(24, bounds.vergeHalfExtent());
    }

    @Test
    @DisplayName("the floor's own layer is buildable, one below the ceiling is buildable, the ceiling itself is not")
    void floorInclusiveCeilingExclusive()
    {
        SoulBounds bounds = SoulBounds.forRank(0);

        assertTrue(bounds.contains(0, bounds.floorY(), 0));
        assertTrue(bounds.contains(0, bounds.ceilingY() - 1, 0));
        assertFalse(bounds.contains(0, bounds.ceilingY(), 0));
        assertFalse(bounds.contains(0, bounds.floorY() - 1, 0));
    }

    @Test
    @DisplayName("the verge is inclusive on both walls, exclusive one block beyond")
    void vergeInclusiveAtItsEdge()
    {
        SoulBounds bounds = SoulBounds.forRank(0);
        int edge = bounds.vergeHalfExtent();

        assertTrue(bounds.contains(edge, bounds.floorY(), 0));
        assertTrue(bounds.contains(-edge, bounds.floorY(), 0));
        assertFalse(bounds.contains(edge + 1, bounds.floorY(), 0));
        assertFalse(bounds.contains(-edge - 1, bounds.floorY(), 0));
        assertTrue(bounds.contains(0, bounds.floorY(), edge));
        assertFalse(bounds.contains(0, bounds.floorY(), edge + 1));
    }

    @Test
    @DisplayName("every rank grows the ceiling, only III, VI and IX grow the verge, and the floor never moves")
    void everyRankGrowsTheCeilingAndEveryThirdTheVerge()
    {
        SoulBounds previous = SoulBounds.forRank(0);

        for (int rank = 1; rank <= SoulBounds.MAX_RANK; rank++)
        {
            SoulBounds current = SoulBounds.forRank(rank);

            assertEquals(previous.floorY(), current.floorY(), "the floor moved at rank " + rank);
            assertTrue(current.ceilingY() > previous.ceilingY(), "the ceiling did not grow at rank " + rank);

            if (rank % 3 == 0)
            {
                assertTrue(current.vergeHalfExtent() > previous.vergeHalfExtent(),
                        "the verge did not grow at rank " + rank);
            }
            else
            {
                assertEquals(previous.vergeHalfExtent(), current.vergeHalfExtent(),
                        "the verge grew at rank " + rank + ", which is not an outward rank");
            }

            previous = current;
        }
    }

    @Test
    @DisplayName("an outward rank stands exactly as wide as it did when every rank widened the soul")
    void outwardRanksKeepTheirOldWidth()
    {
        for (int rank : new int[] {0, 3, 6, 9})
        {
            assertEquals(SoulBounds.DEFAULT_BASE_VERGE + rank * SoulBounds.DEFAULT_VERGE_PER_RANK,
                    SoulBounds.forRank(rank).vergeHalfExtent(), "rank " + rank);
        }

        assertEquals(SoulBounds.forRank(0).vergeHalfExtent(), SoulBounds.forRank(2).vergeHalfExtent());
        assertEquals(SoulBounds.forRank(3).vergeHalfExtent(), SoulBounds.forRank(5).vergeHalfExtent());
        assertEquals(SoulBounds.forRank(6).vergeHalfExtent(), SoulBounds.forRank(8).vergeHalfExtent());
    }

    @Test
    @DisplayName("an outward step of one widens the soul at every rank, as it did before steps existed")
    void stepOfOneWidensEveryRank()
    {
        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            assertEquals(rank, SoulBounds.outwardRank(rank, SoulBounds.MAX_RANK, 1));

            SoulBounds bounds = SoulBounds.forRank(rank, SoulBounds.MAX_RANK, SoulBounds.DEFAULT_FLOOR_Y,
                    SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                    SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK, 1);

            assertEquals(SoulBounds.DEFAULT_BASE_VERGE + rank * SoulBounds.DEFAULT_VERGE_PER_RANK,
                    bounds.vergeHalfExtent(), "rank " + rank);
        }

        // and a step below one is read as one rather than dividing by zero
        assertEquals(4, SoulBounds.outwardRank(4, SoulBounds.MAX_RANK, 0));
    }

    @Test
    @DisplayName("the top of a ladder that is not a multiple of the step still widens the soul")
    void topRankAlwaysWidens()
    {
        assertEquals(9, SoulBounds.outwardRank(10, 10, 3));
        assertEquals(10, SoulBounds.outwardRank(10, 10, 3));
        assertEquals(3, SoulBounds.outwardRank(4, 5, 3));
        assertEquals(5, SoulBounds.outwardRank(5, 5, 3));
        assertEquals(5, SoulBounds.outwardRank(50, 5, 3), "a rank past the top is clamped to it first");
        assertEquals(0, SoulBounds.outwardRank(-2, 9, 3));
    }

    @Test
    @DisplayName("the next outward rank is the next rank that widens the soul, and -1 at the top")
    void nextOutwardRankNamesTheNextWidening()
    {
        assertEquals(3, SoulBounds.nextOutwardRank(0, 9, 3));
        assertEquals(3, SoulBounds.nextOutwardRank(2, 9, 3));
        assertEquals(6, SoulBounds.nextOutwardRank(3, 9, 3));
        assertEquals(9, SoulBounds.nextOutwardRank(8, 9, 3));
        assertEquals(-1, SoulBounds.nextOutwardRank(9, 9, 3));
        assertEquals(10, SoulBounds.nextOutwardRank(9, 10, 3));
        assertEquals(5, SoulBounds.nextOutwardRank(4, 5, 3));
        assertEquals(1, SoulBounds.nextOutwardRank(0, 9, 1));
    }

    @Test
    @DisplayName("a rank past V is clamped to V rather than growing further or throwing")
    void rankAboveMaxIsClamped()
    {
        assertEquals(SoulBounds.forRank(SoulBounds.MAX_RANK), SoulBounds.forRank(SoulBounds.MAX_RANK + 50));
    }

    @Test
    @DisplayName("a negative rank is clamped to 0 rather than shrinking the box further or throwing")
    void negativeRankIsClampedToZero()
    {
        assertEquals(SoulBounds.forRank(0), SoulBounds.forRank(-3));
    }

    @Test
    @DisplayName("the top rank's box fits under maxScannedCells and inside the search square's reach")
    void topRankFitsWithinScanLimits()
    {
        SoulBounds topRank = SoulBounds.forRank(SoulBounds.MAX_RANK);

        // SnapshotBlockVolume.SEARCH_CHUNK_RADIUS = 12 covers chunk-aligned X/Z out to 12 * 16 = 192;
        // this mirrors that number rather than importing it, since structures/core stays
        // Minecraft-free and SnapshotBlockVolume is on the other side of that line. A future
        // rebalance of either constant has to keep this passing, not just compiling.
        final int searchSquareReach = 192;

        assertTrue(topRank.vergeHalfExtent() <= searchSquareReach,
                "verge half-extent " + topRank.vergeHalfExtent() + " exceeds the +-" + searchSquareReach + " search square");

        long footprint = (long) (2 * topRank.vergeHalfExtent() + 1) * (2 * topRank.vergeHalfExtent() + 1);
        long cells = footprint * topRank.buildLayers();

        assertTrue(cells <= ScanSettings.DEFAULTS.maxScannedCells(),
                "the top rank's box is " + cells + " cells, above maxScannedCells " + ScanSettings.DEFAULTS.maxScannedCells());
    }

    @Test
    @DisplayName("an inverted box is rejected")
    void invertedBoxRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new SoulBounds(70, 70, 24));
        assertThrows(IllegalArgumentException.class, () -> new SoulBounds(70, 69, 24));
    }

    @Test
    @DisplayName("a non-positive verge is rejected")
    void nonPositiveVergeRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new SoulBounds(70, 76, 0));
        assertThrows(IllegalArgumentException.class, () -> new SoulBounds(70, 76, -1));
    }

    @Test
    @DisplayName("max_rank = 3 produces a three-rung ladder with no dead ranks and no out-of-bounds lookup")
    void configurableMaxRankProducesAShorterLadder()
    {
        final int maxRank = 3;
        SoulBounds previous = SoulBounds.forRank(0, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK, SoulBounds.DEFAULT_OUTWARD_STEP);

        for (int rank = 1; rank <= maxRank; rank++)
        {
            SoulBounds current = SoulBounds.forRank(rank, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                    SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                    SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK, SoulBounds.DEFAULT_OUTWARD_STEP);

            assertTrue(current.ceilingY() > previous.ceilingY(), "rank " + rank + " did not grow the ceiling");
            previous = current;
        }

        // a rank past the configured max is clamped to it, not to the shipped MAX_RANK of 9
        SoulBounds atMax = SoulBounds.forRank(maxRank, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK, SoulBounds.DEFAULT_OUTWARD_STEP);
        SoulBounds pastMax = SoulBounds.forRank(maxRank + 50, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK, SoulBounds.DEFAULT_OUTWARD_STEP);

        assertEquals(atMax, pastMax);
        assertTrue(atMax.ceilingY() < SoulBounds.forRank(SoulBounds.MAX_RANK).ceilingY(),
                "a three-rung ladder should not reach as high as the shipped nine-rung one");
    }

    @Test
    @DisplayName("rank 0 reads as unascended, every other rank as a Roman numeral")
    void rankLabelIsRomanAboveZero()
    {
        assertEquals("0 (unascended)", SoulBounds.rankLabel(0));
        assertEquals("0 (unascended)", SoulBounds.rankLabel(-1));
        assertEquals("I", SoulBounds.rankLabel(1));
        assertEquals("II", SoulBounds.rankLabel(2));
        assertEquals("III", SoulBounds.rankLabel(3));
        assertEquals("IV", SoulBounds.rankLabel(4));
        assertEquals("V", SoulBounds.rankLabel(5));
        assertEquals("IX", SoulBounds.rankLabel(9));
    }

    @Test
    @DisplayName("an island floor below the datum lowers the box's own floor, but never its ceiling or verge")
    void islandFloorBelowDatumLowersOnlyTheFloor()
    {
        SoulBounds nominal = SoulBounds.forRank(0);
        SoulBounds lowered = SoulBounds.forRank(
                0, SoulBounds.MAX_RANK, SoulBounds.DEFAULT_FLOOR_Y, SoulBounds.DEFAULT_BASE_CEILING_HEIGHT,
                SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK, SoulBounds.DEFAULT_BASE_VERGE,
                SoulBounds.DEFAULT_VERGE_PER_RANK, SoulBounds.DEFAULT_OUTWARD_STEP, SoulBounds.DEFAULT_FLOOR_Y - 21);

        assertEquals(SoulBounds.DEFAULT_FLOOR_Y - 21, lowered.floorY());
        assertEquals(nominal.ceilingY(), lowered.ceilingY(), "the ceiling must stay anchored on the nominal floor");
        assertEquals(nominal.vergeHalfExtent(), lowered.vergeHalfExtent());
        assertTrue(lowered.buildLayers() > nominal.buildLayers(), "lowering the floor should only ever add buildable space");

        assertTrue(lowered.contains(0, SoulBounds.DEFAULT_FLOOR_Y - 21, 0),
                "a soul's own island ground below the datum must read as in-bounds");
    }

    @Test
    @DisplayName("an island floor above the datum never raises the box's floor")
    void islandFloorAboveDatumNeverRaisesTheFloor()
    {
        SoulBounds bounds = SoulBounds.forRank(
                0, SoulBounds.MAX_RANK, SoulBounds.DEFAULT_FLOOR_Y, SoulBounds.DEFAULT_BASE_CEILING_HEIGHT,
                SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK, SoulBounds.DEFAULT_BASE_VERGE,
                SoulBounds.DEFAULT_VERGE_PER_RANK, SoulBounds.DEFAULT_OUTWARD_STEP, SoulBounds.DEFAULT_FLOOR_Y + 10);

        assertEquals(SoulBounds.DEFAULT_FLOOR_Y, bounds.floorY());
    }

    @Test
    @DisplayName("toRegionBounds is inclusive on every face, matching the scanner's own coordinate convention")
    void toRegionBoundsIsInclusive()
    {
        SoulBounds bounds = SoulBounds.forRank(0);
        RegionBounds region = bounds.toRegionBounds();

        assertEquals(bounds.floorY(), region.minY());
        assertEquals(bounds.ceilingY() - 1, region.maxY());
        assertEquals(-bounds.vergeHalfExtent(), region.minX());
        assertEquals(bounds.vergeHalfExtent(), region.maxX());
        assertEquals(-bounds.vergeHalfExtent(), region.minZ());
        assertEquals(bounds.vergeHalfExtent(), region.maxZ());
        assertEquals(bounds.buildLayers(), region.sizeY());
    }
}
