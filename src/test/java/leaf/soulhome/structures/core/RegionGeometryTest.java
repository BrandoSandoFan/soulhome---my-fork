/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionGeometryTest
{
    @Test
    @DisplayName("an empty builder produces the shared EMPTY instance")
    void emptyBuilderIsShared()
    {
        assertSame(RegionGeometry.EMPTY, RegionGeometry.builder(100).build());
    }

    @Test
    @DisplayName("cells come back in the order they were added")
    void cellsPreserveAddOrder()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(3, 0, 0, TestBlocks.LECTERN)
                .add(1, 0, 0, TestBlocks.BOOKSHELF)
                .add(2, 0, 0, TestBlocks.CHAIR)
                .build();

        assertEquals(3, geometry.size());
        assertEquals(3, geometry.cells().get(0).x());
        assertEquals(1, geometry.cells().get(1).x());
        assertEquals(2, geometry.cells().get(2).x());
    }

    @Test
    @DisplayName("a null signature is not indexed")
    void nullSignatureIsIgnored()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, null).build();

        assertTrue(geometry.isEmpty());
        assertSame(RegionGeometry.EMPTY, geometry);
    }

    @Test
    @DisplayName("cellsMatching returns exactly the cells whose signature satisfies the matcher")
    void cellsMatchingFiltersBySignature()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, TestBlocks.BOOKSHELF)
                .add(1, 0, 0, TestBlocks.LECTERN)
                .add(2, 0, 0, TestBlocks.BOOKSHELF)
                .build();

        List<RegionGeometry.Cell> shelves = geometry.cellsMatching(BlockMatcher.ofTags("minecraft:bookshelves"));

        assertEquals(2, shelves.size());
        assertTrue(shelves.stream().allMatch(cell -> cell.signature() == TestBlocks.BOOKSHELF));

        List<RegionGeometry.Cell> lecterns = geometry.cellsMatching(BlockMatcher.ofBlocks("minecraft:lectern"));
        assertEquals(1, lecterns.size());
        assertEquals(1, lecterns.get(0).x());
    }

    @Test
    @DisplayName("a matcher nothing satisfies returns an empty list, not null")
    void cellsMatchingWithNoHitsIsEmpty()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, TestBlocks.BOOKSHELF).build();

        assertTrue(geometry.cellsMatching(BlockMatcher.ofBlocks("minecraft:anvil")).isEmpty());
    }

    @Test
    @DisplayName("cellsMatching is memoized per matcher")
    void cellsMatchingIsMemoized()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, TestBlocks.BOOKSHELF)
                .add(1, 0, 0, TestBlocks.BOOKSHELF)
                .build();

        // a different object, equal by value - BlockMatcher is a record, so this exercises the
        // cache's use of equals/hashCode rather than reference identity on the key
        BlockMatcher first = BlockMatcher.ofTags("minecraft:bookshelves");
        BlockMatcher second = BlockMatcher.ofTags("minecraft:bookshelves");

        assertSame(geometry.cellsMatching(first), geometry.cellsMatching(second),
                "the second call should reuse the first call's list rather than recomputing it");
    }

    @Test
    @DisplayName("the index truncates past its cap rather than throwing")
    void truncatesRatherThanThrowing()
    {
        RegionGeometry geometry = RegionGeometry.builder(2)
                .add(0, 0, 0, TestBlocks.BOOKSHELF)
                .add(1, 0, 0, TestBlocks.BOOKSHELF)
                .add(2, 0, 0, TestBlocks.BOOKSHELF)
                .build();

        assertEquals(2, geometry.size(), "stops accepting cells once the cap is reached");
        assertTrue(geometry.isTruncated());
    }

    @Test
    @DisplayName("an index under its cap is not marked truncated")
    void underCapIsNotTruncated()
    {
        RegionGeometry geometry = RegionGeometry.builder(10).add(0, 0, 0, TestBlocks.BOOKSHELF).build();

        assertFalse(geometry.isTruncated());
    }

    // region clearance (#29)

    @Test
    @DisplayName("isBlocked is false everywhere when nothing was ever recorded as blocked")
    void isBlockedDefaultsToFalse()
    {
        RegionGeometry geometry = RegionGeometry.builder(10).add(0, 0, 0, TestBlocks.BOOKSHELF).build();

        assertFalse(geometry.isBlocked(0, 0, 0), "not tracked - absence must read as 'not blocked', not as a failure");
        assertFalse(geometry.isBlocked(5, 5, 5));
    }

    @Test
    @DisplayName("EMPTY is safe to query for blocked cells and bounds")
    void emptyIsSafeToQuery()
    {
        assertFalse(RegionGeometry.EMPTY.isBlocked(0, 0, 0));
        assertTrue(RegionGeometry.EMPTY.bounds().isEmpty());
    }

    @Test
    @DisplayName("a recorded blocked position is reported, and nowhere else is")
    void addBlockedIsRecorded()
    {
        RegionGeometry geometry = RegionGeometry.builder(10).addBlocked(1, 2, 3).build();

        assertTrue(geometry.isBlocked(1, 2, 3));
        assertFalse(geometry.isBlocked(1, 2, 4));
    }

    @Test
    @DisplayName("bounds is empty until the builder is given one, and present afterwards")
    void boundsIsOptional()
    {
        RegionGeometry withoutBounds = RegionGeometry.builder(10).add(0, 0, 0, TestBlocks.BOOKSHELF).build();
        assertTrue(withoutBounds.bounds().isEmpty());

        RegionBounds bounds = new RegionBounds(0, 0, 0, 4, 4, 4);
        RegionGeometry withBounds = RegionGeometry.builder(10).bounds(bounds).build();
        assertEquals(bounds, withBounds.bounds().orElseThrow());
    }

    // endregion
}
