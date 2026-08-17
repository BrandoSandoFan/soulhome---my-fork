/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Decides what a region is, and how good an example of it it is.
 *
 * <h2>Why the score curve is sublinear</h2>
 *
 * The obvious scoring model - weight times count - has one optimal library: the largest possible
 * solid mass of bookshelves. That is precisely the outcome this whole design exists to avoid, so
 * counts go through {@code sqrt} before they are weighted. The sixty-fourth bookshelf is worth
 * about a twentieth of the first. Per-signal caps are the second, harder line of defence.
 *
 * <h2>Why variety beats volume</h2>
 *
 * Signals carry a {@code role}. A room that hits several distinct roles - books, seating, lighting,
 * a lectern - is multiplied up against a room that hits one role harder. Combined with the curve,
 * the cheapest way to raise a score is to add a kind of thing the room does not have yet, which is
 * also the thing that makes it look more like a real room.
 *
 * <h2>Why a region can decline to be anything</h2>
 *
 * A region is assigned to the argmax archetype only if it clears that archetype's first tier
 * <i>and</i> beats the runner-up by a margin. Otherwise it is {@code AMBIGUOUS}. A library and an
 * enchanting room share most of their signals, and quietly picking one at random is worse than
 * saying "this is halfway between two things" - as long as that gets surfaced, which is what the
 * breakdown on every {@link ArchetypeScore} is for.
 */
public final class ArchetypeClassifier
{
    private final List<ArchetypeDefinition> archetypes;
    private final ScoringSettings settings;

    public ArchetypeClassifier(Collection<ArchetypeDefinition> archetypes, ScoringSettings settings)
    {
        // stable order, so equal scores break ties the same way every scan
        List<ArchetypeDefinition> sorted = new ArrayList<>(archetypes);
        sorted.sort(Comparator.comparing(ArchetypeDefinition::id));

        this.archetypes = List.copyOf(sorted);
        this.settings = settings;
    }

    public ArchetypeClassifier(Collection<ArchetypeDefinition> archetypes)
    {
        this(archetypes, ScoringSettings.DEFAULTS);
    }

    public List<ArchetypeDefinition> archetypes()
    {
        return this.archetypes;
    }

    public List<ClassificationResult> classify(List<SoulRegion> regions)
    {
        List<ClassificationResult> results = new ArrayList<>(regions.size());

        for (SoulRegion region : regions)
        {
            results.add(classify(region));
        }

        return results;
    }

    public ClassificationResult classify(SoulRegion region)
    {
        List<ArchetypeScore> scores = new ArrayList<>(this.archetypes.size());

        for (ArchetypeDefinition archetype : this.archetypes)
        {
            scores.add(score(region, archetype));
        }

        scores.sort(Comparator
                .comparingDouble(ArchetypeScore::score).reversed()
                .thenComparing(ArchetypeScore::archetypeId));

        if (scores.isEmpty())
        {
            return new ClassificationResult(region, ClassificationResult.Status.UNCLASSIFIED, null, null, scores);
        }

        final ArchetypeScore best = scores.get(0);
        final ArchetypeScore runnerUp = scores.size() > 1 ? scores.get(1) : null;

        return new ClassificationResult(region, statusOf(best, runnerUp), best, runnerUp, scores);
    }

    private ClassificationResult.Status statusOf(ArchetypeScore best, ArchetypeScore runnerUp)
    {
        if (!best.qualifies())
        {
            return ClassificationResult.Status.UNCLASSIFIED;
        }

        // only a runner-up that would itself have qualified can make the result ambiguous;
        // being narrowly ahead of something that did not qualify is not a real contest
        if (runnerUp != null
                && runnerUp.qualifies()
                && best.score() < runnerUp.score() * this.settings.ambiguityMargin())
        {
            return ClassificationResult.Status.AMBIGUOUS;
        }

        return ClassificationResult.Status.CLASSIFIED;
    }

    /**
     * Score one region against one archetype, recording every intermediate value along the way.
     *
     * <p>A gated result - wrong region type, too small, a failed requirement - still comes back
     * with its full breakdown rather than as a bare zero, because "you have 3 bookshelves and need
     * 16" is the answer a player actually needs.
     */
    public ArchetypeScore score(SoulRegion region, ArchetypeDefinition archetype)
    {
        final BlockCounts blocks = region.allBlocks();

        final String ineligibleReason = ineligibilityOf(region, archetype);
        final List<ArchetypeScore.FailedRequirement> failedRequirements = failedRequirements(blocks, archetype);

        List<ArchetypeScore.SignalContribution> contributions = new ArrayList<>();
        List<ArchetypeScore.SignalContribution> missing = new ArrayList<>();
        Set<String> rolesPresent = new LinkedHashSet<>();

        double raw = 0d;
        int signalBlocks = 0;

        for (ArchetypeDefinition.Signal signal : archetype.signals())
        {
            final int count = blocks.count(signal.match());
            final int counted = Math.min(count, signal.cap());
            final double contribution = signal.weight() * curve(counted);

            ArchetypeScore.SignalContribution entry = new ArchetypeScore.SignalContribution(
                    signal.match().describe(), signal.role(), signal.weight(), count, counted, contribution);

            if (count == 0)
            {
                missing.add(entry);
                continue;
            }

            contributions.add(entry);
            rolesPresent.add(signal.role());
            signalBlocks += counted;
            raw += contribution;
        }

        for (ArchetypeDefinition.Signal detractor : archetype.detractors())
        {
            final int count = blocks.count(detractor.match());

            if (count == 0)
            {
                continue;
            }

            final int counted = Math.min(count, detractor.cap());
            final double contribution = detractor.weight() * curve(counted);

            // detractors do not count towards diversity or density - they are evidence that this
            // room is something else, not evidence that it is a well-appointed example of this
            contributions.add(new ArchetypeScore.SignalContribution(
                    detractor.match().describe(), detractor.role(), detractor.weight(), count, counted, contribution));

            raw += contribution;
        }

        contributions.sort(Comparator
                .comparingDouble(ArchetypeScore.SignalContribution::contribution).reversed()
                .thenComparing(ArchetypeScore.SignalContribution::description));
        missing.sort(Comparator
                .comparingDouble(ArchetypeScore.SignalContribution::weight).reversed()
                .thenComparing(ArchetypeScore.SignalContribution::description));

        final double diversity = diversityMultiplier(rolesPresent.size());
        final double density = densityMultiplier(signalBlocks, region.volume());
        final boolean gated = ineligibleReason != null || !failedRequirements.isEmpty();
        final double score = gated ? 0d : Math.max(0d, raw * diversity * density);

        return new ArchetypeScore(
                archetype.id(),
                archetype.displayName(),
                score,
                raw,
                diversity,
                density,
                archetype.tierFor(score),
                gated ? OptionalDouble.empty() : archetype.scoreToNextTier(score),
                contributions,
                missing,
                failedRequirements,
                ineligibleReason);
    }

    private static String ineligibilityOf(SoulRegion region, ArchetypeDefinition archetype)
    {
        if (!archetype.accepts(region.type()))
        {
            return "needs " + describeRegionTypes(archetype)
                    + ", but this region is " + region.type().getSerializedName();
        }

        if (region.volume() < archetype.minVolume())
        {
            return "needs a volume of at least " + archetype.minVolume()
                    + ", but this region is " + region.volume();
        }

        return null;
    }

    private static String describeRegionTypes(ArchetypeDefinition archetype)
    {
        List<String> names = new ArrayList<>();

        for (RegionType type : archetype.regionTypes())
        {
            names.add(type.getSerializedName());
        }

        return String.join(" or ", names);
    }

    private static List<ArchetypeScore.FailedRequirement> failedRequirements(BlockCounts blocks, ArchetypeDefinition archetype)
    {
        List<ArchetypeScore.FailedRequirement> failed = new ArrayList<>();

        for (ArchetypeDefinition.Requirement requirement : archetype.requirements())
        {
            final int found = blocks.count(requirement.match());

            if (found < requirement.minCount())
            {
                failed.add(new ArchetypeScore.FailedRequirement(
                        requirement.match().describe(), requirement.minCount(), found));
            }
        }

        return failed;
    }

    /**
     * The sublinear response curve. {@code sqrt} rather than {@code log2(1 + n)} because it is
     * gentler near the low counts players actually build at: four bookshelves are worth twice one,
     * not 2.3 times.
     */
    static double curve(int count)
    {
        return count <= 0 ? 0d : Math.sqrt(count);
    }

    private double diversityMultiplier(int distinctRoles)
    {
        return 1d + this.settings.diversityBonusPerRole() * Math.max(0, distinctRoles - 1);
    }

    /**
     * Penalises signal-sparse cathedrals. Soft rather than a hard cut, so a big airy room is worth
     * less than a well-appointed one without being disqualified for having high ceilings.
     */
    private double densityMultiplier(int signalBlocks, int volume)
    {
        if (volume <= 0 || this.settings.densityFloor() <= 0)
        {
            return 1d;
        }

        final double ratio = (double) signalBlocks / (double) volume;

        if (ratio >= this.settings.densityFloor())
        {
            return 1d;
        }

        return Math.max(this.settings.minDensityFactor(), ratio / this.settings.densityFloor());
    }
}
