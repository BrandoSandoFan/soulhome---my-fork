/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The four requirements the ascension ritual (#83) is judged against, everything except the
 * pillar itself (that is {@link PillarInspector}, since it needs a {@link BlockVolume} rather than
 * a number).
 *
 * <p>Willpower rises the same way the box does in {@link SoulBounds} - a base amount for the first
 * rank plus a further step per rank after it - rather than as a five-entry table, so a pack that
 * lengthens {@code max_rank} gets a threshold for every rank without a table to extend alongside
 * it. These are a balance-pass set of numbers against the shipped archetypes, not load-bearing
 * ones; see {@code ascent.willpower} in the config for the knobs.
 */
public record AscensionSettings(
        int essenceCountPerRank, int ritualDurationTicks, double baseWillpowerThreshold, double willpowerPerRank,
        int pillarSearchRadius)
{
    /** "4 x Sublime Essence N" - #83's own number, but still a knob rather than a constant. */
    public static final int DEFAULT_ESSENCE_COUNT_PER_RANK = 4;

    /** 30 seconds at 20 ticks/second - #83's suggested starting duration. */
    public static final int DEFAULT_RITUAL_DURATION_TICKS = 600;

    public static final double DEFAULT_BASE_WILLPOWER_THRESHOLD = 50.0d;

    public static final double DEFAULT_WILLPOWER_PER_RANK = 50.0d;

    /** "A few blocks" - #83's own phrase for how far the pillar's base may sit from the anchor. */
    public static final int DEFAULT_PILLAR_SEARCH_RADIUS = 4;

    public static final AscensionSettings DEFAULTS = new AscensionSettings(
            DEFAULT_ESSENCE_COUNT_PER_RANK, DEFAULT_RITUAL_DURATION_TICKS, DEFAULT_BASE_WILLPOWER_THRESHOLD,
            DEFAULT_WILLPOWER_PER_RANK, DEFAULT_PILLAR_SEARCH_RADIUS);

    public AscensionSettings
    {
        if (essenceCountPerRank < 1)
        {
            throw new IllegalArgumentException("essenceCountPerRank must be at least 1, got " + essenceCountPerRank);
        }

        if (ritualDurationTicks < 1)
        {
            throw new IllegalArgumentException("ritualDurationTicks must be at least 1, got " + ritualDurationTicks);
        }

        if (baseWillpowerThreshold < 0)
        {
            throw new IllegalArgumentException("baseWillpowerThreshold must not be negative, got " + baseWillpowerThreshold);
        }

        if (willpowerPerRank < 0)
        {
            throw new IllegalArgumentException("willpowerPerRank must not be negative, got " + willpowerPerRank);
        }

        if (pillarSearchRadius < PillarInspector.MIN_BASE_SIZE - 1)
        {
            throw new IllegalArgumentException(
                    "pillarSearchRadius must be at least " + (PillarInspector.MIN_BASE_SIZE - 1)
                            + " for a " + PillarInspector.MIN_BASE_SIZE + "x" + PillarInspector.MIN_BASE_SIZE
                            + " base to ever fit, got " + pillarSearchRadius);
        }
    }

    /**
     * Total awarded room score a soulhome needs to ascend to {@code targetRank} - rank 1 costs
     * {@link #baseWillpowerThreshold}, and every further rank adds {@link #willpowerPerRank}.
     */
    public double willpowerRequired(int targetRank)
    {
        return this.baseWillpowerThreshold + Math.max(0, targetRank - 1) * this.willpowerPerRank;
    }
}
