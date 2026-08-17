/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.OptionalDouble;

/**
 * How well one region matched one archetype, and why.
 *
 * <p>The "why" is not optional extra. With a fixed multiblock, a player who gets no buff can
 * compare their build against the schematic in the book. With a fuzzy classifier there is nothing
 * to compare against, so an unexplained "no buff" is a dead end and the feature reads as broken.
 * Every intermediate value the feedback UX might want to show is therefore kept here, computed
 * once, rather than reconstructed after the fact.
 *
 * @param rawScore             weighted signal total before the multipliers
 * @param diversityMultiplier  bonus for hitting several distinct signal roles
 * @param densityMultiplier    penalty for being a mostly-empty box
 * @param contributions        signals that matched at least one block, best first
 * @param missingSignals       signals that matched nothing - directly the "what should I add"
 *                             answer
 * @param failedRequirements   hard gates that were not met; non-empty means the score is zero
 * @param ineligibleReason     why this archetype would not consider the region at all (wrong
 *                             region type, too small), or {@code null} if it did consider it
 */
public record ArchetypeScore(
        String archetypeId,
        String displayName,
        double score,
        double rawScore,
        double diversityMultiplier,
        double densityMultiplier,
        int tier,
        OptionalDouble scoreToNextTier,
        List<SignalContribution> contributions,
        List<SignalContribution> missingSignals,
        List<FailedRequirement> failedRequirements,
        String ineligibleReason)
{
    public ArchetypeScore
    {
        contributions = List.copyOf(contributions);
        missingSignals = List.copyOf(missingSignals);
        failedRequirements = List.copyOf(failedRequirements);
    }

    /** Whether the score was forced to zero rather than simply being low. */
    public boolean isGated()
    {
        return this.ineligibleReason != null || !this.failedRequirements.isEmpty();
    }

    public boolean qualifies()
    {
        return this.tier >= 1;
    }

    /**
     * One signal's contribution to the total.
     *
     * @param count        blocks matched
     * @param countedCount blocks that actually counted, after the signal's cap
     */
    public record SignalContribution(
            String description,
            String role,
            double weight,
            int count,
            int countedCount,
            double contribution)
    {
        /** Whether the cap is what is holding this signal back - worth telling the player. */
        public boolean isCapped()
        {
            return this.count > this.countedCount;
        }
    }

    /**
     * A hard gate the region failed, phrased so it can be shown verbatim: "needs 16 of
     * #minecraft:bookshelves, found 3".
     */
    public record FailedRequirement(String description, int required, int found)
    {
        @Override
        public String toString()
        {
            return "needs " + this.required + " of " + this.description + ", found " + this.found;
        }
    }
}
