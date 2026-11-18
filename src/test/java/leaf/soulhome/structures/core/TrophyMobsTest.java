/*
 * File created ~ 18 - 11 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyMobsTest
{
    private static AwardedRoom room(String archetype, List<String> mobs)
    {
        return new AwardedRoom(archetype, 2, 60d, null, 0, null, List.of(), mobs);
    }

    @Test
    @DisplayName("mounted groups are the union across carried trophy rooms, never a sum")
    void unionAcrossRooms()
    {
        Set<String> groups = TrophyMobs.groupsOf(List.of(
                room(TrophyGrudges.TROPHY_ROOM_ARCHETYPE, List.of("zombie", "creeper")),
                room(TrophyGrudges.TROPHY_ROOM_ARCHETYPE, List.of("zombie"))));

        assertEquals(Set.of("zombie", "creeper"), groups);
    }

    @Test
    @DisplayName("a room that is not a trophy room contributes nothing")
    void otherRoomsIgnored()
    {
        assertTrue(TrophyMobs.groupsOf(List.of(room("soulhome:library", List.of("zombie")))).isEmpty());
    }

    @Test
    @DisplayName("a room stores its groups sorted, de-duplicated and without unknown ids")
    void normalised()
    {
        assertEquals(List.of("creeper", "zombie"),
                room(TrophyGrudges.TROPHY_ROOM_ARCHETYPE, List.of("zombie", "bogus", "creeper", "zombie")).mountedMobs());
    }

    @Test
    @DisplayName("every group has an entity tag, so a head always counts against something")
    void everyGroupHasATag() throws Exception
    {
        for (String group : TrophyMobs.GROUP_BY_HEAD.values())
        {
            Path tag = Path.of("src/main/resources/data/soulhome/tags/entity_types/trophy/" + group + ".json");
            assertTrue(Files.exists(tag), "missing entity tag for " + group);
            assertTrue(Files.readString(tag).contains("minecraft:"), group + " names no entity");
        }
    }
}
