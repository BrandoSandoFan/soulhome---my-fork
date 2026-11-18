/*
 * File created ~ 18 - 11 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a mob head on a trophy room's wall is worth against the mob it came from (#305).
 *
 * <p>The head block maps to a short <i>group</i> id here, and the group maps to the entities it
 * counts against in data ({@code data/soulhome/tags/entity_type/trophy/<group>.json}), so a
 * datapack can widen "zombie" to a modded zombie without a Java change. Only the block-to-group half
 * lives in code, because it has to be read by the classifier side, which never touches a registry.
 *
 * <p>This is deliberately a sibling of {@link TrophyGrudges}, not part of it: the grudge is
 * per-player and needs a captured owner, a mob head needs neither - the room's own blocks already say
 * which heads are mounted. The <i>amount</i> is the room's ordinary buff magnitude; this only decides
 * <i>against whom</i> it applies.
 */
public final class TrophyMobs
{
    /** Head block id to the group it counts against. Player heads are the grudge's, not this. */
    public static final Map<String, String> GROUP_BY_HEAD = groups();

    private TrophyMobs()
    {
    }

    private static Map<String, String> groups()
    {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("minecraft:zombie_head", "zombie");
        map.put("minecraft:skeleton_skull", "skeleton");
        map.put("minecraft:creeper_head", "creeper");
        map.put("minecraft:piglin_head", "piglin");
        map.put("minecraft:wither_skeleton_skull", "wither_skeleton");
        map.put("minecraft:dragon_head", "dragon");
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * The groups a player's carried trophy rooms hold a head for. A union, not a sum: two zombie
     * heads, or two rooms each with one, are still "zombies" - the amount comes from the buff, and
     * stacking it per head would make the cheapest trophy the strongest.
     */
    public static Set<String> groupsOf(List<AwardedRoom> carried)
    {
        Set<String> groups = new TreeSet<>();

        for (AwardedRoom room : carried)
        {
            if (TrophyGrudges.TROPHY_ROOM_ARCHETYPE.equals(room.archetypeId()))
            {
                groups.addAll(room.mountedMobs());
            }
        }

        return groups;
    }

    /** Sorted, de-duplicated and stripped of anything this mod does not know - what a room stores. */
    public static List<String> normalise(List<String> groups)
    {
        if (groups == null)
        {
            return List.of();
        }

        return groups.stream()
                .filter(GROUP_BY_HEAD.values()::contains)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
