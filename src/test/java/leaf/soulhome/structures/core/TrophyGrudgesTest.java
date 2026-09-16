/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyGrudgesTest
{
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("a trophy room's mounted head grants a targeted grudge, tier 1 to tier 3")
    void tierScalesTheFraction()
    {
        AwardedRoom tier1 = room(1, ALICE);
        AwardedRoom tier3 = room(3, ALICE);

        assertEquals(0.25d, TrophyGrudges.compute(List.of(tier1)).get(ALICE), 1e-9);
        assertEquals(0.5d, TrophyGrudges.compute(List.of(tier3)).get(ALICE), 1e-9);
    }

    @Test
    @DisplayName("only the trophy room archetype grants a grudge")
    void onlyTrophyRoomGrants()
    {
        AwardedRoom notATrophyRoom = new AwardedRoom(
                "soulhome:library", 3, 90d, null, 0, null, List.of(new HeadOwner(ALICE, "Alice")));

        assertTrue(TrophyGrudges.compute(List.of(notATrophyRoom)).isEmpty());
    }

    @Test
    @DisplayName("a room with no mounted heads grants nothing")
    void noHeadsNoGrudge()
    {
        AwardedRoom empty = new AwardedRoom(TrophyGrudges.TROPHY_ROOM_ARCHETYPE, 2, 60d, null, 0, null, List.of());

        assertTrue(TrophyGrudges.compute(List.of(empty)).isEmpty());
    }

    @Test
    @DisplayName("the same player's head in two rooms takes the stronger grudge, not both added together")
    void strongerGrudgeWinsAcrossRooms()
    {
        List<AwardedRoom> rooms = List.of(room(1, ALICE), room(3, ALICE));

        assertEquals(0.5d, TrophyGrudges.compute(rooms).get(ALICE), 1e-9);
    }

    @Test
    @DisplayName("two different mounted players each get their own grudge")
    void multipleOwnersEachGetTheirOwn()
    {
        AwardedRoom bothMounted = new AwardedRoom(
                TrophyGrudges.TROPHY_ROOM_ARCHETYPE, 2, 60d, null, 0, null,
                List.of(new HeadOwner(ALICE, "Alice"), new HeadOwner(BOB, "Bob")));

        Map<UUID, Double> grudges = TrophyGrudges.compute(List.of(bothMounted));

        assertEquals(0.375d, grudges.get(ALICE), 1e-9);
        assertEquals(0.375d, grudges.get(BOB), 1e-9);
    }

    private static AwardedRoom room(int tier, UUID mountedHead)
    {
        return new AwardedRoom(
                TrophyGrudges.TROPHY_ROOM_ARCHETYPE, tier, 60d, null, 0, null,
                List.of(new HeadOwner(mountedHead, "Someone")));
    }
}
