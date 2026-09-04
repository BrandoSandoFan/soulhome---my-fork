/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories;

import leaf.soulhome.datagen.patchouli.categories.data.ArchetypeDocs;
import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import leaf.soulhome.datagen.patchouli.categories.data.FormDocs;
import leaf.soulhome.datagen.patchouli.categories.data.TagDocs;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.Form;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
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
            List<BookStuff.Page> pages = PatchouliMultiblocks.arrangementPages(archetype);

            if (archetype.structures().isEmpty())
            {
                assertTrue(pages.isEmpty(), archetype.id() + " has no forms, so should have no arrangement page");
            }
            else
            {
                assertFalse(pages.isEmpty(), archetype.id() + " has forms, so should document them");

                for (BookStuff.Page page : pages)
                {
                    assertFalse(page.text.isBlank(), archetype.id() + "'s arrangement page has no text");
                }
            }
        }
    }

    @Test
    @DisplayName("the arrangement pages say what every element the sentence names is made of")
    void arrangementPagesCarryTheirLegend()
    {
        for (ArchetypeDefinition archetype : ArchetypeDocs.shipped())
        {
            if (archetype.structures().isEmpty())
            {
                continue;
            }

            final String text = PatchouliMultiblocks.arrangementPages(archetype).stream()
                    .map(page -> page.text)
                    .collect(Collectors.joining(" "));

            for (Form form : archetype.structures())
            {
                for (String element : form.elements().keySet())
                {
                    // the sentence names the element; somewhere on these pages has to say which
                    // blocks that is, or the page is telling a player to arrange something it
                    // never identifies - the defect this test exists for
                    assertTrue(
                            text.contains(FormDocs.label(element) + ": "),
                            archetype.id() + " names '" + element + "' without ever saying what it is");
                }
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

        assertTrue(PatchouliMultiblocks.arrangementPages(noForms).isEmpty());
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

    /**
     * The three rooms written for Iron's Spells and Create - see {@code CLAUDE.md} - must never
     * be readable on an install that lacks those mods. Their book entries used to share the same
     * "you have entered your soul" advancement as every other room, so the page showed up for
     * everyone regardless; each now has to be pinned to its own {@code soulhome:main/<archetype>}
     * advancement, which {@code ClassifiedRoomTrigger} can only ever fire once that archetype has
     * actually been classified - impossible without the blocks that mod adds.
     */
    @Test
    @DisplayName("a room that only another mod can build is gated behind its own advancement, not the shared one")
    void modOnlyRoomsAreGatedBehindTheirOwnAdvancement()
    {
        Set<String> modDependent = Set.of("arcane_sanctum", "ritual_chamber", "workshop");

        BookStuff.Category category = new BookStuff.Category("multiblocks", "desc", "soulhome:soul_lens");

        for (ArchetypeDefinition archetype : ArchetypeDocs.shipped())
        {
            final String path = ArchetypeDocs.pathOf(archetype);
            final boolean needsAnotherMod = PatchouliMultiblocks.needsAnotherMod(archetype);

            if (modDependent.contains(path))
            {
                assertTrue(needsAnotherMod, path + " should be recognised as needing another mod");
            }

            BookStuff.Entry entry = PatchouliMultiblocks.entryFor(category, archetype, 0);

            if (needsAnotherMod)
            {
                assertTrue(entry.advancement.equals("soulhome:main/" + path),
                        path + " can never be classified without another mod, so its page must be "
                                + "gated behind soulhome:main/" + path + ", not " + entry.advancement);
            }
            else
            {
                assertTrue(entry.advancement.equals("soulhome:main/entered_soul_dimension"),
                        path + " should be readable as soon as you have entered your soul");
            }
        }
    }

    /**
     * A requirement matcher that also accepts a vanilla block, or a tag with a vanilla member,
     * is not mod-only - only every alternative failing at once should count.
     */
    @Test
    @DisplayName("a requirement with any vanilla alternative does not count as needing another mod")
    void mixedRequirementIsNotModOnly()
    {
        ArchetypeDefinition mixed = new ArchetypeDefinition(
                "soulhome:test_mixed", "archetype.soulhome.test", List.of(), 1,
                List.of(new ArchetypeDefinition.Requirement(
                        BlockMatcher.ofBlocks("modthatdoesnotexist:thing", "minecraft:lectern"), 1, 0)),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:torch"), 1.0, "light", 16)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1.0, 1)),
                List.of(),
                List.of());

        assertFalse(PatchouliMultiblocks.needsAnotherMod(mixed));
    }
}
