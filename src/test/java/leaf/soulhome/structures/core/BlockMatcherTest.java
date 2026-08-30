/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockMatcherTest
{
    @Test
    @DisplayName("matches by block id and by tag")
    void matchesBlocksAndTags()
    {
        assertTrue(BlockMatcher.ofBlocks("minecraft:lectern").test(TestBlocks.LECTERN));
        assertFalse(BlockMatcher.ofBlocks("minecraft:lectern").test(TestBlocks.BOOKSHELF));

        assertTrue(BlockMatcher.ofTags("soulhome:bookshelves").test(TestBlocks.BOOKSHELF));
        assertFalse(BlockMatcher.ofTags("soulhome:bookshelves").test(TestBlocks.LECTERN));
    }

    @Test
    @DisplayName("a matcher succeeds if any of its entries match")
    void anyEntryMatches()
    {
        BlockMatcher matcher = new BlockMatcher(
                List.of("minecraft:lectern"), List.of("soulhome:bookshelves"));

        assertTrue(matcher.test(TestBlocks.LECTERN));
        assertTrue(matcher.test(TestBlocks.BOOKSHELF));
        assertFalse(matcher.test(TestBlocks.STONE));
    }

    @Test
    @DisplayName("ids are normalised, so datapack typos still work")
    void idsAreNormalised()
    {
        // a leading '#' on a tag is the habit players bring from recipe files
        assertTrue(new BlockMatcher(List.of(), List.of("#soulhome:bookshelves")).test(TestBlocks.BOOKSHELF));

        // an unqualified id means the minecraft namespace, as everywhere else in the game
        assertTrue(new BlockMatcher(List.of("lectern"), List.of()).test(TestBlocks.LECTERN));

        // and case and whitespace should not matter
        assertTrue(new BlockMatcher(List.of("  Minecraft:Lectern "), List.of()).test(TestBlocks.LECTERN));
    }

    @Test
    @DisplayName("duplicates and blanks are dropped")
    void duplicatesAreDropped()
    {
        BlockMatcher matcher = new BlockMatcher(
                List.of("minecraft:lectern", "lectern", "", "  "), List.of());

        assertEquals(List.of("minecraft:lectern"), matcher.blocks());
    }

    @Test
    @DisplayName("an empty matcher is reported rather than silently never matching")
    void emptyMatcherIsInvalid()
    {
        BlockMatcher empty = new BlockMatcher(List.of(), List.of());

        assertTrue(empty.isEmpty());
        assertFalse(empty.test(TestBlocks.BOOKSHELF));
        assertEquals(1, empty.validationErrors().size());
    }

    @Test
    @DisplayName("a malformed id is reported")
    void malformedIdIsReported()
    {
        assertFalse(new BlockMatcher(List.of("minecraft:oak:door"), List.of()).validationErrors().isEmpty());
        assertFalse(new BlockMatcher(List.of("minecraft:"), List.of()).validationErrors().isEmpty());
        assertTrue(new BlockMatcher(List.of("somemod:strange_block"), List.of()).validationErrors().isEmpty(),
                "a block from a mod that is not installed is legal, it simply never matches");
    }

    @Test
    @DisplayName("descriptions read the way players write them")
    void describeIsPlayerFacing()
    {
        assertEquals("#soulhome:bookshelves", BlockMatcher.ofTags("soulhome:bookshelves").describe());
        assertEquals("minecraft:chest or minecraft:barrel",
                BlockMatcher.ofBlocks("minecraft:chest", "minecraft:barrel").describe());
    }
}
