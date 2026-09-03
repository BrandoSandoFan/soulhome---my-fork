/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The soul-residue tap of Sublime Essence (#82): how fast a soulhome earns residue from its own
 * built quality, and how much residue converts into one unit of Essence I.
 *
 * <p>Deliberately sublinear in score - "the player with nineteen tier-3 rooms earns perhaps three
 * times what a player with four does, not twenty times" - so this is a square root, not the score
 * itself. A square root also gives the one behaviour #82's acceptance criteria actually pins down:
 * zero score is zero residue, so an empty soulhome never accrues anything, however long it sits
 * there.
 *
 * <p>Lives here, not in {@code SoulHomeBuffData}, for the same reason every other rate lives in
 * {@code structures.core}: the curve is a rule worth testing on its own, without a
 * {@code ServerLevel} or a saved-data file in the way.
 */
public record EssenceSettings(double residueRateMultiplier, double residueToEssenceRate)
{
    public static final double DEFAULT_RESIDUE_RATE_MULTIPLIER = 1.0d;

    /** How much residue converts into one Essence I - a balance-pass number, not a load-bearing one. */
    public static final double DEFAULT_RESIDUE_TO_ESSENCE_RATE = 100.0d;

    public static final EssenceSettings DEFAULTS =
            new EssenceSettings(DEFAULT_RESIDUE_RATE_MULTIPLIER, DEFAULT_RESIDUE_TO_ESSENCE_RATE);

    public EssenceSettings
    {
        if (residueRateMultiplier < 0)
        {
            throw new IllegalArgumentException("residueRateMultiplier must not be negative, got " + residueRateMultiplier);
        }

        if (residueToEssenceRate <= 0)
        {
            throw new IllegalArgumentException("residueToEssenceRate must be positive, got " + residueToEssenceRate);
        }
    }

    /**
     * Soul residue earned over one span of real time by a soulhome whose rooms currently total
     * {@code totalScore}. Not tied to whether anyone was online to watch it happen - #82 rules out
     * both a daily cap and an online-time gate, so this is pure elapsed wall-clock time against the
     * soulhome's last known score, for whoever last measured the gap to add in.
     *
     * @return 0 for a soulhome with nothing built, or for a non-positive span
     */
    public double residueGained(double totalScore, long elapsedMillis)
    {
        if (totalScore <= 0 || elapsedMillis <= 0)
        {
            return 0;
        }

        final double perSecond = this.residueRateMultiplier * Math.sqrt(totalScore);
        return perSecond * (elapsedMillis / 1000.0);
    }
}
