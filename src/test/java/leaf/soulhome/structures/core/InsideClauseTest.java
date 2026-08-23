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

/** {@code inside} (#30): footprint containment, as opposed to {@link SurroundsClauseTest}'s angular coverage. */
class InsideClauseTest
{
    private static final BlockMatcher ICE = BlockMatcher.ofBlocks("test:ice");
    private static final BlockMatcher RAILS = BlockMatcher.ofBlocks("test:rail");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("surface", ICE, "rails", RAILS);

    private static final InsideClauseType TYPE = new InsideClauseType();

    private static FormClause inside(int maxGap)
    {
        return TYPE.create(ClauseParams.builder().put("of", "surface").put("to", "rails").put("max_gap", maxGap).build());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    /** A 5x5 rail ring (the border of a 5x5 square) at y=0, with generous bounds around it. */
    private static RegionGeometry.Builder ringOfRails()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200).bounds(new RegionBounds(0, 0, 0, 14, 0, 14));

        for (int x = 0; x <= 4; x++)
        {
            for (int z = 0; z <= 4; z++)
            {
                if (x == 0 || x == 4 || z == 0 || z == 4)
                {
                    builder.add(x, 0, z, block("test:rail"));
                }
            }
        }

        return builder;
    }

    @Test
    @DisplayName("ice filling a rail loop scores high")
    void iceInsideTheLoopScoresHigh()
    {
        RegionGeometry.Builder builder = ringOfRails();

        for (int x = 1; x <= 3; x++)
        {
            for (int z = 1; z <= 3; z++)
            {
                builder.add(x, 0, z, block("test:ice"));
            }
        }

        assertEquals(1.0, inside(1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the same ice outside the loop scores 0")
    void iceOutsideTheLoopScoresZero()
    {
        RegionGeometry.Builder builder = ringOfRails();

        for (int x = 10; x <= 12; x++)
        {
            for (int z = 10; z <= 12; z++)
            {
                builder.add(x, 0, z, block("test:ice"));
            }
        }

        assertEquals(0.0, inside(1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an absent 'of' or 'to' scores 0 and names which side is missing")
    void absentElementNamesTheMissingSide()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, block("test:rail")).build();

        FormResult result = inside(1).evaluate(geometry, Map.of("rails", RAILS));

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'surface'"), result.diagnostic());
    }
}
