/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories;

import leaf.soulhome.datagen.patchouli.categories.data.ArchetypeDocs;
import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import leaf.soulhome.datagen.patchouli.categories.data.TagDocs;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.BlockMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "how you arrange it" page #35 (of the structural considerations epic, #25) adds must
 * appear for every shipped archetype that has forms, and only for those - a page that showed up
 * regardless would tell a player every room cares about arrangement, and a missing one for a room
 * that genuinely has forms would be exactly the silent gap the issue warns about.
 */
class PatchouliMultiblocksTest
{
    @Test
    @DisplayName("every shipped archetype with structures gets an arrangement page, and it mentions the form")
    void shippedArchetypesWithFormsGetThePage()
    {
        for (ArchetypeDefinition archetype : ArchetypeDocs.shipped())
        {
            Optional<BookStuff.Page> page = PatchouliMultiblocks.arrangementPage(archetype);

            if (archetype.structures().isEmpty())
            {
                assertTrue(page.isEmpty(), archetype.id() + " has no forms, so should have no arrangement page");
            }
            else
            {
                assertTrue(page.isPresent(), archetype.id() + " has forms, so should document them");
                assertFalse(page.get().text.isBlank(), archetype.id() + "'s arrangement page has no text");
            }
        }
    }

    @Test
    @DisplayName("an archetype with no structures gets no arrangement page at all")
    void archetypeWithoutFormsGetsNoPage()
    {
        ArchetypeDefinition noForms = new ArchetypeDefinition(
                "soulhome:test_no_forms", "archetype.soulhome.test", List.of(), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:torch"), 1.0, "light", 16)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1.0, 1)),
                List.of(),
                List.of());

        assertTrue(PatchouliMultiblocks.arrangementPage(noForms).isEmpty());
    }

    /**
     * #49: every {@code soulhome:} tag a shipped archetype names in a requirement, a signal or a
     * detractor has to be explained somewhere a player can reach - the test that stops a new tag
     * shipping undocumented.
     */
    @Test
    @DisplayName("every soulhome: tag named by a shipped archetype has a glossary page")
    void everyReferencedSoulhomeTagIsDocumented()
    {
        Set<String> documented = TagDocs.shipped().stream().map(TagDocs.Tag::path).collect(Collectors.toSet());

        for (ArchetypeDefinition archetype : ArchetypeDocs.shipped())
        {
            for (String tag : referencedTags(archetype))
            {
                if (tag.startsWith("soulhome:"))
                {
                    assertTrue(
                            documented.contains(tag.substring("soulhome:".length())),
                            archetype.id() + " names " + tag + ", which has no glossary page");
                }
            }
        }
    }

    private static Set<String> referencedTags(ArchetypeDefinition archetype)
    {
        Set<String> tags = new HashSet<>();

        Stream.of(archetype.requirements().stream().map(ArchetypeDefinition.Requirement::match),
                        archetype.signals().stream().map(ArchetypeDefinition.Signal::match),
                        archetype.detractors().stream().map(ArchetypeDefinition.Signal::match))
                .flatMap(s -> s)
                .forEach(matcher -> tags.addAll(matcher.tags()));

        return tags;
    }

    @Test
    @DisplayName("every shipped soulhome: tag is anchored, and its entries are listed under it")
    void glossaryDocumentsEveryTag()
    {
        BookStuff.Category category = new BookStuff.Category("multiblocks", "desc", "soulhome:soul_lens");
        BookStuff.Entry glossary = PatchouliMultiblocks.tagsGlossary(category);

        List<TagDocs.Tag> tags = TagDocs.shipped();

        // one introduction page, then one or more per tag: a long tag - soulhome:machinery holds
        // every machine part Create ships - runs onto continuation pages rather than overrunning
        // one and being silently clipped
        assertTrue(glossary.pages.length >= tags.size() + 1,
                "expected at least " + (tags.size() + 1) + " pages, got " + glossary.pages.length);

        for (int index = 0; index < tags.size(); index++)
        {
            TagDocs.Tag tag = tags.get(index);

            int anchored = -1;

            for (int page = 0; page < glossary.pages.length; page++)
            {
                if (tag.path().equals(glossary.pages[page].anchor))
                {
                    anchored = page;
                    assertTrue(glossary.pages[page].text.contains(tag.id()),
                            tag.id() + "'s page does not show its own id");
                }
            }

            assertTrue(anchored >= 0, "no glossary page anchored on " + tag.path());

            // the tag's own run of pages: from its anchor up to the next tag's
            final String listing = listingFrom(glossary, anchored);

            for (String value : tag.allValues())
            {
                assertTrue(
                        listing.contains(PatchouliMultiblocks.readable(value))
                                || listing.contains(" more."),
                        tag.id() + "'s pages neither list " + value + " nor say how many were left out");
            }
        }
    }

    @Test
    @DisplayName("an entry that needs another mod is listed apart from one that always counts")
    void glossarySeparatesOptionalEntries()
    {
        // a block from a mod the player may not have is a different promise from one every game
        // has, and a glossary that blurs the two sends someone hunting for a block their install
        // does not contain
        BookStuff.Category category = new BookStuff.Category("multiblocks", "desc", "soulhome:soul_lens");
        BookStuff.Entry glossary = PatchouliMultiblocks.tagsGlossary(category);

        TagDocs.Tag arcane = TagDocs.shipped().stream()
                .filter(tag -> tag.id().equals("soulhome:arcane"))
                .findFirst()
                .orElseThrow();

        assertFalse(arcane.optionalValues().isEmpty(), "soulhome:arcane should carry optional entries");

        int anchored = -1;

        for (int page = 0; page < glossary.pages.length; page++)
        {
            if ("arcane".equals(glossary.pages[page].anchor))
            {
                anchored = page;
            }
        }

        assertTrue(anchored >= 0);
        assertTrue(listingFrom(glossary, anchored).contains("with the mod that adds them"),
                "the optional entries should be called out as needing a mod");
    }

    /** Everything from a tag's anchored page up to the next anchored page, as one string. */
    private static String listingFrom(BookStuff.Entry glossary, int anchored)
    {
        StringBuilder listing = new StringBuilder(glossary.pages[anchored].text);

        for (int page = anchored + 1; page < glossary.pages.length; page++)
        {
            if (glossary.pages[page].anchor != null && !glossary.pages[page].anchor.isEmpty())
            {
                break;
            }

            listing.append(glossary.pages[page].text);
        }

        return listing.toString();
    }

    @Test
    @DisplayName("readable() links a soulhome: tag to its glossary anchor, and leaves other tags plain")
    void readableLinksOnlySoulhomeTags()
    {
        String soulhome = PatchouliMultiblocks.readable("#soulhome:lighting");
        assertTrue(soulhome.contains("$(l:soulhome:multiblocks/tags#lighting)"), soulhome);
        assertTrue(soulhome.contains("$(/l)"), soulhome);

        String vanilla = PatchouliMultiblocks.readable("#minecraft:rails");
        assertFalse(vanilla.contains("$(l:"), vanilla);
        assertTrue(vanilla.contains("any rails"), vanilla);
    }
}
