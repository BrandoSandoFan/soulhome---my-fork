/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code soulhome:loop} (#31) - the trickiest grading in the whole issue. See
 * {@link LoopClauseType}'s own doc for the algorithm's rationale.
 *
 * <p>The rectangle-ring tests below ({@link #removingOneRailDropsSharplyButNotToZero} and
 * {@link #ringWithASpurScoresBelowTheSameRingWithoutOne}) deliberately use {@code orthogonal}
 * connectivity rather than the {@code planar} default. A thin rectangular ring under {@code planar}
 * (26-neighbour) adjacency picks up diagonal "shortcut" edges near its corners - a cell one step
 * off a corner is often within Chebyshev distance 1 of a cell on the *next* side over - which turns
 * the ring into a graph with chords, not a simple cycle. That is a fine, even desirable, property
 * of {@code planar} in general (it is exactly what lets a real ramped rail loop with a diagonal
 * corner stay one component), but it defeats the purpose of a test that wants a clean single point
 * of failure. {@code orthogonal} has no diagonal edges at all, so a thin ring under it is a true
 * chordless cycle and removing one cell behaves exactly as the algorithm's doc describes.
 */
class LoopClauseTest
{
    private static final BlockMatcher RAILS = BlockMatcher.ofBlocks("test:rail");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("rails", RAILS);

    private static final LoopClauseType TYPE = new LoopClauseType();

    private static FormClause loop(int minCells, int idealCells, String connectivity)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "rails").put("min_cells", minCells).put("ideal_cells", idealCells)
                .put("connectivity", connectivity)
                .build());
    }

    private static BlockSignature rail()
    {
        return new TestBlocks.TestBlock("test:rail", Set.of(), Passability.PASSABLE);
    }

    /** The border of a 5x3 rectangle (x:0-4, z:0-2) - 12 cells, a clean rectangular ring. */
    private static RegionGeometry.Builder rectangleRing()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int x = 0; x <= 4; x++)
        {
            for (int z = 0; z <= 2; z++)
            {
                if (x == 0 || x == 4 || z == 0 || z == 2)
                {
                    builder.add(x, 0, z, rail());
                }
            }
        }

        return builder;
    }

    @Test
    @DisplayName("a closed rectangle of rails scores 1.0 at a matching ideal_cells")
    void closedRectangleScoresFull()
    {
        FormResult result = loop(8, 12, "orthogonal").evaluate(rectangleRing().build(), ELEMENTS);
        assertEquals(1.0, result.confidence(), 1e-9);
    }

    @Test
    @DisplayName("the same rectangle with one rail removed drops sharply but is not 0 - a horseshoe is a partial loop")
    void removingOneRailDropsSharplyButNotToZero()
    {
        RegionGeometry.Builder builder = rectangleRing();
        // remove the middle of one straight edge - not a corner, so there is no diagonal shortcut
        // to reconnect around it even under planar; here it does not matter either way since this
        // test uses orthogonal, which has no diagonal edges at all
        RegionGeometry withGap = RegionGeometry.builder(200)
                .add(0, 0, 0, rail()).add(1, 0, 0, rail()).add(3, 0, 0, rail()).add(4, 0, 0, rail())
                .add(0, 0, 1, rail()).add(4, 0, 1, rail())
                .add(0, 0, 2, rail()).add(1, 0, 2, rail()).add(2, 0, 2, rail()).add(3, 0, 2, rail()).add(4, 0, 2, rail())
                .build();

        double full = loop(8, 12, "orthogonal").evaluate(rectangleRing().build(), ELEMENTS).confidence();
        FormResult gapped = loop(8, 12, "orthogonal").evaluate(withGap, ELEMENTS);

        assertTrue(gapped.confidence() > 0d, "a one-block gap must not read as 'never attempted a loop'");
        assertTrue(gapped.confidence() < full - 0.3, "full=" + full + " gapped=" + gapped.confidence() + " - expected a sharp drop");
    }

    @Test
    @DisplayName("a straight line of 30 rails scores 0")
    void straightLineScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int x = 0; x < 30; x++)
        {
            builder.add(x, 0, 0, rail());
        }

        FormResult result = loop(8, 12, "planar").evaluate(builder.build(), ELEMENTS);
        assertEquals(0.0, result.confidence(), 1e-9);
    }

    @Test
    @DisplayName("a heap of 8 rails in a 2x2x2 cube scores 0 - the min_cells floor catches what closure alone would not")
    void heapOfRailsScoresZero()
    {
        // a solid cube is, taken purely as a graph, densely cyclic under planar connectivity (every
        // cell touches every other), so closure alone reads it as a "perfect" tiny loop - it is
        // min_cells, not closure, that correctly rejects it as nowhere near a real circuit's size;
        // see the worked #25/#31 example this min_cells:12 mirrors
        RegionGeometry.Builder builder = RegionGeometry.builder(200);

        for (int x = 0; x < 2; x++)
        {
            for (int y = 0; y < 2; y++)
            {
                for (int z = 0; z < 2; z++)
                {
                    builder.add(x, y, z, rail());
                }
            }
        }

        FormResult result = loop(12, 40, "planar").evaluate(builder.build(), ELEMENTS);
        assertEquals(0.0, result.confidence(), 1e-9);
    }

    /** Every lattice point at Manhattan distance {@code radius} from the origin, in perimeter order. */
    private static List<int[]> diamondRing(int radius)
    {
        List<int[]> points = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if (Math.abs(dx) + Math.abs(dz) == radius)
                {
                    points.add(new int[]{dx, dz});
                }
            }
        }

        points.sort(Comparator.comparingDouble(p -> Math.atan2(p[1], p[0])));
        return points;
    }

    @Test
    @DisplayName("an oval and a rectangle of equal cyclic length score identically - shape families, not shapes")
    void ovalAndRectangleOfEqualLengthScoreTheSame()
    {
        RegionGeometry.Builder diamond = RegionGeometry.builder(200);

        for (int[] point : diamondRing(3))
        {
            diamond.add(point[0] + 10, 0, point[1] + 10, rail());
        }

        // both are exactly 12 cells - the diamond by construction (4 * radius), the rectangle as
        // rectangleRing() above
        double ovalConfidence = loop(8, 30, "planar").evaluate(diamond.build(), ELEMENTS).confidence();
        double rectangleConfidence = loop(8, 30, "planar").evaluate(rectangleRing().build(), ELEMENTS).confidence();

        assertEquals(ovalConfidence, rectangleConfidence, 1e-9);
        assertTrue(ovalConfidence > 0d && ovalConfidence < 1d, "should be mid-ramp, not capped, for the comparison to mean anything");
    }

    @Test
    @DisplayName("a figure-of-eight scores at least as well as a simple ring of comparable length")
    void figureOfEightScoresAtLeastAsWellAsASimpleRing()
    {
        // two 8-cell rings (borders of two 3x3 squares) sharing exactly one corner cell - a genuine
        // single crossing point, 15 distinct cells total
        RegionGeometry.Builder figureEight = RegionGeometry.builder(200);
        int[][] ringA = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};
        int[][] ringB = {{2, 2}, {3, 2}, {4, 2}, {2, 3}, {4, 3}, {2, 4}, {3, 4}, {4, 4}};
        Set<String> seen = new java.util.HashSet<>();

        for (int[] point : ringA)
        {
            if (seen.add(point[0] + "," + point[1]))
            {
                figureEight.add(point[0], 0, point[1], rail());
            }
        }

        for (int[] point : ringB)
        {
            if (seen.add(point[0] + "," + point[1]))
            {
                figureEight.add(point[0], 0, point[1], rail());
            }
        }

        // a plain rectangle ring of comparable (not identical - rectangle perimeters are always
        // even, and 15 is odd) length: the border of a 5x4 rectangle, 14 cells
        RegionGeometry.Builder simpleRing = RegionGeometry.builder(200);

        for (int x = 0; x <= 4; x++)
        {
            for (int z = 0; z <= 3; z++)
            {
                if (x == 0 || x == 4 || z == 0 || z == 3)
                {
                    simpleRing.add(x, 0, z, rail());
                }
            }
        }

        FormClause type = loop(8, 30, "planar");
        double figureEightConfidence = type.evaluate(figureEight.build(), ELEMENTS).confidence();
        double simpleRingConfidence = type.evaluate(simpleRing.build(), ELEMENTS).confidence();

        assertTrue(figureEightConfidence >= simpleRingConfidence,
                "figure-8=" + figureEightConfidence + " simple ring=" + simpleRingConfidence);
    }

    @Test
    @DisplayName("a ring with a dead-end spur scores below the same ring without one")
    void ringWithASpurScoresBelowTheSameRingWithoutOne()
    {
        RegionGeometry.Builder withSpur = rectangleRing();
        withSpur.add(2, 0, -1, rail());
        withSpur.add(2, 0, -2, rail());

        FormClause type = loop(8, 12, "orthogonal");
        double plainConfidence = type.evaluate(rectangleRing().build(), ELEMENTS).confidence();
        double spurredConfidence = type.evaluate(withSpur.build(), ELEMENTS).confidence();

        assertEquals(1.0, plainConfidence, 1e-9);
        assertTrue(spurredConfidence < plainConfidence, "spur=" + spurredConfidence + " plain=" + plainConfidence);
    }

    @Test
    @DisplayName("a ramped circuit that climbs and descends by one block stays connected under planar connectivity")
    void rampedCircuitStaysConnected()
    {
        RegionGeometry.Builder ramped = RegionGeometry.builder(200);
        List<int[]> points = diamondRing(3);

        for (int i = 0; i < points.size(); i++)
        {
            int[] point = points.get(i);
            // alternating +1/-1 steps in Y as the circuit runs around - never more than one block of
            // climb or descent between consecutive cells, which is exactly what a rail slope does
            ramped.add(point[0] + 10, i % 2, point[1] + 10, rail());
        }

        FormResult result = loop(8, 12, "planar").evaluate(ramped.build(), ELEMENTS);

        assertEquals(1.0, result.confidence(), 1e-9,
                "the ramp must not fracture the loop into disconnected pieces under planar connectivity");
    }

    @Test
    @DisplayName("an absent 'of' scores 0 and names it in the diagnostic")
    void absentOfNamesIt()
    {
        FormResult result = loop(8, 12, "planar").evaluate(RegionGeometry.EMPTY, Map.of());
        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'rails'"), result.diagnostic());
    }
}
