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
 * @param structuralContributions structural forms (see {@code Form}, the structural considerations
 *                             epic #25) that scored above zero, best first. Empty for every gated
 *                             region - forms are not evaluated at all once a region is gated, since
 *                             there is no point paying for geometry on one that failed
 *                             {@code min_volume} - and, today, for every shipped archetype: none of
 *                             them declare any forms yet, that being the whole of #34
 * @param missingStructures    structural forms that scored exactly zero - "arranged like this and
 *                             it would count" is the answer a player needs, the structural sibling
 *                             of {@code missingSignals}
 * @param structuralCapped     whether structural credit hit {@code ScoringSettings#structuralShareCap}
 *                             and was held back - a player who keeps arranging a room and sees the
 *                             score stop moving deserves to know why rather than concluding the mod
 *                             is broken. Always {@code false} for a gated region: nothing is credited
 *                             far enough to be capped.
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
        String ineligibleReason,
        List<StructureContribution> structuralContributions,
        List<StructureContribution> missingStructures,
        boolean structuralCapped)
{
    public ArchetypeScore
    {
        contributions = List.copyOf(contributions);
        missingSignals = List.copyOf(missingSignals);
        failedRequirements = List.copyOf(failedRequirements);
        structuralContributions = structuralContributions == null ? List.of() : List.copyOf(structuralContributions);
        missingStructures = missingStructures == null ? List.of() : List.copyOf(missingStructures);
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

    /**
     * One structural form's contribution: {@code contribution = weight × confidence}, plus the
     * evaluated clause tree beneath it. "Arrangement: 0.4" tells a player nothing they can act on;
     * the tree tells them what to build next - see {@link ClauseEvaluation}.
     *
     * @param name   the form's own name, from its {@code Form}
     * @param role   grouping label, feeds the diversity multiplier once {@code confidence} clears
     *               {@code ScoringSettings#structuralRoleThreshold}
     */
    public record StructureContribution(
            String name,
            String role,
            double weight,
            double confidence,
            double contribution,
            ClauseEvaluation root)
    {
    }

    /**
     * One clause's evaluated result, as part of a {@link StructureContribution}'s tree - a leaf's
     * own graded confidence and diagnostic, or a composite node's combined confidence together with
     * every child that fed it.
     *
     * @param typeId       {@code "all"}, {@code "any"}, or the leaf's own clause id
     * @param description  {@code FormClause#describe()} - human-readable, for the report
     * @param confidence   this node's own confidence: a leaf's graded result, an {@code all}'s
     *                     weighted mean, or an {@code any}'s best child
     * @param diagnostic   a short reason, already resolved for a composite node - an {@code any}
     *                     carries its winning child's diagnostic, not all of them
     * @param children     empty for a leaf
     * @param selectedChild for an {@code any} node, the index into {@code children} of the
     *                      alternative that won; {@code -1} for anything else. The player who ringed
     *                      their rails in ice should be told that is what was credited, not left
     *                      guessing which of three readings applied.
     */
    public record ClauseEvaluation(
            String typeId,
            String description,
            double confidence,
            String diagnostic,
            List<ClauseEvaluation> children,
            int selectedChild)
    {
        public ClauseEvaluation
        {
            children = List.copyOf(children);
        }
    }
}
