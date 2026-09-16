/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwardedRoomTest
{
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("mounted heads are sorted by uuid regardless of construction order, for determinism")
    void mountedHeadsAreSortedByUuid()
    {
        AwardedRoom bobFirst = new AwardedRoom(
                "soulhome:trophy_room", 2, 60d, null, 0, null,
                List.of(new HeadOwner(BOB, "Bob"), new HeadOwner(ALICE, "Alice")));

        assertEquals(List.of(ALICE, BOB),
                bobFirst.mountedHeads().stream().map(HeadOwner::id).toList());
    }

    @Test
    @DisplayName("a room built with no mounted heads reports having none")
    void noMountedHeadsByDefault()
    {
        AwardedRoom room = new AwardedRoom("soulhome:library", 2, 60d);

        assertFalse(room.hasMountedHeads());
        assertTrue(room.mountedHeads().isEmpty());
    }

    @Test
    @DisplayName("withRoomId and anonymised both carry mounted heads through unchanged")
    void mountedHeadsSurviveIdentityOperations()
    {
        AwardedRoom room = new AwardedRoom(
                "soulhome:trophy_room", 2, 60d, null, 0, null, List.of(new HeadOwner(ALICE, "Alice")));

        assertEquals(room.mountedHeads(), room.withRoomId(5).mountedHeads());
        assertEquals(room.mountedHeads(), room.anonymised().mountedHeads());
    }
}
