/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.JsonOps;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.AttunementSettings;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.BuffSettings;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.RegionType;
import leaf.soulhome.structures.core.RoomBinding;
import leaf.soulhome.structures.core.RoomPool;
import leaf.soulhome.structures.core.SoulBuffTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape the Soul Anchor's screen, {@code /soulhome buffs} and the Soul Lens all read attunement
 * off (#151). Built on the server and sent as-is, so what it says and what the server would actually
 * do cannot drift apart - which only holds if it survives the round trip through its own codec.
 */
class AttunementReportTest
{
    private static final String LIBRARY = "soulhome:library";
    private static final String BULWARK = "soulhome:bulwark";
    private static final String XP = "soulhome:xp_gain";

    @Test
    @DisplayName("with attunement off the report is empty, so every surface that reads it draws nothing")
    void offIsEmpty()
    {
        assertSame(AttunementReport.EMPTY, AttunementReport.of(
                List.of(room(1, LIBRARY, 3, 100d)), List.of(), archetypes(),
                AttunementSettings.OFF, linear(), 0, true));
    }

    @Test
    @DisplayName("a bound room reports its pool and its grants; an unbound one reports what it would grant")
    void poolsAndGrants()
    {
        AttunementReport report = AttunementReport.of(
                List.of(room(1, LIBRARY, 3, 100d), room(2, BULWARK, 2, 60d)),
                List.of(new RoomBinding(1, LIBRARY, box(0, 0, 0, 6, 4, 6))),
                archetypes(), AttunementSettings.DEFAULTS, linear(), 0, true);

        assertEquals(1, report.passiveUsed());
        assertEquals(0, report.activeUsed());
        assertEquals(AttunementSettings.DEFAULT_BASE_PASSIVE_SLOTS, report.passiveSlots());
        assertEquals(AttunementSettings.DEFAULT_BASE_ACTIVE_SLOTS, report.activeSlots());

        AttunementReport.Room library = report.rooms().get(0);
        assertTrue(library.attuned());
        assertEquals(RoomPool.PASSIVE, library.roomPool());
        assertEquals(XP, library.grants().get(0).buffType());
        assertEquals(0.30d, library.grants().get(0).magnitude(), 1e-9);

        AttunementReport.Room bulwark = report.rooms().get(1);
        assertFalse(bulwark.attuned());
        assertEquals(RoomPool.ACTIVE, bulwark.roomPool(), "a room whose buff is pressed spends an active slot");
        assertEquals(List.of(bulwark), report.dormant());
    }

    @Test
    @DisplayName("a bound room that is no longer standing is still listed, and still holds its slot")
    void ghostsAreListed()
    {
        AttunementReport report = AttunementReport.of(
                List.of(),
                List.of(new RoomBinding(7, LIBRARY, box(0, 0, 0, 6, 4, 6))),
                archetypes(), AttunementSettings.DEFAULTS, linear(), 0, true);

        assertEquals(1, report.rooms().size());
        assertEquals(1, report.passiveUsed(), "the slot is spent until the player releases it");

        AttunementReport.Room ghost = report.rooms().get(0);
        assertTrue(ghost.attuned());
        assertFalse(ghost.present(), "and the screen can say so, rather than showing a slot spent on nothing");
        assertTrue(ghost.grants().isEmpty());

        // not in the dormant list: a room that is not standing is not something to attune instead
        assertTrue(report.dormant().isEmpty());
    }

    @Test
    @DisplayName("rank raises both pools")
    void rankRaisesSlots()
    {
        AttunementReport report = AttunementReport.of(
                List.of(), List.of(), archetypes(), AttunementSettings.DEFAULTS, linear(), 5, true);

        assertEquals(10, report.passiveSlots());
        assertEquals(7, report.activeSlots());
    }

    @Test
    @DisplayName("it survives the round trip through its own codec, ghosts and all")
    void roundTripsThroughTheCodec()
    {
        AttunementReport before = AttunementReport.of(
                List.of(room(1, LIBRARY, 3, 100d), room(2, BULWARK, 2, 60d)),
                List.of(new RoomBinding(1, LIBRARY, box(0, 0, 0, 6, 4, 6)), new RoomBinding(9, LIBRARY, null)),
                archetypes(), AttunementSettings.DEFAULTS, linear(), 2, true);

        AttunementReport after = AttunementReport.CODEC
                .parse(JsonOps.INSTANCE, AttunementReport.CODEC.encodeStart(JsonOps.INSTANCE, before).result().orElseThrow())
                .result()
                .orElseThrow();

        assertEquals(before, after);
    }

    private static RegionBounds box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
    {
        return new RegionBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AwardedRoom room(int roomId, String archetypeId, int tier, double score)
    {
        return new AwardedRoom(archetypeId, tier, score, null, roomId, box(0, 0, 0, 6, 4, 6));
    }

    private static Map<String, ArchetypeDefinition> archetypes()
    {
        Map<String, ArchetypeDefinition> byId = new LinkedHashMap<>();

        byId.put(LIBRARY, definition(LIBRARY, XP, 0.30d));
        byId.put(BULWARK, definition(BULWARK, SoulBuffTypes.AEGIS, 4d));

        return byId;
    }

    private static BuffSettings linear()
    {
        return new BuffSettings(0.5d, 3, 10d, Map.of(), BuffSettings.DEFAULT_TYPE_CAPS, 0d, 1d);
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
}
