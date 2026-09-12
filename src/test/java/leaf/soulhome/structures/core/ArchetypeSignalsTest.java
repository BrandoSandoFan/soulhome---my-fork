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

    // region open-cluster filter (#134)

    @Test
    @DisplayName("an enclosed-only archetype's palette is counted but does not seed open-air clusters")
    void enclosedOnlyPaletteDoesNotSeedClusters()
    {
        ArchetypeDefinition coldStorage = archetypeNaming(
                List.of(RegionType.ENCLOSED), BlockMatcher.ofBlocks("minecraft:snow_block"));

        assertTrue(ArchetypeSignals.filterFor(List.of(coldStorage)).test(TestBlocks.SNOW_BLOCK),
                "the counting filter is unchanged: snow is still a block some archetype names");
        assertFalse(ArchetypeSignals.openClusterFilterFor(List.of(coldStorage)).test(TestBlocks.SNOW_BLOCK),
                "nothing open can score snow, so snow should not seed an open-air cluster");
    }

    @Test
    @DisplayName("a datapack adding an open archetype that names snow gets snow back as a cluster seed")
    void anOpenArchetypeNamingSnowSeedsClusters()
    {
        ArchetypeDefinition coldStorage = archetypeNaming(
                List.of(RegionType.ENCLOSED), BlockMatcher.ofBlocks("minecraft:snow_block"));
        ArchetypeDefinition snowField = archetypeNaming(
                List.of(RegionType.OPEN, RegionType.ENCLOSED), BlockMatcher.ofBlocks("minecraft:snow_block"));

        assertTrue(ArchetypeSignals.openClusterFilterFor(List.of(coldStorage, snowField)).test(TestBlocks.SNOW_BLOCK),
                "no Java change: the filter is derived from whatever is loaded");
    }

    @Test
    @DisplayName("an open archetype's requirements seed clusters too, as they always did")
    void openRequirementsSeedClusters()
    {
        ArchetypeDefinition farm = new ArchetypeDefinition(
                "soulhome:test_farm", "archetype.soulhome.test", List.of(RegionType.OPEN), 1,
                List.of(new ArchetypeDefinition.Requirement(BlockMatcher.ofTags("minecraft:crops"), 9)),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:farmland"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(),
                List.of());

        Predicate<BlockSignature> filter = ArchetypeSignals.openClusterFilterFor(List.of(farm));

        assertTrue(filter.test(TestBlocks.WHEAT));
        assertTrue(filter.test(TestBlocks.FARMLAND));
    }

    @Test
    @DisplayName("a signal marked seed: false is counted but never gathered around")
    void aNonSeedingSignalDoesNotSeedClusters()
    {
        ArchetypeDefinition spire = new ArchetypeDefinition(
                "soulhome:test_spire", "archetype.soulhome.test", List.of(RegionType.OPEN), 1,
                List.of(),
                List.of(
                        new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:lightning_rod"), 6d, "conductor", 3),
                        new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:structural"), 0.5d, "masonry", 48, false)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(),
                List.of());

        assertTrue(ArchetypeSignals.openClusterFilterFor(List.of(spire)).test(TestBlocks.LIGHTNING_ROD));
        assertFalse(ArchetypeSignals.openClusterFilterFor(List.of(spire)).test(TestBlocks.DEEPSLATE),
                "the ground a spire stands on must not gather the island around it");
        assertTrue(ArchetypeSignals.filterFor(List.of(spire)).test(TestBlocks.DEEPSLATE),
                "but masonry the spire's region takes in still counts");
    }

    private static ArchetypeDefinition archetypeNaming(List<RegionType> regionTypes, BlockMatcher signal)
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", regionTypes, 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(signal, 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(),
                List.of());
    }

    // endregion

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
