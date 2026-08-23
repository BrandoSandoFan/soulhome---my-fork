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

    /** How many matched structural forms are worth listing - there are far fewer of these per
     *  archetype than signals, so every hit is usually worth showing. */
    private static final int TOP_STRUCTURES = 3;

    /** How many zero-scoring forms to name, with their own diagnostic rather than a bare list -
     *  "what should I arrange next", the structural sibling of {@link #MISSING_SIGNALS}. */
    private static final int MISSING_STRUCTURES = 2;

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
            case CLASSIFIED -> lines.addAll(explainSuccess(best));
            case AMBIGUOUS -> lines.addAll(explainAmbiguity(best, result.runnerUp()));
            case UNCLASSIFIED -> lines.addAll(explainFailure(best));
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

    private static List<Component> explainSuccess(ArchetypeScore best)
    {
        List<Component> lines = new ArrayList<>(contributions(best));
        lines.addAll(structure(best));

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
        lines.addAll(missingStructure(best));

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
        lines.addAll(missingStructure(best));

        return lines;
    }

    /**
     * Nothing qualified. The closest archetype's gates are the actionable part, so they come
     * first and in full - "needs 16 of #minecraft:bookshelves, found 3" is the sentence a stuck
     * player needs.
     */
    private static List<Component> explainFailure(ArchetypeScore best)
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
                    requirement.description(),
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
        lines.addAll(structure(best));
        lines.addAll(missing(best));
        lines.addAll(missingStructure(best));

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
                    contribution.description(),
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

    /**
     * How the room is arranged, not just what it holds - the structural sibling of
     * {@link #contributions}. Best first, exactly as {@link ArchetypeScore#structuralContributions}
     * already sorts them.
     */
    private static List<Component> structure(ArchetypeScore score)
    {
        List<Component> lines = new ArrayList<>();

        if (score.structuralContributions().isEmpty())
        {
            return lines;
        }

        final List<ArchetypeScore.StructureContribution> shown = score.structuralContributions()
                .subList(0, Math.min(TOP_STRUCTURES, score.structuralContributions().size()));

        for (ArchetypeScore.StructureContribution contribution : shown)
        {
            lines.add(indent(translated(
                    Constants.StringKeys.REGION_STRUCTURE,
                    contribution.name(),
                    score(contribution.contribution())))
                    .withStyle(ChatFormatting.WHITE));
        }

        return lines;
    }

    /**
     * Forms that scored exactly zero, each with its own diagnostic - "arranged like this and it
     * would count" is a more useful answer than a bare name, which is why this does not just list
     * names the way {@link #missing} does for signals.
     */
    private static List<Component> missingStructure(ArchetypeScore score)
    {
        List<Component> lines = new ArrayList<>();

        if (score.missingStructures().isEmpty())
        {
            return lines;
        }

        final List<ArchetypeScore.StructureContribution> shown = score.missingStructures()
                .subList(0, Math.min(MISSING_STRUCTURES, score.missingStructures().size()));

        for (ArchetypeScore.StructureContribution missing : shown)
        {
            final String diagnostic = missing.root() == null ? "" : missing.root().diagnostic();

            lines.add(indent(translated(
                    Constants.StringKeys.REGION_STRUCTURE_MISSING,
                    missing.name(),
                    diagnostic.isEmpty() ? missing.root().description() : diagnostic))
                    .withStyle(ChatFormatting.BLUE));
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

        List<String> names = new ArrayList<>();

        for (ArchetypeScore.SignalContribution missing : score.missingSignals())
        {
            if (names.size() >= MISSING_SIGNALS)
            {
                break;
            }

            names.add(missing.description());
        }

        lines.add(indent(translated(
                Constants.StringKeys.REGION_MISSING,
                String.join(", ", names)))
                .withStyle(ChatFormatting.BLUE));

        return lines;
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
        return Component.literal("   ").append(line);
    }
}
