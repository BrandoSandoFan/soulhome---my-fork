/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.BuffBreakdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A player's buffs and where they came from, flattened for the Soul Lens buffs screen (#50).
 *
 * <p>Used outside a soul: the lens already answered "what did the classifier see" with
 * {@link LensRegionReport}, and out in the world the question is the other one - "is any of this
 * actually doing anything". {@link BuffBreakdown} already carries the answer; this is only its
 * network shape, following the same rule as {@link LensRegionReport} - read off the synced score,
 * never recomputed client-side.
 */
public record LensBuffReport(String buffType, double magnitude, boolean capped, List<Source> sources)
{
    public static final Codec<LensBuffReport> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.STRING.fieldOf("buff_type").forGetter(LensBuffReport::buffType),
                    Codec.DOUBLE.fieldOf("magnitude").forGetter(LensBuffReport::magnitude),
                    Codec.BOOL.optionalFieldOf("capped", false).forGetter(LensBuffReport::capped),
                    Source.CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(LensBuffReport::sources))
            .apply(instance, LensBuffReport::new));

    public LensBuffReport
    {
        sources = List.copyOf(sources);
    }

    public static List<LensBuffReport> of(BuffBreakdown breakdown)
    {
        List<LensBuffReport> reports = new ArrayList<>();

        for (Map.Entry<String, Double> total : breakdown.totals().asMap().entrySet())
        {
            final String buffType = total.getKey();
            List<Source> sources = new ArrayList<>();

            for (BuffBreakdown.Source source : breakdown.sourcesOf(buffType))
            {
                sources.add(new Source(source.archetypeId(), source.displayName(), source.rooms(), source.bestTier(), source.magnitude()));
            }

            reports.add(new LensBuffReport(buffType, total.getValue(), breakdown.isCapped(buffType), sources));
        }

        return reports;
    }

    public record Source(String archetypeId, String displayName, int rooms, int bestTier, double magnitude)
    {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("archetype").forGetter(Source::archetypeId),
                        Codec.STRING.optionalFieldOf("display_name", "").forGetter(Source::displayName),
                        Codec.INT.fieldOf("rooms").forGetter(Source::rooms),
                        Codec.INT.fieldOf("best_tier").forGetter(Source::bestTier),
                        Codec.DOUBLE.fieldOf("magnitude").forGetter(Source::magnitude))
                .apply(instance, Source::new));
    }
}
