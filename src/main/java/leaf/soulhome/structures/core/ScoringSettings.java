/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Tuning for {@link ArchetypeClassifier}. Becomes Forge config in the balance pass.
 *
 * @param diversityBonusPerRole  score multiplier added for each distinct signal role beyond the
 *                               first. The lever that rewards a room with books, seating, lighting
 *                               and a lectern over a room with only books.
 * @param densityFloor           signal blocks per cell of volume below which a region starts being
 *                               penalised for being a mostly-empty cathedral
 * @param minDensityFactor       floor on that penalty, so a sparse room is weakened rather than
 *                               erased
 * @param ambiguityMargin        how far ahead the winning archetype must be before the region is
 *                               assigned to it. 1.15 means "15% clear of the runner-up".
 * @param structuralShareCap     structural credit is capped at this fraction of a region's signal
 *                               total, so a perfect arrangement of nothing is worth nothing - see
 *                               the structural considerations epic (#25, #28). 0.5 means arrangement
 *                               can at most add half again what the room's contents alone earned.
 * @param structuralRoleThreshold confidence a structural form must clear before its role counts
 *                               toward the diversity multiplier - otherwise an accidental
 *                               0.02-confidence clause would buy a full diversity bonus for free.
 */
public record ScoringSettings(
        double diversityBonusPerRole,
        double densityFloor,
        double minDensityFactor,
        double ambiguityMargin,
        double structuralShareCap,
        double structuralRoleThreshold)
{
    public static final ScoringSettings DEFAULTS = new ScoringSettings(0.15d, 0.02d, 0.25d, 1.15d, 0.5d, 0.25d);

    public ScoringSettings
    {
        if (diversityBonusPerRole < 0)
        {
            throw new IllegalArgumentException("diversityBonusPerRole must not be negative, got " + diversityBonusPerRole);
        }

        if (densityFloor < 0)
        {
            throw new IllegalArgumentException("densityFloor must not be negative, got " + densityFloor);
        }

        if (minDensityFactor < 0 || minDensityFactor > 1)
        {
            throw new IllegalArgumentException("minDensityFactor must be between 0 and 1, got " + minDensityFactor);
        }

        if (ambiguityMargin < 1)
        {
            throw new IllegalArgumentException("ambiguityMargin must be at least 1, got " + ambiguityMargin);
        }

        if (structuralShareCap < 0)
        {
            throw new IllegalArgumentException("structuralShareCap must not be negative, got " + structuralShareCap);
        }

        if (structuralRoleThreshold < 0 || structuralRoleThreshold > 1)
        {
            throw new IllegalArgumentException(
                    "structuralRoleThreshold must be between 0 and 1, got " + structuralRoleThreshold);
        }
    }
}
