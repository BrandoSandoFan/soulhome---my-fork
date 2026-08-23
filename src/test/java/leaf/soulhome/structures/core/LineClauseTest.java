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

class LineClauseTest
{
    private static final BlockMatcher FENCE = BlockMatcher.ofBlocks("test:fence");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("fence", FENCE);

    private static final LineClauseType TYPE = new LineClauseType();

    private static FormClause line(int minRun, int idealRun, int maxDeviation)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "fence").put("min_run", minRun).put("ideal_run", idealRun).put("max_deviation", maxDeviation)
                .build());
    }

    private static BlockSignature fence()
    {
        return new TestBlocks.TestBlock("test:fence", Set.of(), Passability.PASSABLE);
    }

    @Test
    @DisplayName("8 fences in a row scores 1.0 at ideal_run 8")
    void straightRowScoresFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 8; x++)
        {
            builder.add(x, 0, 0, fence());
        }

        assertEquals(1.0, line(4, 8, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("8 fences in a 3x3 blob scores low - well under the same 8 in a row")
    void blobScoresLow()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 3; x++)
        {
            for (int z = 0; z < 3; z++)
            {
                if (!(x == 1 && z == 1))
                {
                    builder.add(x, 0, z, fence());
                }
            }
        }

        assertTrue(line(4, 8, 1).evaluate(builder.build(), ELEMENTS).confidence() < 0.5);
    }

    @Test
    @DisplayName("a run with a one-cell jog stays one run at max_deviation 1")
    void jogStaysOneRun()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 4; x++)
        {
            builder.add(x, 0, 0, fence());
        }

        for (int x = 4; x < 8; x++)
        {
            builder.add(x, 0, 1, fence());
        }

        assertEquals(1.0, line(4, 8, 1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the same jog splits into two shorter runs once max_deviation is 0")
    void jogSplitsAtZeroDeviation()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 4; x++)
        {
            builder.add(x, 0, 0, fence());
        }

        for (int x = 4; x < 8; x++)
        {
            builder.add(x, 0, 1, fence());
        }

        assertEquals(0.5, line(4, 8, 0).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an absent 'of' scores 0 and names it in the diagnostic")
    void absentOfNamesIt()
    {
        FormResult result = line(4, 8, 1).evaluate(RegionGeometry.EMPTY, Map.of());
        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'fence'"), result.diagnostic());
    }
}
