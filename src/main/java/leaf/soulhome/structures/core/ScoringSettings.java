/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Tuning for {@link ArchetypeClassifier}, exposed as Forge config under {@code scoring}. As with
 * {@link ScanSettings}, {@link #DEFAULTS} is where the numbers themselves live.
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
 *                               the structural considerations epic (#25, #28). 1.0 means arrangement
 *                               can at most double what the room's contents alone earned - raised
 *                               from the original 0.5 by #54, which was a large part of why tier 2
 *                               was so hard to reach for a well-arranged room.
 * @param structuralRoleThreshold confidence a structural form must clear before its role counts
 *                               toward the diversity multiplier - otherwise an accidental
 *                               0.02-confidence clause would buy a full diversity bonus for free.
 *                               A bond's role clears the same bar, for the same reason.
 * @param bondShareCap           bond credit is capped at this fraction of what the room earned on
 *                               its own - its signal total plus its credited arrangement - so a
 *                               perfect floor plan of empty boxes is worth nothing (rule 5 of the
 *                               Soul Architecture epic, #140). Set conservatively: #54 had to soften
 *                               the structural cap after shipping it too tight, and the opposite
 *                               mistake here is harder to undo because it inflates every score at
 *                               once. Discords are not capped by this - evidence against a room
 *                               should not shrink as the room gets bigger.
 */
public record ScoringSettings(
        double diversityBonusPerRole,
        double densityFloor,
        double minDensityFactor,
        double ambiguityMargin,
        double structuralShareCap,
        double structuralRoleThreshold,
        double bondShareCap)
{
    /** Suggested default - see the field javadoc above, and the balance pass in #149. */
    public static final double DEFAULT_BOND_SHARE_CAP = 0.35d;

    public static final ScoringSettings DEFAULTS =
            new ScoringSettings(0.15d, 0.02d, 0.25d, 1.15d, 1.0d, 0.25d, DEFAULT_BOND_SHARE_CAP);

    /** Every setting from before bonds existed, with the bond cap at its default. */
    public ScoringSettings(
            double diversityBonusPerRole,
            double densityFloor,
            double minDensityFactor,
            double ambiguityMargin,
            double structuralShareCap,
            double structuralRoleThreshold)
    {
        this(diversityBonusPerRole, densityFloor, minDensityFactor, ambiguityMargin, structuralShareCap,
                structuralRoleThreshold, DEFAULT_BOND_SHARE_CAP);
    }

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

        if (bondShareCap < 0)
        {
            throw new IllegalArgumentException("bondShareCap must not be negative, got " + bondShareCap);
        }
    }
}
