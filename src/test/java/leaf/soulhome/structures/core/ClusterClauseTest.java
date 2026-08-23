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

class ClusterClauseTest
{
    private static final BlockMatcher BARRELS = BlockMatcher.ofBlocks("test:barrel");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("barrels", BARRELS);

    private static final ClusterClauseType TYPE = new ClusterClauseType();
    private static final FormClause CLUSTER = TYPE.create(ClauseParams.builder().put("of", "barrels").build());

    private static BlockSignature barrel()
    {
        return new TestBlocks.TestBlock("test:barrel", Set.of(), Passability.PASSABLE);
    }

    @Test
    @DisplayName("8 barrels stacked mutually adjacent score 1.0")
    void stackedBarrelsScoreFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int y = 0; y < 8; y++)
        {
            builder.add(0, y, 0, barrel());
        }

        assertEquals(1.0, CLUSTER.evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("8 barrels one per corner of a room score 0.125 - the largest group is a single barrel")
    void scatteredBarrelsScoreOneOverEight()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        int[][] corners = {
                {0, 0, 0}, {10, 0, 0}, {0, 0, 10}, {10, 0, 10},
                {0, 10, 0}, {10, 10, 0}, {0, 10, 10}, {10, 10, 10}
        };

        for (int[] corner : corners)
        {
            builder.add(corner[0], corner[1], corner[2], barrel());
        }

        assertEquals(0.125, CLUSTER.evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an absent 'of' scores 0, not NaN")
    void absentOfScoresZeroNotNaN()
    {
        FormResult result = CLUSTER.evaluate(RegionGeometry.EMPTY, Map.of());
        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'barrels'"), result.diagnostic());
    }
}
