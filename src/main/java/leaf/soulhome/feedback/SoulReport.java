/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.buffs.SoulBuffEffects;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.SoulAnalysis;
import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.BuffBreakdown;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.SoulBuffSet;
import leaf.soulhome.structures.core.SoulRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Puts what the classifier saw into words.
 *
 * <p>This is the difference between a fuzzy system and a broken one. With a fixed multiblock, a
 * player who gets no buff compares their build to the schematic in the book. Here there is no
 * schematic, so an unexplained "no buff" is a dead end - and the only cure is to say, in as many
 * words, what was counted, what was missing, and what the room nearly was.
 *
 * <p>Every number shown here comes off {@link ArchetypeScore}, which the classifier fills in as it
 * scores rather than reconstructing afterwards. Nothing in this class recomputes anything: if the
 * report and the buff could ever disagree, the report would be worse than useless.
 */
public final class SoulReport
{
    /** How many matched signals are worth listing before the tail stops being informative. */
    private static final int TOP_SIGNALS = 4;

    /** How many absent signals to name. This is the "what should I add next" list. */
    private static final int MISSING_SIGNALS = 3;

    /** How many credited forms are worth showing their clause tree before the tail stops helping. */
    private static final int TOP_STRUCTURES = 3;

    /** How many zero-scored forms to name, each with the alternatives that were tried. */
    private static final int MISSING_STRUCTURES = 2;

    /** Guards {@link #clauseLines} against a malformed tree; the grammar itself caps nesting at 2. */
    private static final int MAX_CLAUSE_DEPTH = 8;

    private SoulReport()
    {
    }

    /** The whole soulhome: a header, then one block per region found. */
    public static List<Component> analysis(SoulAnalysis analysis)
    {
        List<Component> lines = new ArrayList<>();

        if (analysis.isEmpty())
        {
            lines.add(translated(Constants.StringKeys.ANALYSE_NOTHING_FOUND).withStyle(ChatFormatting.GRAY));
            return lines;
        }

        lines.add(translated(
                Constants.StringKeys.ANALYSE_HEADER,
                analysis.results().size(),
                analysis.classifiedCount())
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        int index = 1;

        for (ClassificationResult result : analysis.results())
        {
            lines.addAll(region(result, index++));
        }

        return lines;
    }

    /** One region, with the reasoning behind whatever it was decided to be. */
    public static List<Component> region(ClassificationResult result, int index)
    {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal(index + ". ").withStyle(ChatFormatting.DARK_GRAY)
                .append(headline(result)));
        lines.add(indent(describeShape(result.region())).withStyle(ChatFormatting.DARK_GRAY));

        final ArchetypeScore best = result.best();

        if (best == null)
        {
            // no archetypes are loaded at all, which is a datapack problem rather than a build one
            lines.add(indent(translated(Constants.StringKeys.ANALYSE_NO_ARCHETYPES))
                    .withStyle(ChatFormatting.RED));
            return lines;
        }

        switch (result.status())
        {
            case CLASSIFIED -> lines.addAll(explainSuccess(best, result.region()));
            case AMBIGUOUS -> lines.addAll(explainAmbiguity(best, result.runnerUp()));
            case UNCLASSIFIED -> lines.addAll(explainFailure(best, result.region()));
        }

        return lines;
    }

    /**
     * The one-line form, for the Soul Lens overlay and for anywhere a region needs naming rather
     * than explaining.
     */
    public static MutableComponent headline(ClassificationResult result)
    {
        final ArchetypeScore best = result.best();

        return switch (result.status())
        {
            case CLASSIFIED -> translated(
                    Constants.StringKeys.REGION_CLASSIFIED,
                    name(best),
                    best.tier(),
                    score(best.score()))
                    .withStyle(ChatFormatting.GREEN);

            case AMBIGUOUS -> translated(
                    Constants.StringKeys.REGION_AMBIGUOUS,
                    name(best),
                    name(result.runnerUp()))
                    .withStyle(ChatFormatting.YELLOW);

            case UNCLASSIFIED -> translated(Constants.StringKeys.REGION_UNCLASSIFIED)
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    /** What a player has, and which rooms it came from. */
    public static List<Component> buffs(BuffBreakdown breakdown)
    {
        List<Component> lines = new ArrayList<>();

        if (breakdown.totals().isEmpty())
        {
            lines.add(translated(Constants.StringKeys.BUFFS_NONE).withStyle(ChatFormatting.GRAY));
            return lines;
        }

        lines.add(translated(Constants.StringKeys.BUFFS_HEADER).withStyle(ChatFormatting.LIGHT_PURPLE));

        for (Map.Entry<String, Double> total : breakdown.totals().asMap().entrySet())
        {
            final String buffType = total.getKey();

            MutableComponent line = translated(
                    Constants.StringKeys.BUFFS_ENTRY,
                    Component.translatable(buffKey(buffType)),
                    magnitude(buffType, total.getValue()))
                    .withStyle(ChatFormatting.AQUA);

            if (breakdown.isCapped(buffType))
            {
                line.append(Component.literal(" "))
                        .append(translated(Constants.StringKeys.BUFFS_CAPPED).withStyle(ChatFormatting.GOLD));
            }

            lines.add(line);

            final double rankBonus = breakdown.rankBonusOf(buffType);

            if (rankBonus > 0d)
            {
                lines.add(indent(translated(
                        Constants.StringKeys.BUFFS_RANK_BONUS,
                        magnitude(buffType, rankBonus)))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }

            for (BuffBreakdown.Source source : breakdown.sourcesOf(buffType))
            {
                lines.add(indent(translated(
                        Constants.StringKeys.BUFFS_SOURCE,
                        Component.translatable(source.displayName()),
                        source.rooms(),
                        source.bestTier()))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }

        return lines;
    }

    /**
     * Just the magnitudes, for the client, which is sent those but not where they came from.
     * Used by the Soul Lens tooltip - the one place a player can see what their soul is worth
     * without typing anything or being anywhere in particular.
     */
    public static List<Component> buffSummary(SoulBuffSet buffs)
    {
        List<Component> lines = new ArrayList<>();

        if (buffs.isEmpty())
        {
            lines.add(translated(Constants.StringKeys.BUFFS_NONE).withStyle(ChatFormatting.GRAY));
            return lines;
        }

        lines.add(translated(Constants.StringKeys.BUFFS_HEADER).withStyle(ChatFormatting.GRAY));

        for (Map.Entry<String, Double> total : buffs.asMap().entrySet())
        {
            lines.add(indent(translated(
                    Constants.StringKeys.BUFFS_ENTRY,
                    Component.translatable(buffKey(total.getKey())),
                    magnitude(total.getKey(), total.getValue())))
                    .withStyle(ChatFormatting.AQUA));
        }

        return lines;
    }

    private static List<Component> explainSuccess(ArchetypeScore best, SoulRegion region)
    {
        List<Component> lines = new ArrayList<>(contributions(best));
        lines.addAll(structuralSection(best, region));

        final OptionalDouble toNext = best.scoreToNextTier();

        if (toNext.isPresent())
        {
            lines.add(indent(translated(
                    Constants.StringKeys.REGION_NEXT_TIER,
                    score(toNext.getAsDouble()),
                    best.tier() + 1))
                    .withStyle(ChatFormatting.GRAY));
        }

        lines.addAll(missing(best));

        return lines;
    }

    /**
     * Two archetypes too close to call. Saying which two, and by how much, is the whole point -
     * a player told only "ambiguous" has been given a riddle rather than an answer.
     */
    private static List<Component> explainAmbiguity(ArchetypeScore best, ArchetypeScore runnerUp)
    {
        List<Component> lines = new ArrayList<>();

        lines.add(indent(translated(
                Constants.StringKeys.REGION_AMBIGUOUS_DETAIL,
                name(best),
                score(best.score()),
                name(runnerUp),
                score(runnerUp.score())))
                .withStyle(ChatFormatting.YELLOW));

        lines.add(indent(translated(Constants.StringKeys.REGION_AMBIGUOUS_ADVICE))
                .withStyle(ChatFormatting.GRAY));

        lines.addAll(missing(best));

        return lines;
    }

    /**
     * Nothing qualified. The closest archetype's gates are the actionable part, so they come
     * first and in full - "needs 16 of #soulhome:bookshelves, found 3" is the sentence a stuck
     * player needs.
     */
    private static List<Component> explainFailure(ArchetypeScore best, SoulRegion region)
    {
        List<Component> lines = new ArrayList<>();

        lines.add(indent(translated(
                Constants.StringKeys.REGION_CLOSEST,
                name(best),
                score(best.score())))
                .withStyle(ChatFormatting.GRAY));

        if (best.ineligibleReason() != null)
        {
            lines.add(indent(Component.literal("- " + best.ineligibleReason()))
                    .withStyle(ChatFormatting.RED));
        }

        for (ArchetypeScore.FailedRequirement requirement : best.failedRequirements())
        {
            lines.add(indent(translated(
                    Constants.StringKeys.REGION_REQUIREMENT_FAILED,
                    BlockNames.of(requirement.description()),
                    requirement.required(),
                    requirement.found()))
                    .withStyle(ChatFormatting.RED));
        }

        if (!best.isGated())
        {
            // no hard gate failed, so it simply is not enough of a room yet
            final OptionalDouble toNext = best.scoreToNextTier();

            if (toNext.isPresent())
            {
                lines.add(indent(translated(
                        Constants.StringKeys.REGION_NEXT_TIER,
                        score(toNext.getAsDouble()),
                        1))
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        lines.addAll(contributions(best));
        lines.addAll(structuralSection(best, region));
        lines.addAll(missing(best));

        return lines;
    }

    /** What counted, best first, with a note when a cap is what is holding a signal back. */
    private static List<Component> contributions(ArchetypeScore score)
    {
        List<Component> lines = new ArrayList<>();

        if (score.contributions().isEmpty())
        {
            return lines;
        }

        final List<ArchetypeScore.SignalContribution> shown =
                score.contributions().subList(0, Math.min(TOP_SIGNALS, score.contributions().size()));

        for (ArchetypeScore.SignalContribution contribution : shown)
        {
            MutableComponent line = translated(
                    Constants.StringKeys.REGION_SIGNAL,
                    BlockNames.of(contribution.description()),
                    contribution.count(),
                    score(contribution.contribution()));

            line.withStyle(contribution.contribution() < 0 ? ChatFormatting.RED : ChatFormatting.WHITE);

            if (contribution.isCapped())
            {
                line.append(Component.literal(" "))
                        .append(translated(
                                Constants.StringKeys.REGION_SIGNAL_CAPPED,
                                contribution.countedCount())
                                .withStyle(ChatFormatting.GOLD));
            }

            lines.add(indent(line));
        }

        return lines;
    }

    /** What the room has none of. Directly the "what do I build next" answer. */
    private static List<Component> missing(ArchetypeScore score)
    {
        List<Component> lines = new ArrayList<>();

        if (score.missingSignals().isEmpty())
        {
            return lines;
        }

        MutableComponent names = Component.empty();
        int listed = 0;

        for (ArchetypeScore.SignalContribution missing : score.missingSignals())
        {
            if (listed >= MISSING_SIGNALS)
            {
                break;
            }

            if (listed > 0)
            {
                names.append(", ");
            }

            names.append(BlockNames.of(missing.description()));
            listed++;
        }

        lines.add(indent(translated(
                Constants.StringKeys.REGION_MISSING,
                names))
                .withStyle(ChatFormatting.BLUE));

        return lines;
    }

    /**
     * The arrangement half of the report. Skipped entirely when the archetype defines no forms, or
     * a gated region never reached them in the first place - see {@link ArchetypeScore#isGated()}.
     *
     * <p>Reports the clause, not just the form: "arrangement: 0.4" tells a player nothing they can
     * act on, so this walks the evaluated tree {@code ArchetypeClassifier} already recorded rather
     * than showing only the form's own combined confidence.
     */
    private static List<Component> structuralSection(ArchetypeScore score, SoulRegion region)
    {
        List<Component> lines = new ArrayList<>();

        if (score.structuralContributions().isEmpty() && score.missingStructures().isEmpty())
        {
            return lines;
        }

        MutableComponent header = translated(Constants.StringKeys.REGION_STRUCTURE_HEADER)
                .withStyle(ChatFormatting.LIGHT_PURPLE);

        if (score.structuralCapped())
        {
            header.append(Component.literal(" "))
                    .append(translated(Constants.StringKeys.REGION_STRUCTURE_CAPPED).withStyle(ChatFormatting.GOLD));
        }

        lines.add(indent(header));

        if (region.geometry().isTruncated())
        {
            lines.add(indent(translated(Constants.StringKeys.REGION_STRUCTURE_TRUNCATED), 2)
                    .withStyle(ChatFormatting.GRAY));
        }

        final List<ArchetypeScore.StructureContribution> hits = score.structuralContributions();

        for (ArchetypeScore.StructureContribution hit : hits.subList(0, Math.min(TOP_STRUCTURES, hits.size())))
        {
            lines.add(indent(translated(
                    Constants.StringKeys.REGION_STRUCTURE,
                    hit.name(),
                    score(hit.contribution())), 2)
                    .withStyle(ChatFormatting.WHITE));

            lines.addAll(clauseLines(hit.root(), 0));
        }

        final List<ArchetypeScore.StructureContribution> misses = score.missingStructures();

        for (ArchetypeScore.StructureContribution miss :
                misses.subList(0, Math.min(MISSING_STRUCTURES, misses.size())))
        {
            lines.add(indent(translated(Constants.StringKeys.REGION_STRUCTURE_ZERO, miss.name()), 2)
                    .withStyle(ChatFormatting.GRAY));

            lines.addAll(clauseLines(miss.root(), 0));
        }

        return lines;
    }

    /**
     * Walks one form's evaluated clause tree, mirroring how {@code FormClauseEvaluator} built it:
     * an {@code all} node contributes no line of its own, only its children, since it is the
     * clauses themselves that are actionable, not the mean of them. An {@code any} node names its
     * winning alternative when one matched - the player who ringed their rails in ice should be
     * told that is what was credited - and lists every alternative it tried when none did, which is
     * the single most useful thing the grammar makes possible: "the ice is not under, around, or
     * inside the rails" is three ways forward, not one dead end.
     *
     * <p>A clause named by a mod that is not installed ({@code UnknownClause}, #27) renders as
     * skipped, never as a failed clause - it never had anything to say either way.
     */
    private static List<Component> clauseLines(ArchetypeScore.ClauseEvaluation node, int depth)
    {
        if (depth > MAX_CLAUSE_DEPTH)
        {
            return List.of();
        }

        if (node.typeId().startsWith("unknown:"))
        {
            List<Component> lines = new ArrayList<>(1);
            lines.add(indent(translated(
                    Constants.StringKeys.REGION_STRUCTURE_SKIPPED,
                    node.typeId().substring("unknown:".length())), 3)
                    .withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }

        if (node.typeId().equals("all") || node.typeId().equals("any"))
        {
            List<ArchetypeScore.ClauseEvaluation> toRender = node.children();

            if (node.typeId().equals("any") && node.confidence() > 0d && node.selectedChild() >= 0)
            {
                toRender = List.of(node.children().get(node.selectedChild()));
            }

            List<Component> lines = new ArrayList<>();

            for (ArchetypeScore.ClauseEvaluation child : toRender)
            {
                lines.addAll(clauseLines(child, depth + 1));
            }

            return lines;
        }

        // a leaf: exactly one line, "+" or "-" and whatever the clause itself has to say
        List<Component> lines = new ArrayList<>(1);
        lines.add(indent(clauseLine(node), 3));
        return lines;
    }

    private static MutableComponent clauseLine(ArchetypeScore.ClauseEvaluation leaf)
    {
        final boolean hit = leaf.confidence() > 0d;

        final String text = leaf.diagnostic().isBlank()
                ? leaf.description()
                : leaf.description() + " - " + leaf.diagnostic();

        return translated(
                hit ? Constants.StringKeys.REGION_STRUCTURE_CLAUSE_HIT : Constants.StringKeys.REGION_STRUCTURE_CLAUSE_MISS,
                text)
                .withStyle(hit ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY);
    }

    private static MutableComponent describeShape(SoulRegion region)
    {
        return translated(
                Constants.StringKeys.REGION_SHAPE,
                region.type().getSerializedName(),
                region.bounds().toString(),
                region.volume());
    }

    /**
     * An archetype's display name is a translation key in the datapack, so it is resolved rather
     * than printed. A pack that forgot to add a translation gets its key on screen, which is the
     * correct nudge.
     */
    private static Component name(ArchetypeScore score)
    {
        if (score == null)
        {
            return Component.literal("-");
        }

        return score.displayName() == null || score.displayName().isBlank()
                ? Component.literal(score.archetypeId())
                : Component.translatable(score.displayName());
    }

    /** {@code soulhome:xp_gain} to {@code buff.soulhome.xp_gain}, the key the lang file holds. */
    private static String buffKey(String buffType)
    {
        final int separator = buffType.indexOf(':');

        return separator < 0
                ? "buff." + buffType
                : "buff." + buffType.substring(0, separator) + "." + buffType.substring(separator + 1);
    }

    private static String score(double value)
    {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Magnitudes are unitless in the data, so the effect that acts on one is asked how to read it:
     * a fraction shows as a percentage, a flat amount as a number. Showing "+0.2 enchanting
     * levels" as "+20%" would be a small lie that a player would plan around.
     */
    private static String magnitude(String buffType, double value)
    {
        final SoulBuffEffect effect = SoulBuffEffects.get(buffType);

        // an unknown buff type comes from another mod's effect; a fraction is the safer guess,
        // and it is what every built-in buff but one is
        if (effect == null || effect.isFraction())
        {
            return String.format(Locale.ROOT, "+%.0f%%", value * 100d);
        }

        return String.format(Locale.ROOT, "+%.1f", value);
    }

    private static MutableComponent translated(String key, Object... args)
    {
        return Component.translatable(key, args);
    }

    private static MutableComponent indent(Component line)
    {
        return indent(line, 1);
    }

    private static MutableComponent indent(Component line, int levels)
    {
        return Component.literal("   ".repeat(Math.max(levels, 0))).append(line);
    }
}
