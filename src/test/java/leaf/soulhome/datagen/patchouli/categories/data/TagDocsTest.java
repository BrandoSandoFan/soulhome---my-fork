/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #49: the glossary is only worth generating if it reads the same files the classifier actually
 * matches against.
 */
class TagDocsTest
{
    @Test
    @DisplayName("every shipped soulhome: tag reads at least one value, and none blank")
    void shippedTagsHaveValues()
    {
        List<TagDocs.Tag> tags = TagDocs.shipped();

        assertFalse(tags.isEmpty());

        for (TagDocs.Tag tag : tags)
        {
            assertTrue(tag.id().startsWith("soulhome:"), tag.id() + " is not a soulhome: tag");

            // allValues, not values: soulhome:machinery is entirely made of entries that need
            // another mod installed, which is a tag with something in it and nothing to show in
            // a vanilla game - not an empty tag
            assertFalse(tag.allValues().isEmpty(), tag.id() + " has no values");

            for (String value : tag.allValues())
            {
                assertFalse(value.isBlank(), tag.id() + " has a blank value");
            }
        }
    }

    @Test
    @DisplayName("known tags come back with the ids they were shipped under")
    void knownTagsArePresent()
    {
        List<String> ids = TagDocs.shipped().stream().map(TagDocs.Tag::id).toList();

        assertTrue(ids.contains("soulhome:lighting"));
        assertTrue(ids.contains("soulhome:reagents"));
        assertTrue(ids.contains("soulhome:furnishing"));
        assertTrue(ids.contains("soulhome:armament"));
        assertTrue(ids.contains("soulhome:arcane"));
        assertTrue(ids.contains("soulhome:machinery"));
    }

    @Test
    @DisplayName("an entry another mod supplies is read as optional, not as one every game has")
    void optionalEntriesAreReadApart()
    {
        // {"id": ..., "required": false} is how a block from a mod this one does not depend on is
        // added to a tag without the whole tag being dropped on a server that lacks it. Reading
        // one as an ordinary entry would have the glossary promise blocks the player cannot place.
        TagDocs.Tag machinery = TagDocs.shipped().stream()
                .filter(tag -> tag.id().equals("soulhome:machinery"))
                .findFirst()
                .orElseThrow();

        assertTrue(machinery.values().isEmpty(),
                "every machinery entry comes from another mod, so none of them is unconditional");
        assertFalse(machinery.optionalValues().isEmpty(), "and the optional ones should have been read");

        TagDocs.Tag arcane = TagDocs.shipped().stream()
                .filter(tag -> tag.id().equals("soulhome:arcane"))
                .findFirst()
                .orElseThrow();

        assertTrue(arcane.values().contains("minecraft:obsidian"), "vanilla entries stay unconditional");
        assertFalse(arcane.optionalValues().isEmpty(), "and the modded ones are read as optional");
    }

    @Test
    @DisplayName("soulhome:lighting includes the redstone lamp fixed by #45")
    void lightingIncludesRedstoneLamp()
    {
        TagDocs.Tag lighting = TagDocs.shipped().stream()
                .filter(tag -> tag.id().equals("soulhome:lighting"))
                .findFirst()
                .orElseThrow();

        assertTrue(lighting.values().contains("minecraft:redstone_lamp"));
    }
}
