/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.RegionBounds;

import java.util.ArrayList;
import java.util.List;

/**
 * One detected region, flattened to what a client needs to draw it.
 *
 * <p>Region detection is the invisible half of this feature: a player can see their blocks but not
 * where the mod thinks one room stops and the next begins, and almost every "why didn't this
 * work" turns out to be a disagreement about exactly that. Sending the boxes to the client and
 * drawing them is what makes that step visible.
 *
 * <p>Carries ids and a translation key rather than a finished {@code Component}, so the client
 * formats in its own language and nothing here depends on how a component happens to serialise.
 */
public record RegionHighlight(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        String status,
        String archetypeId,
        String displayName,
        int tier,
        double score)
{
    public static final Codec<RegionHighlight> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.fieldOf("min_x").forGetter(RegionHighlight::minX),
                    Codec.INT.fieldOf("min_y").forGetter(RegionHighlight::minY),
                    Codec.INT.fieldOf("min_z").forGetter(RegionHighlight::minZ),
                    Codec.INT.fieldOf("max_x").forGetter(RegionHighlight::maxX),
                    Codec.INT.fieldOf("max_y").forGetter(RegionHighlight::maxY),
                    Codec.INT.fieldOf("max_z").forGetter(RegionHighlight::maxZ),
                    Codec.STRING.fieldOf("status").forGetter(RegionHighlight::status),
                    Codec.STRING.optionalFieldOf("archetype", "").forGetter(RegionHighlight::archetypeId),
                    Codec.STRING.optionalFieldOf("display_name", "").forGetter(RegionHighlight::displayName),
                    Codec.INT.optionalFieldOf("tier", 0).forGetter(RegionHighlight::tier),
                    Codec.DOUBLE.optionalFieldOf("score", 0d).forGetter(RegionHighlight::score))
            .apply(instance, RegionHighlight::new));

    public static RegionHighlight of(ClassificationResult result)
    {
        final RegionBounds bounds = result.region().bounds();
        final ArchetypeScore best = result.best();

        //in an UNCLASSIFIED region every archetype scores 0.0 and "best" is only whichever id
        //sorts first alphabetically - not a name the client should ever show as this region's
        //title. AMBIGUOUS keeps its name (the top candidate, same as the chat report's "halfway
        //between X and Y"); only a genuine zero-everywhere miss goes nameless
        final boolean named = best != null && result.status() != ClassificationResult.Status.UNCLASSIFIED;

        return new RegionHighlight(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                result.status().name(),
                named ? best.archetypeId() : "",
                named ? best.displayName() : "",
                named ? best.tier() : 0,
                best == null ? 0d : best.score());
    }

    public static List<RegionHighlight> of(List<ClassificationResult> results)
    {
        List<RegionHighlight> highlights = new ArrayList<>(results.size());

        for (ClassificationResult result : results)
        {
            highlights.add(of(result));
        }

        return highlights;
    }

    /**
     * Whether this region was awarded its archetype, as opposed to being a near miss. Read off the
     * status name rather than the enum so an unknown value from a mismatched client build renders
     * as "not classified" instead of throwing mid-frame.
     */
    public boolean isClassified()
    {
        return ClassificationResult.Status.CLASSIFIED.name().equals(this.status);
    }

    public boolean isAmbiguous()
    {
        return ClassificationResult.Status.AMBIGUOUS.name().equals(this.status);
    }

    public boolean contains(double x, double y, double z)
    {
        return x >= this.minX && x <= this.maxX + 1
                && y >= this.minY && y <= this.maxY + 1
                && z >= this.minZ && z <= this.maxZ + 1;
    }
}
