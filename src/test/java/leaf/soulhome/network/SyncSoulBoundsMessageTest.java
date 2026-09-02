/*
 * File created ~ 2 - 9 - 2026
 */

package leaf.soulhome.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * #84: the client's copy of its own soulhome's box now carries the rank that produced it, so the
 * Soul Lens and the ascent command can label the firmament with the rank the server actually used
 * rather than guessing from the box's size.
 */
class SyncSoulBoundsMessageTest
{
    @Test
    void rankTravelsWithTheBox()
    {
        SyncSoulBoundsMessage message = new SyncSoulBoundsMessage("soulhome:soul", 3, 70, 88, 72, List.of());

        SyncSoulBoundsMessage.ClientSoulBounds.accept(message);

        assertEquals(3, SyncSoulBoundsMessage.ClientSoulBounds.forDimension("soulhome:soul").getRank());
    }

    @Test
    void unknownDimensionFallsBackToInvalid()
    {
        SyncSoulBoundsMessage.ClientSoulBounds.accept(
                new SyncSoulBoundsMessage("soulhome:soul", 3, 70, 88, 72, List.of()));

        assertSame(SyncSoulBoundsMessage.INVALID,
                SyncSoulBoundsMessage.ClientSoulBounds.forDimension("minecraft:overworld"));
    }
}
