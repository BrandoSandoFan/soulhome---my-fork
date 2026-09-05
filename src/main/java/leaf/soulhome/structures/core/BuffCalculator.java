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
 * <p>Five rules, applied in order:
 *
 * <ol>
 *   <li><b>Repeated rooms fall off.</b> The best library counts fully, the second half as much,
 *       the third a quarter, and past {@link BuffSettings#maxRoomsPerArchetype} not at all.
 *       Otherwise the strategy is a corridor of identical cupboards, which is the same
 *       stack-one-thing failure the scoring curve exists to prevent, one level up.</li>
 *   <li><b>Each archetype is capped at its own declared ceiling.</b> {@code max} in the archetype
 *       JSON is the most that archetype can ever be worth, however many rooms feed it.</li>
 *   <li><b>Each archetype is then scaled by its config multiplier</b>, so a pack can turn one
 *       archetype down without editing a file it does not own.</li>
 *   <li><b>Rank amplifies what is left</b> (#85), for every buff type
 *       {@link SoulBuffTypes#amplifiesWithRank} allows - {@code rankFactor = 1 + ascensionPerRank *
 *       rank}. An unascended soul always multiplies by exactly 1, so this rule is invisible until a
 *       player actually climbs.</li>
 *   <li><b>Every buff type is capped globally</b>, at a ceiling rank also raises
 *       (see {@link BuffSettings#capFor(String, int)}) - otherwise the players who climbed
 *       furthest, by building the most, would be exactly the ones already pinned at the old cap,
 *       and rank would amplify nothing for them. Two archetypes granting the same buff still cannot
 *       between them exceed that raised ceiling.</li>
 * </ol>
 *
 * <p>Ambiguous and unclassified regions contribute nothing - they are not buffs a player has not
 * noticed, they are buffs a player has not earned yet, and the feedback work is what tells them
 * the difference.
 *
 * <p>Every path here goes through {@link #explain}, which computes the totals and the "where did
 * this come from" attribution together. Working them out separately would let the number a player
 * is told differ from the number they are given, which is exactly the class of bug the feedback
 * work exists to prevent.
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
        return compute(results, archetypes, settings, 0);
    }

    /** As {@link #compute}, amplified for an ascended soul (#85) - see {@link BuffSettings#rankFactor}. */
    public static SoulBuffSet compute(
            List<ClassificationResult> results,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings,
            int rank)
    {
        return computeFromAwarded(AwardedRoom.from(results), archetypes, settings, rank);
    }

    /**
     * The same rules, starting from persisted results rather than a fresh classification pass.
     */
    public static SoulBuffSet computeFromAwarded(
            List<AwardedRoom> awarded,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings)
    {
        return computeFromAwarded(awarded, archetypes, settings, 0);
    }

    /** As {@link #computeFromAwarded}, amplified for an ascended soul (#85). */
    public static SoulBuffSet computeFromAwarded(
            List<AwardedRoom> awarded,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings,
            int rank)
    {
        return explain(awarded, archetypes, settings, rank).totals();
    }

    /**
     * The totals, and what each archetype contributed to them.
     */
    public static BuffBreakdown explain(
            List<AwardedRoom> awarded,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings)
    {
        return explain(awarded, archetypes, settings, 0);
    }

    /** As {@link #explain}, amplified for an ascended soul (#85). */
    public static BuffBreakdown explain(
            List<AwardedRoom> awarded,
            Collection<ArchetypeDefinition> archetypes,
            BuffSettings settings,
            int rank)
    {
        Map<String, ArchetypeDefinition> byId = new HashMap<>();

        for (ArchetypeDefinition archetype : archetypes)
        {
            byId.put(archetype.id(), archetype);
        }

        List<BuffBreakdown.Source> sources = new ArrayList<>();
        Map<String, Double> totals = new LinkedHashMap<>();

        for (Map.Entry<String, List<AwardedRoom>> group : groupByArchetype(awarded).entrySet())
        {
            ArchetypeDefinition archetype = byId.get(group.getKey());

            if (archetype == null)
            {
                // a datapack was reloaded between classification and this call
                continue;
            }

            accumulate(totals, sources, archetype, group.getValue(), settings, rank);
        }

        for (Map.Entry<String, Double> entry : totals.entrySet())
        {
            entry.setValue(Math.min(entry.getValue(), settings.capFor(entry.getKey(), rank)));
        }

        return new BuffBreakdown(SoulBuffSet.of(totals), sources);
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
            List<BuffBreakdown.Source> sources,
            ArchetypeDefinition archetype,
            List<AwardedRoom> awarded,
            BuffSettings settings,
            int rank)
    {
        final int contributing = Math.min(awarded.size(), settings.maxRoomsPerArchetype());
        final double multiplier = settings.multiplierFor(archetype.id());

        // subtotal per buff type for this archetype alone, so its own 'max' can be applied before
        // anything is mixed in from elsewhere
        Map<String, Double> subtotals = new LinkedHashMap<>();

        // an archetype naming the same buff type twice is a datapack oddity rather than an error;
        // the tighter ceiling wins, and the type is still only capped once
        Map<String, Double> ceilings = new LinkedHashMap<>();

        int bestTier = 0;

        for (int room = 0; room < contributing; room++)
        {
            final double falloff = Math.pow(settings.repeatedRoomFalloff(), room);
            final double score = awarded.get(room).score();

            bestTier = Math.max(bestTier, awarded.get(room).tier());

            for (ArchetypeDefinition.BuffSpec spec : archetype.buffs())
            {
                subtotals.merge(spec.type(), spec.magnitudeAt(score, archetype, settings) * falloff, Double::sum);
                ceilings.merge(spec.type(), spec.max(), Math::min);
            }
        }

        for (Map.Entry<String, Double> subtotal : subtotals.entrySet())
        {
            final String buffType = subtotal.getKey();
            final double ceiling = ceilings.getOrDefault(buffType, Double.MAX_VALUE);
            final double beforeRank = Math.min(subtotal.getValue(), ceiling) * multiplier;

            // rank amplification (#85): applied after the archetype's own max and the multiplier,
            // before the global type cap - and skipped for any buff type SoulBuffTypes says has
            // nowhere good to put the extra. Nothing is exempt today (#86 gave the last four
            // holdouts a soft ceiling to grow into instead), but a future buff with no such ceiling
            // has somewhere ready to declare it.
            final double granted = SoulBuffTypes.amplifiesWithRank(buffType)
                    ? beforeRank * settings.rankFactor(rank)
                    : beforeRank;

            if (granted <= 0d)
            {
                // an archetype multiplied to zero is switched off, not a source worth listing
                continue;
            }

            totals.merge(buffType, granted, Double::sum);
            sources.add(new BuffBreakdown.Source(
                    buffType,
                    archetype.id(),
                    archetype.displayName(),
                    contributing,
                    bestTier,
                    granted,
                    granted - beforeRank));
        }
    }
}
