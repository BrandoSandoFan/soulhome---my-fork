/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.BuffBreakdown;
import leaf.soulhome.structures.core.ClassificationResult;

import java.util.ArrayList;
import java.util.List;
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
        List<BuffEntry> buffs)
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
                    BuffEntry.CODEC.listOf().optionalFieldOf("buffs", List.of()).forGetter(LensRegionReport::buffs))
            .apply(instance, LensRegionReport::new));

    public LensRegionReport
    {
        matched = List.copyOf(matched);
        missing = List.copyOf(missing);
        forms = List.copyOf(forms);
        buffs = List.copyOf(buffs);
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
                    List.of(), List.of(), List.of(), List.of());
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
                classified ? buffsOf(best.archetypeId(), breakdown) : List.of());
    }

    private static List<Signal> signals(List<ArchetypeScore.SignalContribution> contributions)
    {
        List<Signal> signals = new ArrayList<>(contributions.size());

        for (ArchetypeScore.SignalContribution contribution : contributions)
        {
            signals.add(new Signal(
                    contribution.description(),
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
            names.add(contribution.description());
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

    public record BuffEntry(String buffType, double magnitude)
    {
        public static final Codec<BuffEntry> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("buff_type").forGetter(BuffEntry::buffType),
                        Codec.DOUBLE.fieldOf("magnitude").forGetter(BuffEntry::magnitude))
                .apply(instance, BuffEntry::new));
    }
}
