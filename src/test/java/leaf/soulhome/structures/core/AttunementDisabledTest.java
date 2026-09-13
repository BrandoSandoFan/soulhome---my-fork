/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code attunement.enabled = false} means the mod as it was before the Attunement epic - #151's
 * first non-negotiable.
 *
 * <p>"There is a config switch" and "the switch actually restores the old behaviour everywhere" are
 * different claims, and only the second is worth anything. The property that makes the second one
 * hold here is that with the switch off no room is ever given an identity: a room with no identity
 * cannot be bound and writes nothing to the save. What the reporting surfaces then draw is pinned
 * next door, in {@code AttunementReportTest} - the report is Minecraft-shaped and cannot run here.
 */
class AttunementDisabledTest
{
    private static final String LIBRARY = "soulhome:library";
    private static final String XP = "soulhome:xp_gain";

    @Test
    @DisplayName("with the switch off every classified room is carried, in the order it was found")
    void everyRoomIsCarried()
    {
        List<AwardedRoom> rooms = rooms();

        assertEquals(rooms, AttunementBook.carried(rooms, List.of(), AttunementSettings.OFF));
    }

    @Test
    @DisplayName("a binding left over from when the switch was on grants nothing, and is not thrown away")
    void bindingsAreIgnoredRatherThanErased()
    {
        // a server that turns the switch off should hand its players every buff they had, and a
        // server that turns it back on should hand them the loadout they chose - so the bindings
        // sit on disk being ignored rather than being cleaned up
        List<AwardedRoom> rooms = AttunementBook.reconcile(
                List.of(), List.of(), rooms(), 1).rooms();

        List<RoomBinding> bindings = List.of(RoomBinding.of(rooms.get(0)));

        assertEquals(rooms, AttunementBook.carried(rooms, bindings, AttunementSettings.OFF));
        assertEquals(List.of(rooms.get(0)), AttunementBook.carried(rooms, bindings, AttunementSettings.DEFAULTS));
    }

    @Test
    @DisplayName("nothing about attunement is written to the save")
    void noIdentityIsSaved()
    {
        for (AwardedRoom room : AttunementBook.anonymise(rooms()))
        {
            assertFalse(room.hasIdentity(), "a room with no id writes no RoomId");
            assertNull(room.footprint(), "and no Footprint");
        }
    }

    @Test
    @DisplayName("the buffs are identical to what the same rooms granted before the epic")
    void buffsAreUnchanged()
    {
        List<AwardedRoom> rooms = rooms();

        final double off = BuffCalculator.computeFromAwarded(
                AttunementBook.carried(rooms, List.of(), AttunementSettings.OFF),
                List.of(library()), linear()).magnitude(XP);

        // what the calculator produced before attunement existed: the whole list, no filter
        final double before = BuffCalculator.computeFromAwarded(rooms, List.of(library()), linear()).magnitude(XP);

        assertEquals(before, off, 1e-9);
    }

    @Test
    @DisplayName("no slot is ever exceeded, so the one-off message never fires")
    void nothingEverExceeds()
    {
        List<AwardedRoom> many = new ArrayList<>();

        for (int index = 0; index < 40; index++)
        {
            many.add(new AwardedRoom(LIBRARY, 2, 60d));
        }

        assertFalse(AttunementBook.exceedsSlots(many, archetypes(), AttunementSettings.OFF, 5));
        assertTrue(AttunementBook.exceedsSlots(many, archetypes(), AttunementSettings.DEFAULTS, 5));
    }

    private static List<AwardedRoom> rooms()
    {
        return List.of(
                new AwardedRoom(LIBRARY, 3, 100d, null, 0, new RegionBounds(0, 0, 0, 6, 4, 6)),
                new AwardedRoom(LIBRARY, 1, 50d, null, 0, new RegionBounds(20, 0, 0, 26, 4, 6)));
    }

    private static Map<String, ArchetypeDefinition> archetypes()
    {
        Map<String, ArchetypeDefinition> byId = new LinkedHashMap<>();
        byId.put(LIBRARY, library());
        return byId;
    }

    private static BuffSettings linear()
    {
        return new BuffSettings(0.5d, 3, 1.0d, Map.of(), BuffSettings.DEFAULT_TYPE_CAPS, 0d, 1d);
    }

    private static ArchetypeDefinition library()
    {
        return new ArchetypeDefinition(
                LIBRARY,
                "archetype.soulhome.test",
                List.of(RegionType.ENCLOSED),
                1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(0d, 1), new ArchetypeDefinition.Tier(100d, 3)),
                List.of(new ArchetypeDefinition.BuffSpec(XP, 0d, 0.30d)),
                List.of());
    }
}
