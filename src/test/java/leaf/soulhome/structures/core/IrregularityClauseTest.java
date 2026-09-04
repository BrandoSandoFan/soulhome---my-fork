/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrregularityClauseTest
{
    private static final BlockMatcher STONE = BlockMatcher.ofBlocks("test:stone");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("shell", STONE);

    private static final IrregularityClauseType TYPE = new IrregularityClauseType();

    private static FormClause irregularity(int runLength)
    {
        return TYPE.create(ClauseParams.builder().put("of", "shell").put("run_length", runLength).build());
    }

    private static BlockSignature stone()
    {
        return new TestBlocks.TestBlock("test:stone", Set.of(), Passability.BLOCKING);
    }

    @Test
    @DisplayName("a flat 6x6 wall is entirely regular and scores 0")
    void flatWallScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int x = 0; x < 6; x++)
        {
            for (int y = 0; y < 6; y++)
            {
                builder.add(x, y, 0, stone());
            }
        }

        assertEquals(0.0, irregularity(4).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("scattered singles are wholly irregular and score 1.0")
    void scatteredSinglesScoreFull()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int i = 0; i < 8; i++)
        {
            builder.add(i * 3, i * 2, i * 5, stone());
        }

        assertEquals(1.0, irregularity(4).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a run exactly run_length long counts as regular; one block shorter does not")
    void runLengthIsTheThreshold()
    {
        RegionGeometry.Builder shortRun = RegionGeometry.builder(100);

        for (int x = 0; x < 3; x++)
        {
            shortRun.add(x, 0, 0, stone());
        }

        assertEquals(1.0, irregularity(4).evaluate(shortRun.build(), ELEMENTS).confidence(), 1e-9);

        RegionGeometry.Builder exactRun = RegionGeometry.builder(100);

        for (int x = 0; x < 4; x++)
        {
            exactRun.add(x, 0, 0, stone());
        }

        assertEquals(0.0, irregularity(4).evaluate(exactRun.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the middle of a long wall is regular too - the run is measured through a cell, not from it")
    void runIsMeasuredThroughTheCell()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 7; x++)
        {
            builder.add(x, 0, 0, stone());
        }

        // if the run were only counted forwards, the last three cells would each read as a run of
        // 3, 2 and 1 and the wall would score 3/7 irregular rather than 0
        assertEquals(0.0, irregularity(4).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a wandering hollow scores higher than a squared-off one with the same block count")
    void carvedScoresHigherThanSquared()
    {
        RegionGeometry.Builder squared = RegionGeometry.builder(200);

        for (int x = 0; x < 4; x++)
        {
            for (int z = 0; z < 4; z++)
            {
                squared.add(x, 0, z, stone());
            }
        }

        RegionGeometry.Builder carved = RegionGeometry.builder(200);
        int[][] wander = {{0, 0}, {1, 0}, {2, 1}, {3, 1}, {3, 2}, {2, 3}, {1, 3}, {0, 2},
                {5, 5}, {6, 6}, {7, 5}, {8, 7}, {9, 6}, {10, 8}, {11, 7}, {12, 9}};

        for (int[] point : wander)
        {
            carved.add(point[0], 0, point[1], stone());
        }

        final double squaredScore = irregularity(4).evaluate(squared.build(), ELEMENTS).confidence();
        final double carvedScore = irregularity(4).evaluate(carved.build(), ELEMENTS).confidence();

        assertEquals(0.0, squaredScore, 1e-9);
        assertTrue(carvedScore > squaredScore, "a carved hollow should out-score a squared one, got " + carvedScore);
        assertEquals(1.0, carvedScore, 1e-9);
    }

    @Test
    @DisplayName("runs count along Y as well - a tall straight pillar is regular, not organic")
    void verticalRunsCountToo()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int y = 0; y < 5; y++)
        {
            builder.add(0, y, 0, stone());
        }

        assertEquals(0.0, irregularity(4).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a missing element scores 0 and says which one")
    void missingElementIsNamed()
    {
        FormResult result = irregularity(4).evaluate(RegionGeometry.EMPTY, Map.of());

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("shell"));
    }

    @Test
    @DisplayName("params round-trip through encode")
    void encodeRoundTrips()
    {
        FormClause clause = irregularity(5);
        Map<String, Object> encoded = TYPE.encode(clause);

        assertEquals("shell", encoded.get("of"));
        assertEquals(5, encoded.get("run_length"));
        assertEquals(clause, TYPE.create(ClauseParams.builder()
                .put("of", encoded.get("of")).put("run_length", encoded.get("run_length")).build()));
    }

    @Test
    @DisplayName("a run_length of one would call every cell regular, and is rejected")
    void runLengthOfOneIsRejected()
    {
        assertTrue(irregularity(1).validationErrors(Set.of("shell")).size() == 1);
        assertTrue(irregularity(4).validationErrors(Set.of("shell")).isEmpty());
    }
}
