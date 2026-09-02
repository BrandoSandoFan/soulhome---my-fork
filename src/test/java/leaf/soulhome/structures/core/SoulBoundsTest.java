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
    @DisplayName("every rank grows the ceiling and the verge, and the floor never moves")
    void everyRankGrowsTheBox()
    {
        SoulBounds previous = SoulBounds.forRank(0);

        for (int rank = 1; rank <= SoulBounds.MAX_RANK; rank++)
        {
            SoulBounds current = SoulBounds.forRank(rank);

            assertEquals(previous.floorY(), current.floorY(), "the floor moved at rank " + rank);
            assertTrue(current.ceilingY() > previous.ceilingY(), "the ceiling did not grow at rank " + rank);
            assertTrue(current.vergeHalfExtent() > previous.vergeHalfExtent(), "the verge did not grow at rank " + rank);

            previous = current;
        }
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
    @DisplayName("rank V's box fits under maxScannedCells and inside the search square's +-128 reach")
    void rankVFitsWithinScanLimits()
    {
        SoulBounds rankV = SoulBounds.forRank(SoulBounds.MAX_RANK);

        // SnapshotBlockVolume.SEARCH_CHUNK_RADIUS = 8 covers chunk-aligned X/Z from -128 to 143;
        // this mirrors that number rather than importing it, since structures/core stays
        // Minecraft-free and SnapshotBlockVolume is on the other side of that line. A future
        // rebalance of either constant has to keep this passing, not just compiling.
        final int searchSquareReach = 128;

        assertTrue(rankV.vergeHalfExtent() <= searchSquareReach,
                "verge half-extent " + rankV.vergeHalfExtent() + " exceeds the +-" + searchSquareReach + " search square");

        long footprint = (long) (2 * rankV.vergeHalfExtent() + 1) * (2 * rankV.vergeHalfExtent() + 1);
        long cells = footprint * rankV.buildLayers();

        assertTrue(cells <= ScanSettings.DEFAULTS.maxScannedCells(),
                "rank V's box is " + cells + " cells, above maxScannedCells " + ScanSettings.DEFAULTS.maxScannedCells());
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
                SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK);

        for (int rank = 1; rank <= maxRank; rank++)
        {
            SoulBounds current = SoulBounds.forRank(rank, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                    SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                    SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK);

            assertTrue(current.ceilingY() > previous.ceilingY(), "rank " + rank + " did not grow the ceiling");
            previous = current;
        }

        // a rank past the configured max is clamped to it, not to the shipped MAX_RANK of 5
        SoulBounds atMax = SoulBounds.forRank(maxRank, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK);
        SoulBounds pastMax = SoulBounds.forRank(maxRank + 50, maxRank, SoulBounds.DEFAULT_FLOOR_Y,
                SoulBounds.DEFAULT_BASE_CEILING_HEIGHT, SoulBounds.DEFAULT_CEILING_HEIGHT_PER_RANK,
                SoulBounds.DEFAULT_BASE_VERGE, SoulBounds.DEFAULT_VERGE_PER_RANK);

        assertEquals(atMax, pastMax);
        assertTrue(atMax.ceilingY() < SoulBounds.forRank(SoulBounds.MAX_RANK).ceilingY(),
                "a three-rung ladder should not reach as high as the shipped five-rung one");
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
