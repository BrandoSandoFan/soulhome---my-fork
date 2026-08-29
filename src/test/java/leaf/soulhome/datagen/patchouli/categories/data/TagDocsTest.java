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
            assertFalse(tag.values().isEmpty(), tag.id() + " has no values");

            for (String value : tag.values())
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
