/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformClauseTest
{
    private static final BlockMatcher SLAB = BlockMatcher.ofBlocks("test:slab");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("floor", SLAB);

    private static final PlatformClauseType TYPE = new PlatformClauseType();

    private static FormClause platform(int minArea, int idealArea, int heightTolerance)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "floor").put("min_area", minArea).put("ideal_area", idealArea)
                .put("height_tolerance", heightTolerance)
                .build());
    }

    private static BlockSignature slab()
    {
        return new TestBlocks.TestBlock("test:slab", Set.of(), Passability.PASSABLE);
    }

    @Test
    @DisplayName("a 5x5 slab at ideal_area 25 scores 1.0")
    void fullSlabScoresFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 5; x++)
        {
            for (int z = 0; z < 5; z++)
            {
                builder.add(x, 0, z, slab());
            }
        }

        assertEquals(1.0, platform(4, 25, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("25 scattered singles score 0 - none of them touch, so the largest patch is 1")
    void scatteredSinglesScoreZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int x = 0; x < 5; x++)
        {
            for (int z = 0; z < 5; z++)
            {
                builder.add(x * 3, 0, z * 3, slab());
            }
        }

        assertEquals(0.0, platform(4, 25, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("two separate 3x3 patches score as one 3x3 (9), not as 18 - largest contiguous, not total")
    void twoSeparatePatchesScoreAsOnePatch()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 3; x++)
        {
            for (int z = 0; z < 3; z++)
            {
                builder.add(x, 0, z, slab());
                builder.add(x + 10, 0, z, slab());
            }
        }

        // ideal_area 20: if the two patches were wrongly summed (18), confidence would read 0.9;
        // correctly reading only the largest single patch (9) reads 0.45
        assertEquals(9.0 / 20.0, platform(4, 20, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a surface stepping down one block at a time stays contiguous at height_tolerance 1")
    void steppingDownOneBlockStaysContiguous()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 5; x++)
        {
            builder.add(x, x, 0, slab());
        }

        assertEquals(1.0, platform(4, 5, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("stepping down three in one place breaks contiguity at height_tolerance 1")
    void steppingDownThreeBreaksContiguityAtToleranceOne()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        int[] heights = {0, 1, 2, 3, 6};

        for (int x = 0; x < heights.length; x++)
        {
            builder.add(x, heights[x], 0, slab());
        }

        RegionGeometry geometry = builder.build();

        // broken into a run of 4 and a lone cell at tolerance 1
        assertEquals(4.0 / 5.0, platform(1, 5, 1).evaluate(geometry, ELEMENTS).confidence(), 1e-9);

        // the same build stays fully contiguous once the tolerance actually covers the 3-block step
        assertEquals(1.0, platform(1, 5, 3).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("two blocks that only touch corner-to-corner are not contiguous - connectivity is 4-way, not 8-way")
    void diagonalContactDoesNotConnect()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        builder.add(0, 0, 0, slab());
        builder.add(1, 0, 1, slab());

        // if diagonals counted, the largest patch would be 2 and this would score 1.0
        assertEquals(0.0, platform(2, 2, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("36 blocks in a checkerboard score 0 - fully diagonally connected is still scattered")
    void checkerboardScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int x = 0; x < 12; x++)
        {
            for (int z = 0; z < 6; z++)
            {
                if ((x + z) % 2 == 0)
                {
                    builder.add(x, 0, z, slab());
                }
            }
        }

        assertEquals(0.0, platform(9, 36, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a 36-block diagonal staircase climbing three storeys scores 0, not 1.0 as a flawless floor")
    void diagonalStaircaseScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int i = 0; i < 36; i++)
        {
            builder.add(i, i, i, slab());
        }

        assertEquals(0.0, platform(9, 36, 40).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a 9x9 farm split by a water trench still clears the farm's own ideal_area on either side")
    void trenchedFarmStillReadsAsAField()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 9; x++)
        {
            for (int z = 0; z < 9; z++)
            {
                if (x == 4)
                {
                    continue;
                }

                builder.add(x, 0, z, slab());
            }
        }

        // the trench at x=4 severs the two 4x9 halves under 4-connectivity, but each half is 36
        // cells - farm.json's own ideal_area - so the field still reads as a full field
        assertEquals(1.0, platform(9, 36, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }
}
