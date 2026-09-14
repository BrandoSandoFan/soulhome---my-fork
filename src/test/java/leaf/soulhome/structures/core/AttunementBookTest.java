/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules #152 asks for, pinned.
 *
 * <p>Every case here is written as the edit a player actually makes - add a bookshelf, knock the
 * wall out, pull the whole thing down and build it again - rather than as a box arithmetic problem,
 * because the thing being defended is the player's loadout surviving their own building.
 */
class AttunementBookTest
{
    private static final String LIBRARY = "soulhome:library";
    private static final String WORKSHOP = "soulhome:workshop";
    private static final String BULWARK = "soulhome:bulwark";

    @Nested
    @DisplayName("room identity across scans")
    class Identity
    {
        @Test
        @DisplayName("a room seen for the first time is given a serial")
        void firstSightingIsNumbered()
        {
            AttunementBook.Reconciliation first = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1);

            assertEquals(1, first.rooms().get(0).roomId());
            assertEquals(2, first.nextRoomId());
        }

        @Test
        @DisplayName("adding a bookshelf does not change which room it is")
        void editingDoesNotReassign()
        {
            // the same box, different contents: the footprint is what identity is carried on, and
            // the contents are exactly what a region's own hash would have changed for
            assertKeepsIdentity(box(0, 0, 0, 6, 4, 6), box(0, 0, 0, 6, 4, 6));
        }

        @Test
        @DisplayName("extending a room by a wing does not change which room it is")
        void extendingDoesNotReassign()
        {
            assertKeepsIdentity(box(0, 0, 0, 6, 4, 6), box(0, 0, 0, 10, 4, 6));
        }

        @Test
        @DisplayName("rebuilding a wall one block out does not change which room it is")
        void rebuildingAWallDoesNotReassign()
        {
            assertKeepsIdentity(box(0, 0, 0, 6, 4, 6), box(-1, 0, 0, 6, 4, 6));
        }

        @Test
        @DisplayName("two rooms sharing a wall are never confused for one another")
        void neighboursStayApart()
        {
            AttunementBook.Reconciliation first = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1);

            // built alongside, sharing the wall at x=6 - a one-cell-thick overlap, nowhere near half
            AttunementBook.Reconciliation second = AttunementBook.reconcile(
                    first.rooms(), List.of(),
                    List.of(room(WORKSHOP, box(6, 0, 0, 12, 4, 6))), first.nextRoomId());

            assertNotEquals(first.rooms().get(0).roomId(), second.rooms().get(0).roomId());
        }

        @Test
        @DisplayName("a room converted to another kind is still the same room")
        void archetypeIsNotIdentity()
        {
            AttunementBook.Reconciliation first = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1);

            AttunementBook.Reconciliation second = AttunementBook.reconcile(
                    first.rooms(), List.of(),
                    List.of(room(WORKSHOP, box(0, 0, 0, 6, 4, 6))), first.nextRoomId());

            assertEquals(first.rooms().get(0).roomId(), second.rooms().get(0).roomId());
        }

        @Test
        @DisplayName("splitting a room in two: the larger half keeps the identity")
        void splitKeepsTheLargerHalf()
        {
            AttunementBook.Reconciliation first = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 20, 4, 6))), 1);

            final int original = first.rooms().get(0).roomId();

            AttunementBook.Reconciliation second = AttunementBook.reconcile(
                    first.rooms(), List.of(),
                    List.of(
                            room(LIBRARY, box(14, 0, 0, 20, 4, 6)),
                            room(LIBRARY, box(0, 0, 0, 12, 4, 6))),
                    first.nextRoomId());

            assertEquals(original, second.rooms().get(1).roomId(), "the bigger half is still that room");
            assertNotEquals(original, second.rooms().get(0).roomId());
        }

        @Test
        @DisplayName("the same soulhome reconciles the same way whatever order the scan walked it in")
        void orderIndependent()
        {
            List<AwardedRoom> previous = AttunementBook.reconcile(
                    List.of(), List.of(),
                    List.of(
                            room(LIBRARY, box(0, 0, 0, 6, 4, 6)),
                            room(WORKSHOP, box(20, 0, 0, 26, 4, 6))),
                    1).rooms();

            List<AwardedRoom> fresh = List.of(
                    room(LIBRARY, box(0, 0, 0, 6, 4, 6)),
                    room(WORKSHOP, box(20, 0, 0, 26, 4, 6)));

            List<AwardedRoom> reversed = new ArrayList<>(fresh);
            Collections.reverse(reversed);

            Map<RegionBounds, Integer> forward = idsByFootprint(
                    AttunementBook.reconcile(previous, List.of(), fresh, 3).rooms());
            Map<RegionBounds, Integer> backward = idsByFootprint(
                    AttunementBook.reconcile(previous, List.of(), reversed, 3).rooms());

            assertEquals(forward, backward);
        }

        private void assertKeepsIdentity(RegionBounds before, RegionBounds after)
        {
            AttunementBook.Reconciliation first = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, before)), 1);

            AttunementBook.Reconciliation second = AttunementBook.reconcile(
                    first.rooms(), List.of(), List.of(room(LIBRARY, after)), first.nextRoomId());

            assertEquals(first.rooms().get(0).roomId(), second.rooms().get(0).roomId());
            assertEquals(first.nextRoomId(), second.nextRoomId(), "nothing new was numbered");
        }
    }

    @Nested
    @DisplayName("a binding outlives its room")
    class Ghosts
    {
        @Test
        @DisplayName("demolishing an attuned room leaves it bound, and rebuilding restores it")
        void demolishAndRebuild()
        {
            final RegionBounds where = box(0, 0, 0, 6, 4, 6);

            AttunementBook.Reconciliation built = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, where)), 1);

            List<RoomBinding> bindings = new ArrayList<>(
                    List.of(RoomBinding.of(built.rooms().get(0))));

            // pulled down: nothing classifies any more
            AttunementBook.Reconciliation gone = AttunementBook.reconcile(
                    built.rooms(), bindings, List.of(), built.nextRoomId());

            assertEquals(1, gone.bindings().size(), "the binding is not dropped with the room");
            assertTrue(AttunementBook.carried(gone.rooms(), gone.bindings(), AttunementSettings.DEFAULTS).isEmpty());

            // built again on the same spot
            AttunementBook.Reconciliation rebuilt = AttunementBook.reconcile(
                    gone.rooms(), gone.bindings(), List.of(room(LIBRARY, where)), gone.nextRoomId());

            assertEquals(built.rooms().get(0).roomId(), rebuilt.rooms().get(0).roomId());
            assertEquals(
                    1,
                    AttunementBook.carried(rebuilt.rooms(), rebuilt.bindings(), AttunementSettings.DEFAULTS).size(),
                    "the rebuild is carried again without the player re-binding it");
        }

        @Test
        @DisplayName("a ghost still holds its slot")
        void ghostsHoldSlots()
        {
            AttunementBook.Reconciliation built = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1);

            List<RoomBinding> bindings = new ArrayList<>(List.of(RoomBinding.of(built.rooms().get(0))));

            AttunementBook.Reconciliation gone = AttunementBook.reconcile(
                    built.rooms(), bindings, List.of(), built.nextRoomId());

            assertEquals(
                    1,
                    AttunementBook.slotsUsed(gone.bindings(), gone.rooms(), archetypes())
                            .get(RoomPool.PASSIVE));
        }
    }

    @Nested
    @DisplayName("slots")
    class Slots
    {
        @Test
        @DisplayName("an active room spends an active slot and a passive room a passive one")
        void poolsAreSeparate()
        {
            AttunementBook.Reconciliation scan = AttunementBook.reconcile(
                    List.of(), List.of(),
                    List.of(
                            room(LIBRARY, box(0, 0, 0, 6, 4, 6)),
                            room(BULWARK, box(20, 0, 0, 26, 4, 6))),
                    1);

            List<RoomBinding> bindings = new ArrayList<>();

            for (AwardedRoom room : scan.rooms())
            {
                assertEquals(
                        AttunementBook.BindResult.BOUND,
                        AttunementBook.apply(
                                bindings, scan.rooms(), archetypes(), AttunementSettings.DEFAULTS, 0,
                                room.roomId(), true));
            }

            Map<RoomPool, Integer> used = AttunementBook.slotsUsed(bindings, scan.rooms(), archetypes());

            assertEquals(1, used.get(RoomPool.PASSIVE));
            assertEquals(1, used.get(RoomPool.ACTIVE));
        }

        @Test
        @DisplayName("binding past the limit is refused, and changes nothing")
        void refusedPastTheLimit()
        {
            final AttunementSettings tight = new AttunementSettings(true, 1, 0, 1, 0);

            List<AwardedRoom> rooms = AttunementBook.reconcile(
                    List.of(), List.of(),
                    List.of(
                            room(LIBRARY, box(0, 0, 0, 6, 4, 6)),
                            room(LIBRARY, box(20, 0, 0, 26, 4, 6))),
                    1).rooms();

            List<RoomBinding> bindings = new ArrayList<>();

            assertEquals(
                    AttunementBook.BindResult.BOUND,
                    AttunementBook.apply(bindings, rooms, archetypes(), tight, 0, rooms.get(0).roomId(), true));

            assertEquals(
                    AttunementBook.BindResult.NO_SLOTS,
                    AttunementBook.apply(bindings, rooms, archetypes(), tight, 0, rooms.get(1).roomId(), true));

            assertEquals(1, bindings.size());
        }

        @Test
        @DisplayName("a room id nobody has ever classified binds nothing")
        void forgedIdsChangeNothing()
        {
            List<RoomBinding> bindings = new ArrayList<>();

            assertEquals(
                    AttunementBook.BindResult.NO_SUCH_ROOM,
                    AttunementBook.apply(
                            bindings, List.of(), archetypes(), AttunementSettings.DEFAULTS, 0, 9999, true));

            assertTrue(bindings.isEmpty());
        }

        @Test
        @DisplayName("rank grants a slot in each pool")
        void rankGrantsSlots()
        {
            final AttunementSettings settings = AttunementSettings.DEFAULTS;

            assertEquals(5, settings.slotsFor(RoomPool.PASSIVE, 0));
            assertEquals(2, settings.slotsFor(RoomPool.ACTIVE, 0));
            assertEquals(10, settings.slotsFor(RoomPool.PASSIVE, 5));
            assertEquals(7, settings.slotsFor(RoomPool.ACTIVE, 5));
            assertEquals(7, settings.totalSlots(0));
            assertEquals(17, settings.totalSlots(5));
        }
    }

    @Nested
    @DisplayName("what a player carries")
    class Carried
    {
        @Test
        @DisplayName("with attunement off every classified room is carried, in the order it was found")
        void offCarriesEverything()
        {
            List<AwardedRoom> rooms = List.of(
                    room(LIBRARY, box(0, 0, 0, 6, 4, 6)),
                    room(BULWARK, box(20, 0, 0, 26, 4, 6)));

            assertEquals(rooms, AttunementBook.carried(rooms, List.of(), AttunementSettings.OFF));
        }

        @Test
        @DisplayName("with attunement on only bound rooms are carried")
        void onCarriesOnlyTheBound()
        {
            List<AwardedRoom> rooms = AttunementBook.reconcile(
                    List.of(), List.of(),
                    List.of(
                            room(LIBRARY, box(0, 0, 0, 6, 4, 6)),
                            room(BULWARK, box(20, 0, 0, 26, 4, 6))),
                    1).rooms();

            List<RoomBinding> bindings = List.of(RoomBinding.of(rooms.get(1)));

            assertEquals(
                    List.of(rooms.get(1)),
                    AttunementBook.carried(rooms, bindings, AttunementSettings.DEFAULTS));
        }

        @Test
        @DisplayName("attuning your second-best library grants what your best one would have")
        void falloffRanksTheAttunedSubset()
        {
            // the whole point of #153's falloff rule: the subset is ranked against itself, so a
            // player who spends their one slot on the lesser room is not also charged the falloff
            // for the better one they did not bring
            List<AwardedRoom> rooms = AttunementBook.reconcile(
                    List.of(), List.of(),
                    List.of(
                            scored(LIBRARY, 3, 100d, box(0, 0, 0, 6, 4, 6)),
                            scored(LIBRARY, 1, 50d, box(20, 0, 0, 26, 4, 6))),
                    1).rooms();

            SoulBuffSet lesserAlone = BuffCalculator.computeFromAwarded(
                    AttunementBook.carried(rooms, List.of(RoomBinding.of(rooms.get(1))), AttunementSettings.DEFAULTS),
                    List.of(libraryArchetype()), linear());

            SoulBuffSet asIfBest = BuffCalculator.computeFromAwarded(
                    List.of(scored(LIBRARY, 1, 50d, null)), List.of(libraryArchetype()), linear());

            assertEquals(
                    asIfBest.magnitude("soulhome:xp_gain"),
                    lesserAlone.magnitude("soulhome:xp_gain"),
                    1e-9);
        }
    }

    @Nested
    @DisplayName("a free slot fills itself")
    class AutoFill
    {
        @Test
        @DisplayName("a soul under its limit carries exactly what it carried before the epic landed")
        void underTheLimitNothingChanges()
        {
            // the update-day case. Five rooms, five passive slots, nothing bound: a player who logs
            // in must find every buff they went to bed with
            List<AwardedRoom> fresh = new ArrayList<>();

            for (int index = 0; index < 5; index++)
            {
                fresh.add(scored(LIBRARY, 2, 50d + index, box(index * 20, 0, 0, index * 20 + 6, 4, 6)));
            }

            AttunementBook.Reconciliation scan = AttunementBook.reconcile(List.of(), List.of(), fresh, 1);
            List<RoomBinding> bindings = new ArrayList<>();

            assertTrue(AttunementBook.autoFill(
                    bindings, scan.rooms(), scan.newlyNumbered(), archetypes(), AttunementSettings.DEFAULTS, 0));

            assertEquals(
                    scan.rooms(),
                    AttunementBook.carried(scan.rooms(), bindings, AttunementSettings.DEFAULTS));
        }

        @Test
        @DisplayName("a soul over its limit keeps its best rooms")
        void overTheLimitKeepsTheBest()
        {
            final AttunementSettings tight = new AttunementSettings(true, 2, 0, 0, 0);

            List<AwardedRoom> fresh = List.of(
                    scored(LIBRARY, 1, 20d, box(0, 0, 0, 6, 4, 6)),
                    scored(LIBRARY, 3, 90d, box(20, 0, 0, 26, 4, 6)),
                    scored(LIBRARY, 2, 55d, box(40, 0, 0, 46, 4, 6)));

            AttunementBook.Reconciliation scan = AttunementBook.reconcile(List.of(), List.of(), fresh, 1);
            List<RoomBinding> bindings = new ArrayList<>();

            AttunementBook.autoFill(bindings, scan.rooms(), scan.newlyNumbered(), archetypes(), tight, 0);

            assertEquals(
                    List.of(scan.rooms().get(1), scan.rooms().get(2)),
                    AttunementBook.carried(scan.rooms(), bindings, tight));
        }

        @Test
        @DisplayName("a room the player released stays released, even with the slot standing empty")
        void releasingIsRespected()
        {
            // the one decision the whole epic exists to ask for. Quietly re-binding what a player
            // just let go of would be the system overruling it
            AttunementBook.Reconciliation first = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1);

            List<RoomBinding> bindings = new ArrayList<>();

            AttunementBook.autoFill(
                    bindings, first.rooms(), first.newlyNumbered(), archetypes(), AttunementSettings.DEFAULTS, 0);

            assertEquals(1, bindings.size());

            AttunementBook.apply(
                    bindings, first.rooms(), archetypes(), AttunementSettings.DEFAULTS, 0,
                    first.rooms().get(0).roomId(), false);

            assertTrue(bindings.isEmpty());

            // a later scan finds the same room, matched rather than newly numbered, so nothing
            // reaches for it
            AttunementBook.Reconciliation second = AttunementBook.reconcile(
                    first.rooms(), bindings, List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), first.nextRoomId());

            AttunementBook.autoFill(
                    bindings, second.rooms(), second.newlyNumbered(), archetypes(), AttunementSettings.DEFAULTS, 0);

            assertTrue(bindings.isEmpty(), "the release stands");
        }

        @Test
        @DisplayName("with attunement off nothing is bound at all")
        void offBindsNothing()
        {
            AttunementBook.Reconciliation scan = AttunementBook.reconcile(
                    List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1);

            List<RoomBinding> bindings = new ArrayList<>();

            assertFalse(AttunementBook.autoFill(
                    bindings, scan.rooms(), scan.newlyNumbered(), archetypes(), AttunementSettings.OFF, 0));

            assertTrue(bindings.isEmpty());
        }
    }

    @Test
    @DisplayName("a soulhome with attunement off writes no identities at all")
    void anonymiseStripsEverything()
    {
        List<AwardedRoom> rooms = AttunementBook.reconcile(
                List.of(), List.of(), List.of(room(LIBRARY, box(0, 0, 0, 6, 4, 6))), 1).rooms();

        AwardedRoom stripped = AttunementBook.anonymise(rooms).get(0);

        assertFalse(stripped.hasIdentity());
        assertEquals(null, stripped.footprint());
        assertEquals(rooms.get(0).archetypeId(), stripped.archetypeId());
        assertEquals(rooms.get(0).score(), stripped.score(), 1e-9);
    }

    // region fixtures

    private static Map<RegionBounds, Integer> idsByFootprint(List<AwardedRoom> rooms)
    {
        Map<RegionBounds, Integer> ids = new LinkedHashMap<>();

        for (AwardedRoom room : rooms)
        {
            ids.put(room.footprint(), room.roomId());
        }

        return ids;
    }

    private static RegionBounds box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
    {
        return new RegionBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AwardedRoom room(String archetypeId, RegionBounds footprint)
    {
        return scored(archetypeId, 2, 60d, footprint);
    }

    private static AwardedRoom scored(String archetypeId, int tier, double score, RegionBounds footprint)
    {
        return new AwardedRoom(archetypeId, tier, score, null, 0, footprint);
    }

    /** One passive archetype and one active one, which is all any pool question needs. */
    private static Map<String, ArchetypeDefinition> archetypes()
    {
        Map<String, ArchetypeDefinition> byId = new LinkedHashMap<>();

        byId.put(LIBRARY, libraryArchetype());
        byId.put(WORKSHOP, definition(WORKSHOP, "soulhome:reach", 0.3d));
        byId.put(BULWARK, definition(BULWARK, SoulBuffTypes.AEGIS, 4d));

        return byId;
    }

    private static ArchetypeDefinition libraryArchetype()
    {
        return definition(LIBRARY, "soulhome:xp_gain", 0.30d);
    }

    private static BuffSettings linear()
    {
        return new BuffSettings(0.5d, 3, 1.0d, Map.of(), BuffSettings.DEFAULT_TYPE_CAPS, 0d, 1d);
    }

    private static ArchetypeDefinition definition(String id, String buffType, double max)
    {
        return new ArchetypeDefinition(
                id,
                "archetype.soulhome.test",
                List.of(RegionType.ENCLOSED),
                1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(0d, 1), new ArchetypeDefinition.Tier(100d, 3)),
                List.of(new ArchetypeDefinition.BuffSpec(buffType, 0d, max)),
                List.of());
    }

    // endregion
}
