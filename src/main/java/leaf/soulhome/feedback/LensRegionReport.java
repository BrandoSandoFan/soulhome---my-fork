/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.AspectSelection;
import leaf.soulhome.structures.core.BuffBreakdown;
import leaf.soulhome.structures.core.ClassificationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One region, flattened to what the Soul Lens screen needs to explain it.
 *
 * <p>{@link RegionHighlight} carries only what the world outlines and corner labels need - a box
 * and a headline. The lens screen (#50) needs the reasoning behind that headline too: which
 * signals counted, which are missing, how the arrangement scored, and what the room is worth. That
 * is everything {@link ArchetypeScore} already holds, so this class does not recompute anything -
 * it walks the same score the report and the buff are built from, exactly as {@link SoulReport}
 * does, so the screen and the chat command can never disagree about what a room is worth.
 *
 * <p>Text here is plain, resolved strings rather than translation keys - the same choice
 * {@code ArchetypeScore.SignalContribution#description()} and {@code ClauseEvaluation#description()}
 * already made, since both are built from datapack-supplied names that have no lang key of their
 * own.
 */
public record LensRegionReport(
        int index,
        String status,
        String archetypeId,
        String displayName,
        int tier,
        double score,
        double scoreToNextTier,
        String runnerUpDisplayName,
        double runnerUpScore,
        boolean noArchetypes,
        List<Signal> matched,
        List<String> missing,
        List<Form> forms,
        List<BuffEntry> buffs,
        List<BondLine> bonds,
        AspectLine aspect)
{
    /** Sentinel for {@link #scoreToNextTier}: no next tier to reach, or nothing scored at all. */
    public static final double NO_NEXT_TIER = -1d;

    public static final Codec<LensRegionReport> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.fieldOf("index").forGetter(LensRegionReport::index),
                    Codec.STRING.fieldOf("status").forGetter(LensRegionReport::status),
                    Codec.STRING.optionalFieldOf("archetype", "").forGetter(LensRegionReport::archetypeId),
                    Codec.STRING.optionalFieldOf("display_name", "").forGetter(LensRegionReport::displayName),
                    Codec.INT.optionalFieldOf("tier", 0).forGetter(LensRegionReport::tier),
                    Codec.DOUBLE.optionalFieldOf("score", 0d).forGetter(LensRegionReport::score),
                    Codec.DOUBLE.optionalFieldOf("score_to_next_tier", NO_NEXT_TIER).forGetter(LensRegionReport::scoreToNextTier),
                    Codec.STRING.optionalFieldOf("runner_up_display_name", "").forGetter(LensRegionReport::runnerUpDisplayName),
                    Codec.DOUBLE.optionalFieldOf("runner_up_score", 0d).forGetter(LensRegionReport::runnerUpScore),
                    Codec.BOOL.optionalFieldOf("no_archetypes", false).forGetter(LensRegionReport::noArchetypes),
                    Signal.CODEC.listOf().optionalFieldOf("matched", List.of()).forGetter(LensRegionReport::matched),
                    Codec.STRING.listOf().optionalFieldOf("missing", List.of()).forGetter(LensRegionReport::missing),
                    Form.CODEC.listOf().optionalFieldOf("forms", List.of()).forGetter(LensRegionReport::forms),
                    BuffEntry.CODEC.listOf().optionalFieldOf("buffs", List.of()).forGetter(LensRegionReport::buffs),
                    BondLine.CODEC.listOf().optionalFieldOf("bonds", List.of()).forGetter(LensRegionReport::bonds),
                    // absent for a room with no aspect, which is every room when the switch is off
                    // and every room of an archetype that declares none (#176)
                    AspectLine.CODEC.optionalFieldOf("aspect")
                            .forGetter((LensRegionReport report) -> Optional.ofNullable(report.aspect())))
            .apply(instance, LensRegionReport::withOptionalAspect));

    public LensRegionReport
    {
        matched = List.copyOf(matched);
        missing = List.copyOf(missing);
        forms = List.copyOf(forms);
        buffs = List.copyOf(buffs);
        bonds = bonds == null ? List.of() : List.copyOf(bonds);
    }

    /** A report from before bonds existed, or of a region with none to speak of. */
    public LensRegionReport(
            int index,
            String status,
            String archetypeId,
            String displayName,
            int tier,
            double score,
            double scoreToNextTier,
            String runnerUpDisplayName,
            double runnerUpScore,
            boolean noArchetypes,
            List<Signal> matched,
            List<String> missing,
            List<Form> forms,
            List<BuffEntry> buffs)
    {
        this(index, status, archetypeId, displayName, tier, score, scoreToNextTier, runnerUpDisplayName,
                runnerUpScore, noArchetypes, matched, missing, forms, buffs, List.of());
    }

    /** A report from before aspects existed, or of a room that took none. */
    public LensRegionReport(
            int index,
            String status,
            String archetypeId,
            String displayName,
            int tier,
            double score,
            double scoreToNextTier,
            String runnerUpDisplayName,
            double runnerUpScore,
            boolean noArchetypes,
            List<Signal> matched,
            List<String> missing,
            List<Form> forms,
            List<BuffEntry> buffs,
            List<BondLine> bonds)
    {
        this(index, status, archetypeId, displayName, tier, score, scoreToNextTier, runnerUpDisplayName,
                runnerUpScore, noArchetypes, matched, missing, forms, buffs, bonds, null);
    }

    /** The codec's own constructor: an absent aspect field is a room that took none. */
    private static LensRegionReport withOptionalAspect(
            int index,
            String status,
            String archetypeId,
            String displayName,
            int tier,
            double score,
            double scoreToNextTier,
            String runnerUpDisplayName,
            double runnerUpScore,
            boolean noArchetypes,
            List<Signal> matched,
            List<String> missing,
            List<Form> forms,
            List<BuffEntry> buffs,
            List<BondLine> bonds,
            Optional<AspectLine> aspect)
    {
        return new LensRegionReport(
                index, status, archetypeId, displayName, tier, score, scoreToNextTier, runnerUpDisplayName,
                runnerUpScore, noArchetypes, matched, missing, forms, buffs, bonds, aspect.orElse(null));
    }

    public boolean hasAspect()
    {
        return this.aspect != null;
    }

    /** Indices of the regions this one's credited bonds are with, for the lens to highlight. */
    public List<Integer> bondedRegions()
    {
        List<Integer> partners = new ArrayList<>();

        for (BondLine bond : this.bonds)
        {
            if (bond.credited() && bond.otherRegion() >= 0 && !partners.contains(bond.otherRegion()))
            {
                partners.add(bond.otherRegion());
            }
        }

        return partners;
    }

    public boolean isClassified()
    {
        return ClassificationResult.Status.CLASSIFIED.name().equals(this.status);
    }

    public boolean isAmbiguous()
    {
        return ClassificationResult.Status.AMBIGUOUS.name().equals(this.status);
    }

    public boolean hasNextTier()
    {
        return this.scoreToNextTier != NO_NEXT_TIER;
    }

    public boolean hasRunnerUp()
    {
        return !this.runnerUpDisplayName.isBlank();
    }

    public static List<LensRegionReport> of(List<ClassificationResult> results, BuffBreakdown breakdown)
    {
        List<LensRegionReport> reports = new ArrayList<>(results.size());

        for (int i = 0; i < results.size(); i++)
        {
            reports.add(of(results.get(i), i, breakdown));
        }

        return reports;
    }

    public static LensRegionReport of(ClassificationResult result, int index, BuffBreakdown breakdown)
    {
        final ArchetypeScore best = result.best();

        if (best == null)
        {
            return new LensRegionReport(
                    index, result.status().name(), "", "", 0, 0d, NO_NEXT_TIER, "", 0d, true,
                    List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }

        final boolean classified = result.status() == ClassificationResult.Status.CLASSIFIED;
        final boolean ambiguous = result.status() == ClassificationResult.Status.AMBIGUOUS;
        final OptionalDouble toNext = best.scoreToNextTier();
        final ArchetypeScore runnerUp = result.runnerUp();

        return new LensRegionReport(
                index,
                result.status().name(),
                best.archetypeId(),
                best.displayName(),
                classified ? best.tier() : 0,
                best.score(),
                toNext.isPresent() ? toNext.getAsDouble() : NO_NEXT_TIER,
                ambiguous && runnerUp != null ? runnerUp.displayName() : "",
                ambiguous && runnerUp != null ? runnerUp.score() : 0d,
                false,
                signals(best.contributions()),
                missing(best.missingSignals()),
                forms(best),
                classified ? buffsOf(best.archetypeId(), breakdown) : List.of(),
                bonds(best),
                aspectOf(best));
    }

    /**
     * The aspect the room took, its near miss, and what would tip it - the same four things
     * {@code SoulReport} says, so the screen and the chat command cannot disagree. Null when the
     * room took no aspect, which is the whole of what the switch being off looks like here (#176).
     */
    private static AspectLine aspectOf(ArchetypeScore score)
    {
        final AspectSelection aspect = score.aspect();

        if (aspect == null)
        {
            return null;
        }

        final AspectSelection.Support runnerUp = aspect.runnerUp();
        final AspectSelection.Support taken = aspect.taken();
        final AspectSelection.Tip tip = aspect.tip();

        return new AspectLine(
                aspect.takenId(),
                aspect.takenDisplayName(),
                runnerUp == null ? "" : runnerUp.displayName(),
                // unsigned, for the same reason SoulReport takes it unsigned: the winner is "ahead
                // by" and a default that held its room is "behind by", and neither wants a minus
                taken == null || runnerUp == null ? 0d : Math.abs(taken.support() - runnerUp.support()),
                aspect.heldByDefault(),
                tip == null ? "" : BlockNames.text(tip.blockDescription()),
                tip == null ? 0 : tip.blocksNeeded(),
                tip == null ? "" : tip.displayName());
    }

    /**
     * Credited bonds first, then the near misses with their reasons - the same order and the same
     * omission as {@code SoulReport}: a discord that did not fire is not worth a line, since
     * "your powder magazine is not near your hearth" reads as a nudge to move it closer.
     */
    private static List<BondLine> bonds(ArchetypeScore score)
    {
        List<BondLine> bonds = new ArrayList<>();

        for (ArchetypeScore.BondContribution bond : score.bondContributions())
        {
            bonds.add(new BondLine(
                    bond.relationId(), bond.otherDisplayName(), bond.otherArchetypeId(), bond.otherRegion(),
                    bond.contribution(), true, bond.discord(), bond.diagnostic()));
        }

        for (ArchetypeScore.BondContribution miss : score.missingBonds())
        {
            if (!miss.discord())
            {
                bonds.add(new BondLine(
                        miss.relationId(), miss.otherDisplayName(), miss.otherArchetypeId(), miss.otherRegion(),
                        0d, false, false, miss.diagnostic()));
            }
        }

        return bonds;
    }

    private static List<Signal> signals(List<ArchetypeScore.SignalContribution> contributions)
    {
        List<Signal> signals = new ArrayList<>(contributions.size());

        for (ArchetypeScore.SignalContribution contribution : contributions)
        {
            signals.add(new Signal(
                    BlockNames.text(contribution.description()),
                    contribution.count(),
                    contribution.countedCount(),
                    contribution.contribution()));
        }

        return signals;
    }

    private static List<String> missing(List<ArchetypeScore.SignalContribution> missing)
    {
        List<String> names = new ArrayList<>(missing.size());

        for (ArchetypeScore.SignalContribution contribution : missing)
        {
            names.add(BlockNames.text(contribution.description()));
        }

        return names;
    }

    private static List<Form> forms(ArchetypeScore score)
    {
        List<Form> forms = new ArrayList<>(score.structuralContributions().size() + score.missingStructures().size());

        for (ArchetypeScore.StructureContribution hit : score.structuralContributions())
        {
            forms.add(new Form(hit.name(), hit.contribution(), true, clauseLines(hit.root(), new ArrayList<>())));
        }

        for (ArchetypeScore.StructureContribution miss : score.missingStructures())
        {
            forms.add(new Form(miss.name(), 0d, false, clauseLines(miss.root(), new ArrayList<>())));
        }

        return forms;
    }

    /**
     * Walks one form's evaluated clause tree the same way {@code SoulReport#clauseLines} does: an
     * {@code all}/{@code any} node contributes no line of its own, only its children, and an
     * {@code any} node with a winning child shows only that child.
     */
    private static List<ClauseLine> clauseLines(ArchetypeScore.ClauseEvaluation node, List<ClauseLine> into)
    {
        if (node.typeId().equals("all") || node.typeId().equals("any"))
        {
            List<ArchetypeScore.ClauseEvaluation> children = node.children();

            if (node.typeId().equals("any") && node.confidence() > 0d && node.selectedChild() >= 0)
            {
                children = List.of(children.get(node.selectedChild()));
            }

            for (ArchetypeScore.ClauseEvaluation child : children)
            {
                clauseLines(child, into);
            }

            return into;
        }

        final String text = node.diagnostic().isBlank()
                ? node.description()
                : node.description() + " - " + node.diagnostic();

        into.add(new ClauseLine(text, node.confidence() > 0d));
        return into;
    }

    private static List<BuffEntry> buffsOf(String archetypeId, BuffBreakdown breakdown)
    {
        List<BuffEntry> entries = new ArrayList<>();

        for (BuffBreakdown.Source source : breakdown.sources())
        {
            if (source.archetypeId().equals(archetypeId))
            {
                entries.add(new BuffEntry(source.buffType(), source.magnitude()));
            }
        }

        return entries;
    }

    /** @param countedCount blocks that actually counted, after the signal's cap - see {@link #isCapped()} */
    public record Signal(String description, int count, int countedCount, double contribution)
    {
        public static final Codec<Signal> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("description").forGetter(Signal::description),
                        Codec.INT.fieldOf("count").forGetter(Signal::count),
                        Codec.INT.fieldOf("counted").forGetter(Signal::countedCount),
                        Codec.DOUBLE.fieldOf("contribution").forGetter(Signal::contribution))
                .apply(instance, Signal::new));

        public boolean isCapped()
        {
            return this.count > this.countedCount;
        }
    }

    public record ClauseLine(String text, boolean hit)
    {
        public static final Codec<ClauseLine> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("text").forGetter(ClauseLine::text),
                        Codec.BOOL.fieldOf("hit").forGetter(ClauseLine::hit))
                .apply(instance, ClauseLine::new));
    }

    public record Form(String name, double contribution, boolean credited, List<ClauseLine> clauses)
    {
        public static final Codec<Form> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("name").forGetter(Form::name),
                        Codec.DOUBLE.fieldOf("contribution").forGetter(Form::contribution),
                        Codec.BOOL.fieldOf("credited").forGetter(Form::credited),
                        ClauseLine.CODEC.listOf().optionalFieldOf("clauses", List.of()).forGetter(Form::clauses))
                .apply(instance, Form::new));

        public Form
        {
            clauses = List.copyOf(clauses);
        }
    }

    /**
     * One bond, credited or not, as the lens shows it.
     *
     * @param otherRegion index of the partner region in the same report list, or {@code -1}
     * @param diagnostic  the relation's own reason - what was credited, or the gap and the threshold
     */
    public record BondLine(
            String relationId,
            String otherDisplayName,
            String otherArchetypeId,
            int otherRegion,
            double contribution,
            boolean credited,
            boolean discord,
            String diagnostic)
    {
        public static final Codec<BondLine> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("relation").forGetter(BondLine::relationId),
                        Codec.STRING.optionalFieldOf("other_display_name", "").forGetter(BondLine::otherDisplayName),
                        Codec.STRING.optionalFieldOf("other_archetype", "").forGetter(BondLine::otherArchetypeId),
                        Codec.INT.optionalFieldOf("other_region", -1).forGetter(BondLine::otherRegion),
                        Codec.DOUBLE.optionalFieldOf("contribution", 0d).forGetter(BondLine::contribution),
                        Codec.BOOL.optionalFieldOf("credited", false).forGetter(BondLine::credited),
                        Codec.BOOL.optionalFieldOf("discord", false).forGetter(BondLine::discord),
                        Codec.STRING.optionalFieldOf("diagnostic", "").forGetter(BondLine::diagnostic))
                .apply(instance, BondLine::new));
    }

    /**
     * What the room turned out to be for, as the lens shows it - the Aspects epic (#171).
     *
     * <p>Carries the margin between first and second because the near miss is the actionable half,
     * and carries it knowing it decides nothing: the room scores and pays the same whichever aspect
     * it took. The screen says so in as many words for the same reason the chat report does.
     *
     * @param heldByDefault the runner-up leads on support but not by enough to take the room -
     *                      a different sentence from "this is an archive", and the useful one
     * @param tipBlocks     how many more of {@code tipDescription} would hand it over, or 0 when no
     *                      number of blocks would
     */
    public record AspectLine(
            String aspectId,
            String displayName,
            String runnerUpDisplayName,
            double margin,
            boolean heldByDefault,
            String tipDescription,
            int tipBlocks,
            String tipDisplayName)
    {
        public static final Codec<AspectLine> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("id").forGetter(AspectLine::aspectId),
                        Codec.STRING.fieldOf("display_name").forGetter(AspectLine::displayName),
                        Codec.STRING.optionalFieldOf("runner_up_display_name", "").forGetter(AspectLine::runnerUpDisplayName),
                        Codec.DOUBLE.optionalFieldOf("margin", 0d).forGetter(AspectLine::margin),
                        Codec.BOOL.optionalFieldOf("held_by_default", false).forGetter(AspectLine::heldByDefault),
                        Codec.STRING.optionalFieldOf("tip_description", "").forGetter(AspectLine::tipDescription),
                        Codec.INT.optionalFieldOf("tip_blocks", 0).forGetter(AspectLine::tipBlocks),
                        Codec.STRING.optionalFieldOf("tip_display_name", "").forGetter(AspectLine::tipDisplayName))
                .apply(instance, AspectLine::new));

        public boolean hasRunnerUp()
        {
            return !this.runnerUpDisplayName.isBlank();
        }

        public boolean hasTip()
        {
            return this.tipBlocks > 0 && !this.tipDescription.isBlank();
        }
    }

    public record BuffEntry(String buffType, double magnitude)
    {
        public static final Codec<BuffEntry> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("buff_type").forGetter(BuffEntry::buffType),
                        Codec.DOUBLE.fieldOf("magnitude").forGetter(BuffEntry::magnitude))
                .apply(instance, BuffEntry::new));
    }
}
