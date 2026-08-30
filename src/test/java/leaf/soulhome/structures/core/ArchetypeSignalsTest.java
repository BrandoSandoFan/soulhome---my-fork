/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchetypeSignalsTest
{
    @Test
    @DisplayName("geometryFilterFor matches only blocks named by a form's elements")
    void geometryFilterMatchesFormElementsOnly()
    {
        ArchetypeDefinition archetype = archetypeWithForm(
                Map.of("rails", BlockMatcher.ofTags("minecraft:rails")));

        Predicate<BlockSignature> filter = ArchetypeSignals.geometryFilterFor(List.of(archetype));

        assertTrue(filter.test(TestBlocks.RAIL));
        assertFalse(filter.test(TestBlocks.STONE));
    }

    @Test
    @DisplayName("a signal or requirement match is not, on its own, indexed for geometry")
    void signalsAloneDoNotFeedGeometry()
    {
        // signals() is exercised by filterFor already - geometryFilterFor must not also fold it
        // in, or "only index what a form will ask about" (#26) would index the whole signal set
        ArchetypeDefinition archetype = new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(),
                List.of());

        assertFalse(ArchetypeSignals.geometryFilterFor(List.of(archetype)).test(TestBlocks.BOOKSHELF));
        assertTrue(ArchetypeSignals.filterFor(List.of(archetype)).test(TestBlocks.BOOKSHELF));
    }

    @Test
    @DisplayName("no archetypes named a form means the filter matches nothing")
    void noFormsMeansNothingMatches()
    {
        assertFalse(ArchetypeSignals.geometryFilterFor(List.of()).test(TestBlocks.RAIL));
    }

    @Test
    @DisplayName("elements from every form of every archetype are unioned")
    void elementsAreUnionedAcrossFormsAndArchetypes()
    {
        ArchetypeDefinition track = archetypeWithForm(Map.of("rails", BlockMatcher.ofTags("minecraft:rails")));
        ArchetypeDefinition hearth = archetypeWithForm(Map.of("seats", BlockMatcher.ofTags("soulhome:seating")));

        Predicate<BlockSignature> filter = ArchetypeSignals.geometryFilterFor(List.of(track, hearth));

        assertTrue(filter.test(TestBlocks.RAIL));
        assertTrue(filter.test(TestBlocks.CHAIR));
    }

    private static ArchetypeDefinition archetypeWithForm(Map<String, BlockMatcher> elements)
    {
        FakeClauseForFilterTest root = new FakeClauseForFilterTest(elements.keySet());

        Form form = new Form("form", 1.0, null, elements, root, elements.keySet());

        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(),
                List.of(form));
    }

    private record FakeClauseForFilterTest(Set<String> elementNames) implements FormClause
    {
        @Override
        public String typeId()
        {
            return "fake";
        }

        @Override
        public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
        {
            return FormResult.ZERO;
        }

        @Override
        public String describe()
        {
            return "";
        }

        @Override
        public List<String> validationErrors(Set<String> elementNames)
        {
            return List.of();
        }
    }
}
