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
 * @param bondContributions    bonds credited to this room (see the Soul Architecture epic, #140):
 *                             which other room it relates to, how, and what that was worth. Only
 *                             ever non-empty on an awarded room scored through
 *                             {@code ArchetypeClassifier#classify(List, RegionAdjacency)}; a room
 *                             scored on its own has none. Discords appear here too, with a negative
 *                             contribution.
 * @param missingBonds         bonds this archetype declares that were not earned, each with why -
 *                             the near miss is the useful half: "your Enchanting Room is 19 blocks
 *                             away; within 12 would count", or that no such room exists yet
 * @param bondCapped           whether bond credit hit {@code ScoringSettings#bondShareCap}
 * @param aspect               which aspect this room took of this archetype, and what came second -
 *                             the Aspects epic (#171). Null for an archetype declaring no aspects,
 *                             for a gated region, and for every region when {@code aspects.enabled}
 *                             is off, which is how "off is indistinguishable from before the epic"
 *                             holds at every surface that reads this record. <b>Nothing on it feeds
 *                             {@link #score}</b>: the aspect decides which buff the magnitude is
 *                             paid into and nothing else. See {@link AspectSelector}.
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
        boolean structuralCapped,
        List<BondContribution> bondContributions,
        List<BondContribution> missingBonds,
        boolean bondCapped,
        AspectSelection aspect)
{
    public ArchetypeScore
    {
        contributions = List.copyOf(contributions);
        missingSignals = List.copyOf(missingSignals);
        failedRequirements = List.copyOf(failedRequirements);
        structuralContributions = structuralContributions == null ? List.of() : List.copyOf(structuralContributions);
        missingStructures = missingStructures == null ? List.of() : List.copyOf(missingStructures);
        bondContributions = bondContributions == null ? List.of() : List.copyOf(bondContributions);
        missingBonds = missingBonds == null ? List.of() : List.copyOf(missingBonds);
    }

    /** A score with no bonds - every score before #140, and every score of a region on its own. */
    public ArchetypeScore(
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
        this(archetypeId, displayName, score, rawScore, diversityMultiplier, densityMultiplier, tier,
                scoreToNextTier, contributions, missingSignals, failedRequirements, ineligibleReason,
                structuralContributions, missingStructures, structuralCapped, List.of(), List.of(), false, null);
    }

    /** A score with bonds but no aspect - every score before #171, and most fixtures since. */
    public ArchetypeScore(
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
            boolean structuralCapped,
            List<BondContribution> bondContributions,
            List<BondContribution> missingBonds,
            boolean bondCapped)
    {
        this(archetypeId, displayName, score, rawScore, diversityMultiplier, densityMultiplier, tier,
                scoreToNextTier, contributions, missingSignals, failedRequirements, ineligibleReason,
                structuralContributions, missingStructures, structuralCapped, bondContributions,
                missingBonds, bondCapped, null);
    }

    /** Whether there is an aspect worth naming - see {@link #aspect}. */
    public boolean hasAspect()
    {
        return this.aspect != null;
    }

    /** The aspect this room took, or null when it took none. Never an id shown in prose (#103). */
    public String aspectId()
    {
        return this.aspect == null ? null : this.aspect.takenId();
    }

    /** Whether there is anything to say about bonds at all - a region with none says nothing. */
    public boolean hasBondsToReport()
    {
        return !this.bondContributions.isEmpty() || !this.missingBonds.isEmpty();
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
     * #soulhome:bookshelves, found 3".
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
     * One bond's contribution, credited or not - {@code contribution = weight × confidence}.
     *
     * @param otherArchetypeId  the archetype this room is bonded to
     * @param otherDisplayName  its translation key, for a report that names rooms, never ids
     * @param otherRegion       index into the scan's region list of the room the bond was graded
     *                          against - the best partner when several qualified - or {@code -1}
     *                          when no room of that archetype exists. The Soul Lens highlights it.
     * @param relationId        {@code "adjoins"}, {@code "near"}, ...
     * @param description       the relation in plain words with its numbers - see
     *                          {@link BondRelation#describe}
     * @param diagnostic        the graded relation's own reason - the gap and the threshold on a
     *                          miss, what was credited on a hit
     * @param discord           whether the weight is negative
     */
    public record BondContribution(
            String otherArchetypeId,
            String otherDisplayName,
            int otherRegion,
            String relationId,
            String description,
            String role,
            double weight,
            double confidence,
            double contribution,
            String diagnostic,
            boolean discord)
    {
        public boolean isCredited()
        {
            return this.confidence > 0d;
        }
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
