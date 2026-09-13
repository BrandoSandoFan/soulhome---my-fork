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
 * @param aspectsEnabled         whether a room takes an aspect at all (the Aspects epic, #171, rule
 *                               1). Off is not "aspects are skipped in the calculator" but
 *                               indistinguishable from the mod before the epic: no aspect is
 *                               selected, no surface mentions one, nothing about one is saved, and
 *                               every archetype grants its own top-level buffs. An archetype
 *                               declaring aspects still loads and still works with this off - it
 *                               simply pays what it always paid.
 * @param aspectMargin           how far clear of the default aspect a challenger must stand before
 *                               it takes the room. Ties and near-ties go to the default, which pays
 *                               what the room paid before aspects existed, so a player who updates
 *                               and changes nothing notices nothing - and a room whose two aspects
 *                               are near-equal does not flip its buff every time a block moves. See
 *                               {@link AspectSelector} for why this is the anchor rather than
 *                               remembering the aspect a room had last time.
 */
public record ScoringSettings(
        double diversityBonusPerRole,
        double densityFloor,
        double minDensityFactor,
        double ambiguityMargin,
        double structuralShareCap,
        double structuralRoleThreshold,
        double bondShareCap,
        boolean aspectsEnabled,
        double aspectMargin)
{
    /**
     * Suggested default - see the field javadoc above. A quarter: with the shipped bonds a room
     * in a well-laid-out house earns around a tenth to a fifth more than the same room alone,
     * which lifts a strong tier 2 to tier 3 and does nothing for an empty box. See the balance
     * pass in #149, and {@code ArchetypeCeilingTest}'s bound on how much headroom bonds may add.
     */
    public static final double DEFAULT_BOND_SHARE_CAP = 0.25d;

    /**
     * Aspects ship on, and the decision #176 asks to be recorded rather than made by accident is
     * this one.
     *
     * <p>The argument for off is real: whether a room should mean one thing or several is a matter
     * of taste, and this is a change to how an existing save pays out. The argument that wins is
     * that the two risks behind it are answered by the mechanism rather than by the switch. No build
     * loses anything, because the default aspect pays exactly the archetype's own buffs; and no
     * build silently changes what it pays, because a challenger only takes a room by leading the
     * default by {@link #DEFAULT_ASPECT_MARGIN} - so a player who updates and changes nothing keeps
     * what they had unless they had plainly already built the other thing. Against that, a feature
     * shipped off is a feature nobody finds, and a mod whose most-considered system is its rooms
     * should not hide the first change that makes a thirtieth room interesting rather than additive.
     *
     * <p>A pack that wants one room to mean one thing sets {@code aspects.enabled = false} and gets
     * exactly the mod as it was, which is the promise #176 exists to keep.
     */
    public static final boolean DEFAULT_ASPECTS_ENABLED = true;

    /**
     * 15% clear of the default, the same phrasing {@link #ambiguityMargin} uses for the same kind of
     * question - "is this actually a contest, or a coin toss with extra steps".
     */
    public static final double DEFAULT_ASPECT_MARGIN = 1.15d;

    public static final ScoringSettings DEFAULTS = new ScoringSettings(
            0.15d, 0.02d, 0.25d, 1.15d, 1.0d, 0.25d, DEFAULT_BOND_SHARE_CAP,
            DEFAULT_ASPECTS_ENABLED, DEFAULT_ASPECT_MARGIN);

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

    /** Every setting from before aspects existed, with the aspect switch and margin at their defaults. */
    public ScoringSettings(
            double diversityBonusPerRole,
            double densityFloor,
            double minDensityFactor,
            double ambiguityMargin,
            double structuralShareCap,
            double structuralRoleThreshold,
            double bondShareCap)
    {
        this(diversityBonusPerRole, densityFloor, minDensityFactor, ambiguityMargin, structuralShareCap,
                structuralRoleThreshold, bondShareCap, DEFAULT_ASPECTS_ENABLED, DEFAULT_ASPECT_MARGIN);
    }

    /** The same settings with aspects switched off - what {@code aspects.enabled = false} produces. */
    public ScoringSettings withoutAspects()
    {
        return new ScoringSettings(
                this.diversityBonusPerRole, this.densityFloor, this.minDensityFactor, this.ambiguityMargin,
                this.structuralShareCap, this.structuralRoleThreshold, this.bondShareCap,
                false, this.aspectMargin);
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

        if (aspectMargin < 1)
        {
            throw new IllegalArgumentException("aspectMargin must be at least 1, got " + aspectMargin);
        }
    }
}
