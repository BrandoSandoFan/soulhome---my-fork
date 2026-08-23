/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnclosureClauseTest
{
    private static final BlockMatcher FENCE = BlockMatcher.ofBlocks("test:fence");
    private static final BlockMatcher ICE = BlockMatcher.ofBlocks("test:ice");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("fence", FENCE, "surface", ICE);

    private static final EnclosureClauseType TYPE = new EnclosureClauseType();

    private static FormClause enclosure(String around, double minCoverage)
    {
        return TYPE.create(ClauseParams.builder().put("of", "fence").put("around", around).put("min_coverage", minCoverage).build());
    }

    private static BlockSignature fence()
    {
        return new TestBlocks.TestBlock("test:fence", Set.of(), Passability.PASSABLE);
    }

    private static BlockSignature ice()
    {
        return new TestBlocks.TestBlock("test:ice", Set.of(), Passability.PASSABLE);
    }

    /** Full border ring of the 7x7 (x/z 0-6) bounds - no `around`, so this is the region's own extent. */
    private static RegionGeometry.Builder fullRing()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200).bounds(new RegionBounds(0, 0, 0, 6, 0, 6));

        for (int x = 0; x <= 6; x++)
        {
            for (int z = 0; z <= 6; z++)
            {
                if (x == 0 || x == 6 || z == 0 || z == 6)
                {
                    builder.add(x, 0, z, fence());
                }
            }
        }

        return builder;
    }

    @Test
    @DisplayName("a full fence ring scores ~1.0")
    void fullRingScoresFull()
    {
        assertEquals(1.0, enclosure("", 0.5).evaluate(fullRing().build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a ring with a one-block gate still scores near 1.0 - the case this design exists to get right")
    void ringWithGateStillScoresNearFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200).bounds(new RegionBounds(0, 0, 0, 6, 0, 6));

        for (int x = 0; x <= 6; x++)
        {
            for (int z = 0; z <= 6; z++)
            {
                if ((x == 0 || x == 6 || z == 0 || z == 6) && !(x == 3 && z == 0))
                {
                    builder.add(x, 0, z, fence());
                }
            }
        }

        assertTrue(enclosure("", 0.5).evaluate(builder.build(), ELEMENTS).confidence() > 0.9);
    }

    @Test
    @DisplayName("a fence missing one whole side scores roughly half enclosed, well between full and none")
    void missingOneWholeSideScoresRoughlyHalf()
    {
        // an elongated room, 20 wide x 3 deep, missing its entire long top edge
        RegionGeometry.Builder builder = RegionGeometry.builder(200).bounds(new RegionBounds(0, 0, 0, 19, 0, 2));

        for (int x = 0; x <= 19; x++)
        {
            for (int z = 0; z <= 2; z++)
            {
                if ((x == 0 || x == 19 || z == 0 || z == 2) && z != 2)
                {
                    builder.add(x, 0, z, fence());
                }
            }
        }

        double confidence = enclosure("", 0.5).evaluate(builder.build(), ELEMENTS).confidence();
        assertTrue(confidence > 0.4 && confidence < 0.75, "expected roughly half-enclosed, was " + confidence);
    }

    @Test
    @DisplayName("a straight fence across the middle, not actually enclosing anything, scores 0")
    void straightFenceAcrossTheMiddleScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200).bounds(new RegionBounds(0, 0, 0, 6, 0, 6));

        for (int x = 0; x <= 6; x++)
        {
            builder.add(x, 0, 3, fence());
        }

        assertEquals(0.0, enclosure("", 0.5).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("'around' naming another element encloses that element's footprint, not the whole region")
    void aroundNamesAnotherElementsFootprint()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        // ice sits in the middle third of the room; the fence rings only the ice, not the room
        for (int x = 1; x <= 3; x++)
        {
            for (int z = 1; z <= 3; z++)
            {
                builder.add(x, 0, z, ice());
            }
        }

        for (int x = 0; x <= 4; x++)
        {
            for (int z = 0; z <= 4; z++)
            {
                if (x == 0 || x == 4 || z == 0 || z == 4)
                {
                    builder.add(x, 0, z, fence());
                }
            }
        }

        assertEquals(1.0, enclosure("surface", 0.5).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an absent 'of' scores 0 and names it in the diagnostic")
    void absentOfNamesIt()
    {
        FormResult result = enclosure("", 0.5).evaluate(RegionGeometry.EMPTY, Map.of());
        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'fence'"), result.diagnostic());
    }
}
