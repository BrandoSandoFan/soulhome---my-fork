/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Optional;

/**
 * What one region turned out to be.
 *
 * @param best      the winning archetype, or the closest miss when nothing qualified. Never null
 *                  unless no archetypes are loaded at all.
 * @param runnerUp  the second-place archetype, kept because "it was nearly an enchanting room"
 *                  is the single most useful thing to tell a player whose room did not classify
 * @param allScores every archetype's score, best first
 */
public record ClassificationResult(
        SoulRegion region,
        Status status,
        ArchetypeScore best,
        ArchetypeScore runnerUp,
        List<ArchetypeScore> allScores)
{
    public ClassificationResult
    {
        allScores = List.copyOf(allScores);
    }

    public enum Status
    {
        /** Assigned to an archetype. */
        CLASSIFIED,

        /**
         * Two archetypes scored too close together to pick between. Surfaced to the player rather
         * than dropped - "I built a thing and nothing happened" is the failure mode that kills
         * this feature.
         */
        AMBIGUOUS,

        /** Nothing reached its first tier. */
        UNCLASSIFIED
    }

    /** The archetype this region counts as, if any. */
    public Optional<ArchetypeScore> awarded()
    {
        return this.status == Status.CLASSIFIED ? Optional.ofNullable(this.best) : Optional.empty();
    }

    public Optional<String> awardedArchetypeId()
    {
        return awarded().map(ArchetypeScore::archetypeId);
    }

    public int awardedTier()
    {
        return awarded().map(ArchetypeScore::tier).orElse(0);
    }
}
