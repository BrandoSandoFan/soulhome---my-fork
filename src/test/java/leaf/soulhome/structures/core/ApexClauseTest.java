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

class ApexClauseTest
{
    private static final BlockMatcher ROD = BlockMatcher.ofBlocks("test:rod");
    private static final BlockMatcher COPPER = BlockMatcher.ofBlocks("test:copper");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("rod", ROD, "mast", COPPER);

    private static final ApexClauseType TYPE = new ApexClauseType();

    private static FormClause apex(int tolerance)
    {
        return TYPE.create(ClauseParams.builder().put("of", "rod").put("tolerance", tolerance).build());
    }

    private static BlockSignature rod()
    {
        return new TestBlocks.TestBlock("test:rod", Set.of(), Passability.PASSABLE);
    }

    private static BlockSignature copper()
    {
        return new TestBlocks.TestBlock("test:copper", Set.of(), Passability.BLOCKING);
    }

    /** A mast of copper from y=0 to y=5 with a rod on top - the build the clause is written for. */
    private static RegionGeometry mastWithRodAt(int rodY)
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int y = 0; y <= 5; y++)
        {
            builder.add(0, y, 0, copper());
        }

        builder.add(1, rodY, 0, rod());
        return builder.bounds(new RegionBounds(0, 0, 0, 1, 5, 0)).build();
    }

    @Test
    @DisplayName("a rod at the top of the mast scores 1.0")
    void rodAtTheTipScoresFull()
    {
        assertEquals(1.0, apex(1).evaluate(mastWithRodAt(5), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the same mast with the rod at its foot scores 0 - the clause's whole reason to exist")
    void rodAtTheFootScoresZero()
    {
        assertEquals(0.0, apex(1).evaluate(mastWithRodAt(0), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("tolerance lets a rod one layer down still count")
    void toleranceAdmitsOneLayerDown()
    {
        assertEquals(0.0, apex(0).evaluate(mastWithRodAt(4), ELEMENTS).confidence(), 1e-9);
        assertEquals(1.0, apex(1).evaluate(mastWithRodAt(4), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("two rods, one crowning and one at ground level, score half")
    void partialCoverageScoresTheFraction()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int y = 0; y <= 5; y++)
        {
            builder.add(0, y, 0, copper());
        }

        builder.add(1, 5, 0, rod());
        builder.add(2, 0, 0, rod());

        RegionGeometry geometry = builder.bounds(new RegionBounds(0, 0, 0, 2, 5, 0)).build();

        assertEquals(0.5, apex(1).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the roof is the region's own extent, not the element's - a lone rod low down still scores 0")
    void heightIsMeasuredAgainstTheRegionNotTheElement()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        builder.add(0, 0, 0, rod());

        // the region reaches y=9 even though nothing but the rod was indexed; comparing the rod
        // against itself would call this a perfect apex
        RegionGeometry geometry = builder.bounds(new RegionBounds(0, 0, 0, 0, 9, 0)).build();

        assertEquals(0.0, apex(1).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a geometry with no bounds falls back to the highest indexed cell rather than failing")
    void noBoundsFallsBackToIndexedCells()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        builder.add(0, 0, 0, copper());
        builder.add(0, 4, 0, copper());
        builder.add(1, 4, 0, rod());

        assertEquals(1.0, apex(1).evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a missing element scores 0 and says which one")
    void missingElementIsNamed()
    {
        FormResult result = apex(1).evaluate(RegionGeometry.EMPTY, Map.of());

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("rod"));
    }

    @Test
    @DisplayName("params round-trip through encode")
    void encodeRoundTrips()
    {
        FormClause clause = apex(3);
        Map<String, Object> encoded = TYPE.encode(clause);

        assertEquals("rod", encoded.get("of"));
        assertEquals(3, encoded.get("tolerance"));
        assertEquals(clause, TYPE.create(ClauseParams.builder()
                .put("of", encoded.get("of")).put("tolerance", encoded.get("tolerance")).build()));
    }
}
