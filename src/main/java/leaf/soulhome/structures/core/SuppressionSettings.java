/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Suppression (#188): what a player who has built at least one room perceives around another player
 * who has ascended. Both curves live here and nothing about rendering does - the client reads a
 * {@link Signature} and draws it.
 *
 * <h2>Two ranks, two jobs, never one number (rule 4 of #181)</h2>
 *
 * <ul>
 *   <li><b>Their rank sets the amount</b>: {@link #radiusFor}, {@link #strengthFor} and
 *       {@link #perceptionRangeFor} read only the watched player's rank.</li>
 *   <li><b>Your rank sets the legibility</b>: {@link #legibilityFor} reads only the observer's.
 *       As it rises the same field resolves from a formless smear into discrete rings, and the
 *       ring count is their rank.</li>
 * </ul>
 *
 * A single intensity driven by the difference of the two would render a strong opponent you can
 * handle identically to a weak one. {@code SuppressionSettingsTest} asserts the property that
 * rules that out directly: across the whole configured rank range, no two distinct
 * {@code (theirRank, yourRank)} pairs produce the same {@link Signature}.
 *
 * <p>Legibility is {@code 1 - (1 - yourRank / maxRank)^2}: strictly increasing across the ladder, so
 * every ascension resolves the field a little further, but front-loaded, so a middling rank already
 * reads rings rather than waiting for the top of the ladder to be told anything.
 *
 * <p><b>Rank 0 renders nothing.</b> An unascended player is not surrounded by a faint nothing; they
 * are surrounded by nothing, and {@link #signature} returns null for them.
 */
public record SuppressionSettings(
        boolean enabled,
        boolean distortion,
        boolean audio,
        double basePerceptionRange,
        double perceptionRangePerRank,
        double baseRadius,
        double radiusPerRank,
        double strengthPerRank,
        double sharpDisplacementShare)
{
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_DISTORTION = true;
    public static final boolean DEFAULT_AUDIO = true;

    /** 20 blocks for a rank I, 52 for a rank IX. You notice a great soul from further off than a small one. */
    public static final double DEFAULT_BASE_PERCEPTION_RANGE = 16d;
    public static final double DEFAULT_PERCEPTION_RANGE_PER_RANK = 4d;

    /** A 1.1 block field at rank I, 3.5 at rank IX, measured from the player's centre. */
    public static final double DEFAULT_BASE_RADIUS = 0.8d;
    public static final double DEFAULT_RADIUS_PER_RANK = 0.3d;

    /** How hard the field pulls on the image, per rank, capped at 1. */
    public static final double DEFAULT_STRENGTH_PER_RANK = 0.1d;

    /**
     * How much of the warp is left once the observer can read it fully. Below 1 on purpose: a rank 4
     * halo seen by a rank 4 is something you can aim through, where the same field seen by a rank 0
     * spoils their aim. Suppression costs a low-rank player accuracy; it never costs them
     * information they earned.
     */
    public static final double DEFAULT_SHARP_DISPLACEMENT_SHARE = 0.3d;

    public static final SuppressionSettings DEFAULTS = new SuppressionSettings(
            DEFAULT_ENABLED,
            DEFAULT_DISTORTION,
            DEFAULT_AUDIO,
            DEFAULT_BASE_PERCEPTION_RANGE,
            DEFAULT_PERCEPTION_RANGE_PER_RANK,
            DEFAULT_BASE_RADIUS,
            DEFAULT_RADIUS_PER_RANK,
            DEFAULT_STRENGTH_PER_RANK,
            DEFAULT_SHARP_DISPLACEMENT_SHARE);

    public SuppressionSettings
    {
        if (!(basePerceptionRange > 0d) || perceptionRangePerRank < 0d)
        {
            throw new IllegalArgumentException("perception range must be positive and grow with rank");
        }

        // strictly increasing, not merely non-decreasing: a radius that stood still between two ranks
        // would render those two ranks identically, which is the one thing this record exists to prevent
        if (!(baseRadius > 0d) || !(radiusPerRank > 0d))
        {
            throw new IllegalArgumentException("radius must be positive and strictly grow with rank");
        }

        if (!(strengthPerRank > 0d))
        {
            throw new IllegalArgumentException("strengthPerRank must be positive, got " + strengthPerRank);
        }

        if (!(sharpDisplacementShare >= 0d) || sharpDisplacementShare > 1d)
        {
            throw new IllegalArgumentException("sharpDisplacementShare must be in [0, 1], got " + sharpDisplacementShare);
        }
    }

    /** How far away a player of this rank is perceived at all, in blocks. Their rank, never yours. */
    public double perceptionRangeFor(int theirRank)
    {
        return this.basePerceptionRange + Math.max(0, theirRank) * this.perceptionRangePerRank;
    }

    /** The field's radius around them, in blocks. Their rank, never yours. */
    public double radiusFor(int theirRank)
    {
        return theirRank <= 0 ? 0d : this.baseRadius + theirRank * this.radiusPerRank;
    }

    /** How hard the field pulls, in {@code [0, 1]}. Their rank, never yours. */
    public double strengthFor(int theirRank)
    {
        return theirRank <= 0 ? 0d : Math.min(1d, theirRank * this.strengthPerRank);
    }

    /**
     * How well you can read a field, in {@code [0, 1]}: 0 is a formless smear, 1 is crisp rings.
     * Your rank, never theirs. See the class javadoc for the curve.
     */
    public static double legibilityFor(int yourRank, int maxRank)
    {
        if (maxRank <= 0)
        {
            return 1d;
        }

        final double share = Math.min(1d, Math.max(0d, (double) yourRank / maxRank));
        final double remaining = 1d - share;
        return 1d - remaining * remaining;
    }

    /**
     * How far the field actually displaces the image: its full strength when unreadable, down to
     * {@link #sharpDisplacementShare} of it when fully legible.
     */
    public double displacementFor(int theirRank, int yourRank, int maxRank)
    {
        final double legibility = legibilityFor(yourRank, maxRank);
        return this.strengthFor(theirRank) * (1d - (1d - this.sharpDisplacementShare) * legibility);
    }

    /**
     * Everything the client needs to draw one suppressed player, or null for a rank 0 player who
     * renders nothing at all. {@code rings} is always their rank; how countable those rings are is
     * {@code legibility}'s business, which is what keeps a rank 4 seen by a rank 0 a formless warp
     * rather than a four-ringed halo drawn faintly.
     */
    public Signature signature(int theirRank, int yourRank, int maxRank)
    {
        if (theirRank <= 0)
        {
            return null;
        }

        return new Signature(
                theirRank,
                this.radiusFor(theirRank),
                this.strengthFor(theirRank),
                legibilityFor(yourRank, maxRank),
                this.displacementFor(theirRank, yourRank, maxRank));
    }

    /** One suppressed player as one observer perceives them. */
    public record Signature(int rings, double radius, double strength, double legibility, double displacement)
    {
    }
}
