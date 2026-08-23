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

/** {@code above} and {@code beneath} (#29): {@link VerticalRelationClause}. */
class VerticalRelationClauseTest
{
    private static final BlockMatcher RAILS = BlockMatcher.ofBlocks("test:rail");
    private static final BlockMatcher ICE = BlockMatcher.ofBlocks("test:ice");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("rails", RAILS, "ice", ICE);

    private static final AboveClauseType ABOVE = new AboveClauseType();
    private static final BeneathClauseType BENEATH = new BeneathClauseType();

    private static FormClause above(String of, String to)
    {
        return ABOVE.create(ClauseParams.builder()
                .put("of", of).put("to", to).put("max_drop", 1).put("alignment", "column").put("coverage", 1.0)
                .build());
    }

    private static FormClause beneath(String of, String to)
    {
        return BENEATH.create(ClauseParams.builder()
                .put("of", of).put("to", to).put("max_drop", 1).put("alignment", "column").put("coverage", 1.0)
                .build());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    /** Rails at y=1 running straight over an ice field at y=0, same X/Z columns. */
    private static RegionGeometry railsOnIce()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 5; x++)
        {
            builder.add(x, 1, 0, block("test:rail"));
            builder.add(x, 0, 0, block("test:ice"));
        }

        return builder.build();
    }

    @Test
    @DisplayName("rails on an ice field score 1.0 for above{of: rails, to: ice}")
    void railsAboveIceScoresFull()
    {
        assertEquals(1.0, above("rails", "ice").evaluate(railsOnIce(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the same field with the ice elsewhere scores 0")
    void iceElsewhereScoresZero()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 5; x++)
        {
            builder.add(x, 1, 0, block("test:rail"));
            builder.add(x, 0, 5, block("test:ice"));
        }

        assertEquals(0.0, above("rails", "ice").evaluate(builder.build(), ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("above{of: rails, to: ice} and beneath{of: ice, to: rails} are not symmetric - assert the actual numbers")
    void directionIsNotSymmetric()
    {
        // rails over ice for x in [0,4], but the ice field also runs further, to x in [0,6] with
        // no rail above the extra two cells - so "rails are above ice" is perfect (every rail has
        // ice under it), but "ice is beneath rails" is not (2 of 7 ice cells have no rail over them)
        RegionGeometry.Builder builder = RegionGeometry.builder(100);

        for (int x = 0; x < 5; x++)
        {
            builder.add(x, 1, 0, block("test:rail"));
        }

        for (int x = 0; x < 7; x++)
        {
            builder.add(x, 0, 0, block("test:ice"));
        }

        RegionGeometry geometry = builder.build();

        double aboveConfidence = above("rails", "ice").evaluate(geometry, ELEMENTS).confidence();
        double beneathConfidence = beneath("ice", "rails").evaluate(geometry, ELEMENTS).confidence();

        assertEquals(1.0, aboveConfidence, 1e-9, "every rail has ice directly beneath it");
        assertEquals(5.0 / 7.0, beneathConfidence, 1e-9, "only 5 of 7 ice cells have a rail directly above");
        assertTrue(aboveConfidence != beneathConfidence, "the two relations must not agree just because they share a build");
    }

    @Test
    @DisplayName("an absent 'of' or 'to' scores 0 and names which side is missing")
    void absentElementNamesTheMissingSide()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, block("test:ice")).build();

        FormResult result = above("rails", "ice").evaluate(geometry, Map.of("ice", ICE));

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'rails'"), result.diagnostic());
    }
}
