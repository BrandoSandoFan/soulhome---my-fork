/*
 * File created ~ 2 - 9 - 2026
 */

package leaf.soulhome.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #84: the client's copy of its own soulhome's box now carries the rank that produced it, so the
 * Soul Lens and the ascent command can label the firmament with the rank the server actually used
 * rather than guessing from the box's size.
 *
 * <p>#158 added ground to it. Ground and walls are different numbers on purpose, and the lens has
 * to be able to say both or the open verge between them reads as growth having failed.
 */
class SyncSoulBoundsMessageTest
{
    @Test
    void rankTravelsWithTheBox()
    {
        SyncSoulBoundsMessage message = new SyncSoulBoundsMessage("soulhome:soul", 3, 70, 88, 72, List.of(), 54, false);

        SyncSoulBoundsMessage.ClientSoulBounds.accept(message);

        assertEquals(3, SyncSoulBoundsMessage.ClientSoulBounds.forDimension("soulhome:soul").getRank());
    }

    @Test
    void groundTravelsBesideTheWalls()
    {
        SyncSoulBoundsMessage.ClientSoulBounds.accept(
                new SyncSoulBoundsMessage("soulhome:soul", 3, 70, 88, 72, List.of(), 54, true));

        SyncSoulBoundsMessage current = SyncSoulBoundsMessage.ClientSoulBounds.forDimension("soulhome:soul");

        assertEquals(72, current.getVergeHalfExtent());
        assertEquals(54, current.getGroundReach());
        assertTrue(current.isGrowing());
    }

    @Test
    void aBoxWithNoGroundKnownReportsNone()
    {
        assertEquals(0, SyncSoulBoundsMessage.INVALID.getGroundReach());
        assertFalse(SyncSoulBoundsMessage.INVALID.isGrowing());
    }

    @Test
    void unknownDimensionFallsBackToInvalid()
    {
        SyncSoulBoundsMessage.ClientSoulBounds.accept(
                new SyncSoulBoundsMessage("soulhome:soul", 3, 70, 88, 72, List.of(), 54, false));

        assertSame(SyncSoulBoundsMessage.INVALID,
                SyncSoulBoundsMessage.ClientSoulBounds.forDimension("minecraft:overworld"));
    }
}
