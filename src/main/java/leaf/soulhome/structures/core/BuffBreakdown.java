/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's buffs, plus where each one came from.
 *
 * <p>{@link SoulBuffSet} is what effects read: buff type to magnitude, and nothing else, because
 * that is all an effect needs and every extra field would be paid for on every hit taken and every
 * orb picked up. But "you have +20% experience" is only half an answer - the other half is "from
 * the library in the north wing" - so the aggregation also emits this, which the feedback UX
 * reads and nothing on a hot path does.
 *
 * @param totals  the magnitudes actually granted, after every cap
 * @param sources what each archetype contributed, before the global ceiling was applied
 */
public record BuffBreakdown(SoulBuffSet totals, List<Source> sources)
{
    public static final BuffBreakdown EMPTY = new BuffBreakdown(SoulBuffSet.empty(), List.of());

    public BuffBreakdown
    {
        totals = totals == null ? SoulBuffSet.empty() : totals;
        sources = List.copyOf(sources);
    }

    /** Which rooms fed one buff type, largest contribution first. */
    public List<Source> sourcesOf(String buffType)
    {
        List<Source> matching = new ArrayList<>();

        for (Source source : this.sources)
        {
            if (source.buffType().equals(buffType))
            {
                matching.add(source);
            }
        }

        return matching;
    }

    /**
     * Whether the global ceiling is what is holding this buff type down. Worth saying out loud:
     * a player who builds a second library and sees no change deserves to know it is the cap and
     * not their build.
     */
    public boolean isCapped(String buffType)
    {
        double claimed = 0d;

        for (Source source : sourcesOf(buffType))
        {
            claimed += source.magnitude();
        }

        // a hair of tolerance, since these are sums of doubles rather than exact quantities
        return claimed - this.totals.magnitude(buffType) > 1.0e-6d;
    }

    /**
     * One archetype's contribution to one buff type.
     *
     * @param rooms    how many rooms of this archetype counted, after the repeat falloff cut off
     *                 the rest
     * @param bestTier the highest tier among those rooms
     */
    public record Source(
            String buffType,
            String archetypeId,
            String displayName,
            int rooms,
            int bestTier,
            double magnitude)
    {
    }
}
