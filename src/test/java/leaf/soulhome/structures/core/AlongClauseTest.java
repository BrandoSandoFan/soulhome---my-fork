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

class AlongClauseTest
{
    private static final BlockMatcher FENCE = BlockMatcher.ofBlocks("test:fence");
    private static final BlockMatcher TRACK = BlockMatcher.ofBlocks("test:track");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("fence", FENCE, "track", TRACK);

    private static final AlongClauseType TYPE = new AlongClauseType();

    private static FormClause along(int maxDistance, int minRun)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "fence").put("to", "track")
                .put("max_distance", maxDistance).put("min_run", minRun).put("coverage", 1.0)
                .build());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    @Test
    @DisplayName("a fence running the full length of a track scores 1.0")
    void fullLengthRunScoresFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 8; x++)
        {
            builder.add(x, 0, 0, block("test:track"));
            builder.add(x, 0, 1, block("test:fence"));
        }

        assertEquals(1.0, along(2, 4).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a single fence post near the track, with no run, does not satisfy min_run")
    void singlePostDoesNotSatisfyMinRun()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:track"))
                .add(0, 0, 1, block("test:fence"))
                .build();

        assertEquals(0.0, along(2, 4).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a run that meets min_run counts, and only that run's cells are satisfied")
    void qualifyingRunCountsPartially()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        // a qualifying run of 4 fence cells beside the track
        for (int x = 0; x < 4; x++)
        {
            builder.add(x, 0, 0, block("test:track"));
            builder.add(x, 0, 1, block("test:fence"));
        }

        // one lone fence cell far away, near no track - never satisfied
        builder.add(50, 0, 0, block("test:fence"));

        FormResult result = along(2, 4).evaluate(builder.build(), ELEMENTS);

        assertEquals(4.0 / 5.0, result.confidence(), 1e-9);
    }

    @Test
    @DisplayName("an absent 'of' or 'to' scores 0 and names which side is missing")
    void absentElementNamesTheMissingSide()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, block("test:track")).build();

        FormResult result = along(2, 4).evaluate(geometry, Map.of("track", TRACK));

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'fence'"), result.diagnostic());
    }
}
