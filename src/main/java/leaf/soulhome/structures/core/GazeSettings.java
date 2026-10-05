/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Soulgaze (#187): how far the observatory's active reaches, how long a gaze lasts, how often it
 * can be cast, and - per rule 5 of #181 - how obvious a gaze is to the player being watched.
 *
 * <h2>Obviousness runs the other way from everything else here</h2>
 *
 * Range and duration grow with the observatory's magnitude; obviousness shrinks with it. A better
 * observatory does not just see further, it sees more quietly - but never silently.
 * {@link #obviousnessFor} is {@code floor + (1 - floor) / (1 + falloff * magnitude)}: monotonic
 * decreasing, exactly 1 at magnitude 0, and approaching {@link #obviousnessFloor} from above
 * without ever reaching it, whatever magnitude rank amplification (#85) pushes the room to. The
 * floor is validated strictly positive, so no configuration can make a gaze undetectable.
 *
 * <p>This is a different axis from {@link SuppressionSettings}, and the two must not be confused:
 * a gaze's obviousness comes from the <i>watcher's</i> room and changes only what one target
 * perceives; suppression comes from the <i>watched</i> player's rank and is the same for every
 * observer. Ascending does nothing to how quiet a gaze is.
 */
public record GazeSettings(
        boolean enabled,
        boolean notifyOwner,
        double baseRange,
        double rangePerMagnitude,
        int baseDurationTicks,
        int durationPerMagnitude,
        int baseRechargeTicks,
        int rechargeSavedPerMagnitude,
        double openSoulDurationBonus,
        double obviousnessFloor,
        double obviousnessFalloff,
        int noticeCooldownTicks)
{
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_NOTIFY_OWNER = true;

    /** 32 blocks at tier 1, 56 at tier 3. Far enough to pick out a body across a clearing, not across a valley. */
    public static final double DEFAULT_BASE_RANGE = 24d;
    public static final double DEFAULT_RANGE_PER_MAGNITUDE = 8d;

    /** 22.5 seconds at tier 1, 37.5 at tier 3 - long enough to walk a soul's rooms, short enough to be a glimpse. */
    public static final int DEFAULT_BASE_DURATION_TICKS = 300;
    public static final int DEFAULT_DURATION_PER_MAGNITUDE = 150;

    /** 4.5 minutes at tier 1, 3.5 at tier 3. Espionage should be a decision, not a habit. */
    public static final int DEFAULT_BASE_RECHARGE_TICKS = 6000;
    public static final int DEFAULT_RECHARGE_SAVED_PER_MAGNITUDE = 600;

    /**
     * A soul whose owner is inside it is open, and a gaze into one lasts this much longer. One knob,
     * and small, per #187: the symmetry of two bodies left out in the world is doing most of the
     * work of making a meditating target the one worth gazing at, and this is not to be "fixed"
     * upward for being too weak.
     */
    public static final double DEFAULT_OPEN_SOUL_DURATION_BONUS = 1.25d;

    /** The quietest a gaze can ever be: a tier 3 observatory is still noticeable (rule 5 of #181). */
    public static final double DEFAULT_OBVIOUSNESS_FLOOR = 0.25d;

    /** 0.625 at tier 1, 0.4375 at tier 3, with the default floor. */
    public static final double DEFAULT_OBVIOUSNESS_FALLOFF = 1d;

    /**
     * The shortest gap between two notices to the same target (#189). A watcher who gazes, returns
     * and gazes again cannot strobe a target who has no way to answer; the second gaze still lands,
     * it just does not ring the same bell twice in a breath.
     */
    public static final int DEFAULT_NOTICE_COOLDOWN_TICKS = 200;

    public static final GazeSettings DEFAULTS = new GazeSettings(
            DEFAULT_ENABLED,
            DEFAULT_NOTIFY_OWNER,
            DEFAULT_BASE_RANGE,
            DEFAULT_RANGE_PER_MAGNITUDE,
            DEFAULT_BASE_DURATION_TICKS,
            DEFAULT_DURATION_PER_MAGNITUDE,
            DEFAULT_BASE_RECHARGE_TICKS,
            DEFAULT_RECHARGE_SAVED_PER_MAGNITUDE,
            DEFAULT_OPEN_SOUL_DURATION_BONUS,
            DEFAULT_OBVIOUSNESS_FLOOR,
            DEFAULT_OBVIOUSNESS_FALLOFF,
            DEFAULT_NOTICE_COOLDOWN_TICKS);

    public GazeSettings
    {
        if (!(baseRange > 0d) || rangePerMagnitude < 0d)
        {
            throw new IllegalArgumentException("range must be positive and grow with magnitude, got "
                    + baseRange + " + " + rangePerMagnitude);
        }

        if (baseDurationTicks < 1 || durationPerMagnitude < 0)
        {
            throw new IllegalArgumentException("duration must be at least a tick and grow with magnitude, got "
                    + baseDurationTicks + " + " + durationPerMagnitude);
        }

        if (baseRechargeTicks < 1 || rechargeSavedPerMagnitude < 0)
        {
            throw new IllegalArgumentException("recharge must be at least a tick and shrink with magnitude, got "
                    + baseRechargeTicks + " - " + rechargeSavedPerMagnitude);
        }

        if (!(openSoulDurationBonus >= 1d))
        {
            throw new IllegalArgumentException("openSoulDurationBonus is a bonus, and must be at least 1, got "
                    + openSoulDurationBonus);
        }

        // strictly positive: a floor of zero is a gaze that can be made undetectable, which rule 5
        // of #181 rules out at the level of the mechanism rather than trusting a config to respect it
        if (!(obviousnessFloor > 0d) || obviousnessFloor > 1d)
        {
            throw new IllegalArgumentException("obviousnessFloor must be in (0, 1], got " + obviousnessFloor);
        }

        if (!(obviousnessFalloff >= 0d))
        {
            throw new IllegalArgumentException("obviousnessFalloff must not be negative, got " + obviousnessFalloff);
        }

        if (noticeCooldownTicks < 0)
        {
            throw new IllegalArgumentException("noticeCooldownTicks must not be negative, got " + noticeCooldownTicks);
        }
    }

    /** How far the crosshair raycast looks for a target, in blocks. */
    public double rangeFor(double magnitude)
    {
        return this.baseRange + Math.max(0d, magnitude) * this.rangePerMagnitude;
    }

    /**
     * How long a gaze lasts, in ticks. {@code targetHome} is whether the soul's owner was inside it
     * when the gaze landed - see {@link #DEFAULT_OPEN_SOUL_DURATION_BONUS}.
     */
    public int durationFor(double magnitude, boolean targetHome)
    {
        final double base = this.baseDurationTicks + Math.max(0d, magnitude) * this.durationPerMagnitude;
        return (int) Math.round(targetHome ? base * this.openSoulDurationBonus : base);
    }

    /** Ticks to recharge the one charge a gaze banks, before the server's multiplier and floor. */
    public int rechargeTicksFor(double magnitude)
    {
        return Math.max(1, this.baseRechargeTicks - (int) Math.round(Math.max(0d, magnitude) * this.rechargeSavedPerMagnitude));
    }

    /**
     * How obvious a gaze cast at this magnitude is to its target, in {@code (floor, 1]}. Whatever
     * signal the target perceives (#189) scales its own intensity by this. See the class javadoc.
     */
    public double obviousnessFor(double magnitude)
    {
        final double m = Double.isNaN(magnitude) ? 0d : Math.max(0d, magnitude);
        return this.obviousnessFloor + (1d - this.obviousnessFloor) / (1d + this.obviousnessFalloff * m);
    }
}
