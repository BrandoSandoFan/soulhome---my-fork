/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 *
 * <h2>Bonds are scored against the awards, and never re-run</h2>
 *
 * A bond (the Soul Architecture epic, #140) is declared between <i>archetypes</i> but can only be
 * graded once it is known <i>which region is which archetype</i> - and changing a room's score
 * could change which archetype it is awarded, since awards are decided on the best score with an
 * ambiguity margin. The naive loop is circular. So {@link #classify(List, RegionAdjacency)}
 * classifies every region on its own first, then scores each awarded room's bonds against the
 * other awards, then folds the credit back into that room's score. A room's archetype is decided
 * by what is in it and how it is arranged; bonds adjust what the room is <i>worth</i>, never what
 * it <i>is</i>. That keeps the pass finite, keeps it order-independent, and keeps a player from
 * finding that moving a chair changed what the room next door is called.
 *
 * <p>Mirroring how structural credit folds in, but capped as a share of what the room earned on
 * its own so a perfect floor plan of empty boxes is worth nothing:
 *
 * <pre>{@code
 * bondRaw   = Σ bond.weight × confidence(bond)          (positive bonds)
 * capped    = min(bondRaw, bondShareCap × (signalRaw + structuralCredited))
 * discord   = Σ bond.weight × confidence(bond)          (negative bonds, uncapped)
 * raw       = signalRaw + detractorTotal + structuralCredited + capped + discord
 * score     = max(0, raw × diversity × density)
 * }</pre>
 *
 * <p>Discords are not share-capped: a negative bond is evidence against, and capping it as a
 * share of the room's own signal would mean a bigger room shrugs off a hazard next door. What a
 * discord cannot do is un-award the room: a hearth beside a powder magazine can lose every tier
 * but its first, since what the room is was decided before bonds were looked at.
 *
 * <h2>An aspect is chosen, never scored</h2>
 *
 * Where a bond adjusts what a room is worth, an aspect (the Aspects epic, #171) adjusts neither
 * that nor what the room is. It decides only which buff the room's magnitude is paid into - an
 * archive or a scriptorium, both libraries, both at the same tier and the same score. So the
 * selection is made once the score is settled, from {@link AspectSelector}, and no value it
 * computes appears in any expression here that produces a score. If aspect support ever reaches a
 * magnitude, rule 3 of the epic has been broken and two well-built rooms start paying less than one.
 */
public final class ArchetypeClassifier
{
    private final List<ArchetypeDefinition> archetypes;
    private final Map<String, ArchetypeDefinition> byId;
    private final ScoringSettings settings;
    private final BondBook bonds;

    public ArchetypeClassifier(Collection<ArchetypeDefinition> archetypes, ScoringSettings settings)
    {
        this(archetypes, settings, BondBook.of(archetypes, BondRelationRegistry.BUILTIN));
    }

    /**
     * @param bonds the resolved bonds between these archetypes - a test with its own relation
     *              registry builds its own book; the game resolves against
     *              {@link BondRelationRegistry#BUILTIN}
     */
    public ArchetypeClassifier(Collection<ArchetypeDefinition> archetypes, ScoringSettings settings, BondBook bonds)
    {
        // stable order, so equal scores break ties the same way every scan
        List<ArchetypeDefinition> sorted = new ArrayList<>(archetypes);
        sorted.sort(Comparator.comparing(ArchetypeDefinition::id));

        Map<String, ArchetypeDefinition> byId = new HashMap<>();

        for (ArchetypeDefinition archetype : sorted)
        {
            byId.put(archetype.id(), archetype);
        }

        this.archetypes = List.copyOf(sorted);
        this.byId = Map.copyOf(byId);
        this.settings = settings;
        this.bonds = bonds;
    }

    public ArchetypeClassifier(Collection<ArchetypeDefinition> archetypes)
    {
        this(archetypes, ScoringSettings.DEFAULTS);
    }

    public List<ArchetypeDefinition> archetypes()
    {
        return this.archetypes;
    }

    public BondBook bonds()
    {
        return this.bonds;
    }

    /**
     * Every region on its own, with no relationships between them - what a caller without a
     * {@link RegionAdjacency} gets, and bond-free by construction.
     */
    public List<ClassificationResult> classify(List<SoulRegion> regions)
    {
        return classify(regions, RegionAdjacency.none(regions.size()));
    }

    /**
     * Every region, then every awarded room's bonds against the other awards - see the class
     * javadoc for why in that order and never again.
     *
     * @param adjacency how the regions relate, indexed as {@code regions} is - from
     *                  {@code RegionScanner#scanWithAdjacency}
     */
    public List<ClassificationResult> classify(List<SoulRegion> regions, RegionAdjacency adjacency)
    {
        List<Classified> unbonded = new ArrayList<>(regions.size());

        for (SoulRegion region : regions)
        {
            unbonded.add(classifyOnItsOwn(region));
        }

        List<ClassificationResult> results = new ArrayList<>(regions.size());

        for (int i = 0; i < unbonded.size(); i++)
        {
            results.add(withBonds(i, unbonded, adjacency));
        }

        return results;
    }

    public ClassificationResult classify(SoulRegion region)
    {
        return classifyOnItsOwn(region).result();
    }

    /** One region's result together with the working the bond pass needs to re-score its winner. */
    private record Classified(ClassificationResult result, Scored best)
    {
        String awardedId()
        {
            return this.result.awardedArchetypeId().orElse(null);
        }
    }

    private Classified classifyOnItsOwn(SoulRegion region)
    {
        List<Scored> scored = new ArrayList<>(this.archetypes.size());

        // shared across every archetype scored for this region: clauses are value records, and the
        // same clause - "seating surrounds the fire" - is commonly named by more than one
        // archetype's forms. Keyed on clause + element bindings, not the clause alone, since two
        // archetypes can name the same clause while binding its element names to different blocks.
        // See FormClauseEvaluator.
        Map<FormClauseEvaluator.MemoKey, FormResult> clauseMemo = new HashMap<>();

        for (ArchetypeDefinition archetype : this.archetypes)
        {
            scored.add(scoreParts(region, archetype, clauseMemo));
        }

        scored.sort(Comparator
                .comparingDouble((Scored entry) -> entry.score().score()).reversed()
                .thenComparing(entry -> entry.score().archetypeId()));

        List<ArchetypeScore> scores = new ArrayList<>(scored.size());

        for (Scored entry : scored)
        {
            scores.add(entry.score());
        }

        if (scores.isEmpty())
        {
            return new Classified(
                    new ClassificationResult(region, ClassificationResult.Status.UNCLASSIFIED, null, null, scores), null);
        }

        final ArchetypeScore best = scores.get(0);
        final ArchetypeScore runnerUp = scores.size() > 1 ? scores.get(1) : null;

        return new Classified(
                new ClassificationResult(region, statusOf(best, runnerUp), best, runnerUp, scores), scored.get(0));
    }

    // region bonds (#147)

    /**
     * Re-score one awarded room with its bonds, or hand back an unawarded one untouched. Reads
     * only the unbonded awards of the other regions, so the result for region {@code i} cannot
     * depend on the order the regions are visited in.
     */
    private ClassificationResult withBonds(int i, List<Classified> unbonded, RegionAdjacency adjacency)
    {
        final Classified own = unbonded.get(i);
        final String archetypeId = own.awardedId();

        if (archetypeId == null)
        {
            return own.result();
        }

        List<BondBook.Resolved> declared = this.bonds.bondsOf(archetypeId);

        if (declared.isEmpty())
        {
            return own.result();
        }

        List<ArchetypeScore.BondContribution> hits = new ArrayList<>();
        List<ArchetypeScore.BondContribution> misses = new ArrayList<>();

        double bondRaw = 0d;
        double discordRaw = 0d;
        Set<String> bondRoles = new LinkedHashSet<>();

        for (BondBook.Resolved bond : declared)
        {
            ArchetypeScore.BondContribution graded = gradeAgainstBest(i, bond, unbonded, adjacency);

            if (!graded.isCredited())
            {
                misses.add(graded);
                continue;
            }

            hits.add(graded);

            if (bond.isDiscord())
            {
                discordRaw += graded.contribution();
            }
            else
            {
                bondRaw += graded.contribution();

                if (graded.confidence() >= this.settings.structuralRoleThreshold())
                {
                    bondRoles.add(bond.role());
                }
            }
        }

        hits.sort(Comparator
                .comparingDouble(ArchetypeScore.BondContribution::contribution).reversed()
                .thenComparing(ArchetypeScore.BondContribution::otherArchetypeId));
        misses.sort(Comparator
                .comparingDouble(ArchetypeScore.BondContribution::weight).reversed()
                .thenComparing(ArchetypeScore.BondContribution::otherArchetypeId));

        final Scored parts = own.best();
        final double cap = this.settings.bondShareCap() * (parts.signalRaw() + parts.structuralCredited());
        final boolean capped = bondRaw > cap;

        ArchetypeScore before = own.result().best();
        ArchetypeDefinition archetype = this.byId.get(archetypeId);

        Set<String> roles = new LinkedHashSet<>(parts.roles());
        roles.addAll(bondRoles);

        final double raw = parts.rawWithoutBonds() + Math.min(bondRaw, cap) + discordRaw;
        final double diversity = diversityMultiplier(roles.size());
        double score = Math.max(0d, raw * diversity * parts.density());

        // a discord can cost a room every tier but its first: what the room is was decided before
        // bonds were looked at, and a hearth beside a powder magazine is still a hearth
        final double entry = archetype.tiers().isEmpty() ? 0d : archetype.tiers().get(0).minScore();
        score = Math.max(score, Math.min(before.score(), entry));

        ArchetypeScore bonded = new ArchetypeScore(
                before.archetypeId(),
                before.displayName(),
                score,
                raw,
                diversity,
                before.densityMultiplier(),
                archetype.tierFor(score),
                archetype.scoreToNextTier(score),
                before.contributions(),
                before.missingSignals(),
                before.failedRequirements(),
                before.ineligibleReason(),
                before.structuralContributions(),
                before.missingStructures(),
                before.structuralCapped(),
                hits,
                misses,
                capped,
                // carried through untouched: a bond says where a room stands, which is no evidence
                // about what it is for, and re-deciding the aspect here would make a room's buff
                // depend on the room next door
                before.aspect());

        // the award stands whatever the bonds did - only the winner's entry is replaced. The list
        // is left in its unbonded order so the runner-up is still the room's own runner-up.
        List<ArchetypeScore> scores = new ArrayList<>(own.result().allScores());

        for (int k = 0; k < scores.size(); k++)
        {
            if (scores.get(k).archetypeId().equals(archetypeId))
            {
                scores.set(k, bonded);
            }
        }

        return new ClassificationResult(
                own.result().region(), own.result().status(), bonded, own.result().runnerUp(), scores);
    }

    /**
     * Grade one bond against the best-graded room of the partner archetype, or against nothing
     * when no such room was awarded - which is still reported, since "you have no Enchanting
     * Room" is the answer a player needs.
     */
    private ArchetypeScore.BondContribution gradeAgainstBest(
            int self, BondBook.Resolved bond, List<Classified> unbonded, RegionAdjacency adjacency)
    {
        final ArchetypeDefinition other = this.byId.get(bond.other());
        final String otherName = other == null ? bond.other() : other.displayName();
        final String description = bond.relation().describe(bond.params());

        BondGrade best = null;
        int bestRegion = -1;

        for (int j = 0; j < unbonded.size(); j++)
        {
            if (j == self || !bond.other().equals(unbonded.get(j).awardedId()))
            {
                continue;
            }

            BondGrade grade = bond.relation().grade(self, j, adjacency, bond.params());

            // the best partner, and the first of equals in region order, so ties break the same
            // way every scan
            if (best == null || grade.confidence() > best.confidence())
            {
                best = grade;
                bestRegion = j;
            }
        }

        if (best == null)
        {
            final String why = other == null
                    ? "no such room is known here - it may need a mod that is not installed"
                    : "there is no such room in this soul yet";

            return new ArchetypeScore.BondContribution(
                    bond.other(), otherName, -1, bond.relation().id(), description, bond.role(),
                    bond.weight(), 0d, 0d, why, bond.isDiscord());
        }

        return new ArchetypeScore.BondContribution(
                bond.other(), otherName, bestRegion, bond.relation().id(), description, bond.role(),
                bond.weight(), best.confidence(), bond.weight() * best.confidence(), best.diagnostic(),
                bond.isDiscord());
    }

    // endregion

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
        return scoreParts(region, archetype, new HashMap<>()).score();
    }

    /**
     * A score and the working behind it, kept so the bond pass can add a term without
     * recomputing anything the first pass already knew.
     *
     * @param rawWithoutBonds     signal total, detractors and credited arrangement - everything
     *                            {@code raw} was before bonds
     * @param structuralCredited  the arrangement credit after the share cap
     * @param roles               every role that fed the diversity multiplier
     */
    private record Scored(
            ArchetypeScore score,
            double signalRaw,
            double structuralCredited,
            double rawWithoutBonds,
            Set<String> roles,
            double density)
    {
    }

    /**
     * @param clauseMemo shared across every archetype {@link #classify(SoulRegion)} scores for one
     *                   region, so a leaf clause named by more than one archetype's forms - with the
     *                   same element bindings - is only ever evaluated once. A caller going through
     *                   {@link #score(SoulRegion, ArchetypeDefinition)} directly gets a throwaway
     *                   memo instead - correct either way, just without the cross-archetype saving.
     */
    private Scored scoreParts(SoulRegion region, ArchetypeDefinition archetype, Map<FormClauseEvaluator.MemoKey, FormResult> clauseMemo)
    {
        final BlockCounts blocks = region.allBlocks();

        final String ineligibleReason = ineligibilityOf(region, archetype);
        final List<ArchetypeScore.FailedRequirement> failedRequirements = failedRequirements(region, archetype);
        final boolean gated = ineligibleReason != null || !failedRequirements.isEmpty();

        List<ArchetypeScore.SignalContribution> contributions = new ArrayList<>();
        List<ArchetypeScore.SignalContribution> missing = new ArrayList<>();
        Set<String> rolesPresent = new LinkedHashSet<>();

        double signalRaw = 0d;
        double signalBlocks = 0d;

        for (ArchetypeDefinition.Signal signal : archetype.signals())
        {
            // scored on the exact credit - half a shared wall is half a wall - and reported as
            // whole blocks, see BlockCounts#count
            final double count = blocks.credit(signal.match());
            final double counted = Math.min(count, signal.cap());
            final double contribution = signal.weight() * curve(counted);

            ArchetypeScore.SignalContribution entry = new ArchetypeScore.SignalContribution(
                    signal.match().describe(), signal.role(), signal.weight(),
                    BlockCounts.wholeBlocks(count), BlockCounts.wholeBlocks(counted), contribution);

            if (count <= 0d)
            {
                missing.add(entry);
                continue;
            }

            contributions.add(entry);
            rolesPresent.add(signal.role());
            signalBlocks += counted;
            signalRaw += contribution;
        }

        double raw = signalRaw;

        for (ArchetypeDefinition.Signal detractor : archetype.detractors())
        {
            final double count = blocks.credit(detractor.match());

            if (count <= 0d)
            {
                continue;
            }

            final double counted = Math.min(count, detractor.cap());
            final double contribution = detractor.weight() * curve(counted);

            // detractors do not count towards diversity or density - they are evidence that this
            // room is something else, not evidence that it is a well-appointed example of this
            contributions.add(new ArchetypeScore.SignalContribution(
                    detractor.match().describe(), detractor.role(), detractor.weight(),
                    BlockCounts.wholeBlocks(count), BlockCounts.wholeBlocks(counted), contribution));

            raw += contribution;
        }

        contributions.sort(Comparator
                .comparingDouble(ArchetypeScore.SignalContribution::contribution).reversed()
                .thenComparing(ArchetypeScore.SignalContribution::description));
        missing.sort(Comparator
                .comparingDouble(ArchetypeScore.SignalContribution::weight).reversed()
                .thenComparing(ArchetypeScore.SignalContribution::description));

        // structure is evidence, never a gate: a gated archetype still scores 0, but its forms are
        // not evaluated at all - no point paying for geometry on a region that failed min_volume
        List<ArchetypeScore.StructureContribution> structuralHits = List.of();
        List<ArchetypeScore.StructureContribution> structuralMisses = List.of();
        boolean structuralCapped = false;
        double structuralCredited = 0d;

        if (!gated && !archetype.structures().isEmpty())
        {
            List<ArchetypeScore.StructureContribution> hits = new ArrayList<>();
            List<ArchetypeScore.StructureContribution> misses = new ArrayList<>();
            double structuralRaw = 0d;

            for (Form form : archetype.structures())
            {
                ArchetypeScore.ClauseEvaluation rootEval =
                        FormClauseEvaluator.evaluate(form.root(), region.geometry(), form.elements(), clauseMemo);
                final double confidence = rootEval.confidence();
                final double contribution = form.weight() * confidence;

                ArchetypeScore.StructureContribution entry = new ArchetypeScore.StructureContribution(
                        form.name(), form.role(), form.weight(), confidence, contribution, rootEval);

                if (confidence <= 0d)
                {
                    misses.add(entry);
                    continue;
                }

                hits.add(entry);
                structuralRaw += contribution;

                // below the threshold, an accidental sliver of a match does not buy a full
                // diversity bonus for free
                if (confidence >= this.settings.structuralRoleThreshold())
                {
                    rolesPresent.add(form.role());
                }
            }

            hits.sort(Comparator
                    .comparingDouble(ArchetypeScore.StructureContribution::contribution).reversed()
                    .thenComparing(ArchetypeScore.StructureContribution::name));
            misses.sort(Comparator.comparing(ArchetypeScore.StructureContribution::name));

            structuralHits = hits;
            structuralMisses = misses;

            // proportional to signalRaw, not a constant: arrangement amplifies a real room and is
            // worth nothing in an empty one - a perfect ring of chairs around nothing scores nothing
            final double structuralCap = this.settings.structuralShareCap() * signalRaw;
            structuralCapped = structuralRaw > structuralCap;
            structuralCredited = Math.min(structuralRaw, structuralCap);
            raw += structuralCredited;
        }

        final double diversity = diversityMultiplier(rolesPresent.size());
        final double density = densityMultiplier(signalBlocks, region.volume());
        final double score = gated ? 0d : Math.max(0d, raw * diversity * density);

        // #171 rule 3, and the line the whole epic turns on: the aspect is chosen after the score
        // is settled and is never an input to it. Nothing below this point may read `selected`.
        // A gated region skips it for the same reason it skips forms - there is no point deciding
        // what a room is for when it is not a room
        final AspectSelection selected = gated || !this.settings.aspectsEnabled()
                ? null
                : AspectSelector.select(region, archetype, this.settings, clauseMemo);

        ArchetypeScore result = new ArchetypeScore(
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
                ineligibleReason,
                structuralHits,
                structuralMisses,
                structuralCapped,
                List.of(),
                List.of(),
                false,
                selected);

        return new Scored(result, signalRaw, structuralCredited, raw, Set.copyOf(rolesPresent), density);
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

    /**
     * @param region supplies both the block counts a requirement is checked against and, for a
     *               {@code minVolumeFraction} requirement, the {@link SoulRegion#volume()} that
     *               fraction is taken of - see {@link ArchetypeDefinition.Requirement}.
     */
    private static List<ArchetypeScore.FailedRequirement> failedRequirements(SoulRegion region, ArchetypeDefinition archetype)
    {
        final BlockCounts blocks = region.allBlocks();
        List<ArchetypeScore.FailedRequirement> failed = new ArrayList<>();

        for (ArchetypeDefinition.Requirement requirement : archetype.requirements())
        {
            // whole blocks, deliberately: a gate is a promise about what is there, and fifteen and
            // a half bookshelves do not clear "at least 16" any more than fifteen do
            final int found = blocks.count(requirement.match());
            final int threshold = effectiveThreshold(requirement, region.volume());

            if (found < threshold)
            {
                failed.add(new ArchetypeScore.FailedRequirement(
                        requirement.match().describe(), threshold, found));
            }
        }

        return failed;
    }

    private static int effectiveThreshold(ArchetypeDefinition.Requirement requirement, int volume)
    {
        if (requirement.minVolumeFraction() <= 0d)
        {
            return requirement.minCount();
        }

        final int fromVolume = (int) Math.ceil(requirement.minVolumeFraction() * volume);
        return Math.max(requirement.minCount(), fromVolume);
    }

    /**
     * The sublinear response curve. {@code sqrt} rather than {@code log2(1 + n)} because it is
     * gentler near the low counts players actually build at: four bookshelves are worth twice one,
     * not 2.3 times.
     */
    static double curve(double count)
    {
        return count <= 0d ? 0d : Math.sqrt(count);
    }

    private double diversityMultiplier(int distinctRoles)
    {
        return 1d + this.settings.diversityBonusPerRole() * Math.max(0, distinctRoles - 1);
    }

    /**
     * Penalises signal-sparse cathedrals. Soft rather than a hard cut, so a big airy room is worth
     * less than a well-appointed one without being disqualified for having high ceilings.
     */
    private double densityMultiplier(double signalBlocks, int volume)
    {
        if (volume <= 0 || this.settings.densityFloor() <= 0)
        {
            return 1d;
        }

        final double ratio = signalBlocks / (double) volume;

        if (ratio >= this.settings.densityFloor())
        {
            return 1d;
        }

        return Math.max(this.settings.minDensityFactor(), ratio / this.settings.densityFloor());
    }
}
