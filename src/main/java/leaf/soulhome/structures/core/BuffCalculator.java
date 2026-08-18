/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a soulhome's classified rooms into the buffs its owner walks around with.
 *
 * <p>Three rules, applied in order:
 *
 * <ol>
 *   <li><b>Repeated rooms fall off.</b> The best library counts fully, the second half as much,
 *       the third a quarter, and past {@link BuffSettings#maxRoomsPerArchetype} not at all.
 *       Otherwise the strategy is a corridor of identical cupboards, which is the same
 *       stack-one-thing failure the scoring curve exists to prevent, one level up.</li>
 *   <li><b>Each archetype is capped at its own declared ceiling.</b> {@code max} in the archetype
 *       JSON is the most that archetype can ever be worth, however many rooms feed it.</li>
 *   <li><b>Every buff type is capped globally.</b> Two archetypes granting the same buff cannot
 *       between them exceed {@link BuffSettings#globalMaxMagnitude}.</li>
 * </ol>
 *
 * <p>Ambiguous and unclassified regions contribute nothing - they are not buffs a player has not
 * noticed, they are buffs a player has not earned yet, and the feedback work is what tells them
 * the difference.
 */
public final class BuffCalculator
{
    private BuffCalculator()
    {
    }

    public static SoulBuffSet compute(
            List<ClassificationResult> results,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings)
    {
        return computeFromAwarded(AwardedRoom.from(results), archetypes, settings);
    }

    /**
     * The same rules, starting from persisted results rather than a fresh classification pass.
     */
    public static SoulBuffSet computeFromAwarded(
            List<AwardedRoom> awarded,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings)
    {
        Map<String, ArchetypeDefinition> byId = new HashMap<>();

        for (ArchetypeDefinition archetype : archetypes)
        {
            byId.put(archetype.id(), archetype);
        }

        Map<String, Double> totals = new LinkedHashMap<>();

        for (Map.Entry<String, List<AwardedRoom>> group : groupByArchetype(awarded).entrySet())
        {
            ArchetypeDefinition archetype = byId.get(group.getKey());

            if (archetype == null)
            {
                // a datapack was reloaded between classification and this call
                continue;
            }

            accumulate(totals, archetype, group.getValue(), settings);
        }

        for (Map.Entry<String, Double> entry : totals.entrySet())
        {
            entry.setValue(Math.min(entry.getValue(), settings.globalMaxMagnitude()));
        }

        return SoulBuffSet.of(totals);
    }

    /**
     * Awarded rooms per archetype, best first - the order the falloff is applied in, so the room a
     * player is proudest of is the one that counts fully.
     */
    private static Map<String, List<AwardedRoom>> groupByArchetype(List<AwardedRoom> awarded)
    {
        Map<String, List<AwardedRoom>> grouped = new LinkedHashMap<>();

        for (AwardedRoom room : awarded)
        {
            grouped.computeIfAbsent(room.archetypeId(), id -> new ArrayList<>()).add(room);
        }

        for (List<AwardedRoom> rooms : grouped.values())
        {
            rooms.sort(Comparator.comparingDouble(AwardedRoom::score).reversed());
        }

        return grouped;
    }

    private static void accumulate(
            Map<String, Double> totals,
            ArchetypeDefinition archetype,
            List<AwardedRoom> awarded,
            BuffSettings settings)
    {
        final int contributing = Math.min(awarded.size(), settings.maxRoomsPerArchetype());

        // subtotal per buff type for this archetype alone, so its own 'max' can be applied before
        // anything is mixed in from elsewhere
        Map<String, Double> subtotals = new LinkedHashMap<>();

        // an archetype naming the same buff type twice is a datapack oddity rather than an error;
        // the tighter ceiling wins, and the type is still only capped once
        Map<String, Double> ceilings = new LinkedHashMap<>();

        for (int room = 0; room < contributing; room++)
        {
            final double falloff = Math.pow(settings.repeatedRoomFalloff(), room);
            final int tier = awarded.get(room).tier();

            for (ArchetypeDefinition.BuffSpec spec : archetype.buffs())
            {
                subtotals.merge(spec.type(), spec.magnitudeAt(tier) * falloff, Double::sum);
                ceilings.merge(spec.type(), spec.max(), Math::min);
            }
        }

        for (Map.Entry<String, Double> subtotal : subtotals.entrySet())
        {
            final double ceiling = ceilings.getOrDefault(subtotal.getKey(), Double.MAX_VALUE);
            totals.merge(subtotal.getKey(), Math.min(subtotal.getValue(), ceiling), Double::sum);
        }
    }
}
