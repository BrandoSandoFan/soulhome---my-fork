/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories;

import leaf.soulhome.datagen.patchouli.categories.data.ArchetypeDocs;
import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.BlockMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
}
