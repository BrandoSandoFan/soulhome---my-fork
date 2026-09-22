/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * How much ground an ascension adds, and in what shape (#158/#159).
 *
 * <p>Two numbers decide the extent, and they are deliberately different numbers. {@link
 * #groundLimit} is a hard square no generated ground ever crosses, and it lags the verge by {@link
 * #vergeMargin} so there is always open void between the apron and the wall - somewhere to build
 * outward into, and the thing that makes "your walls are at 104, your ground reaches 78" a
 * distinction a player can see rather than a bug they report. {@link #bandWidth} is how far outward
 * from the ground that already exists one run of growth reaches, and it is measured from the rank
 * growth last completed rather than from the rank before this one - so a soulhome that was already
 * rank III before growth shipped, and a soulhome whose server stopped halfway through a band, both
 * catch up by the same arithmetic as an ordinary ascension. There is no separate "has this rank
 * been grown" flag and so no duplicate event to guard against.
 *
 * <p>The defaults are sized against the shipped verge (24 base, 16 per rank): ground reaches 30 at
 * rank I against walls at 40, and 78 at rank V against walls at 104.
 */
public record TerrainGrowthSettings(
        boolean enabled, int baseGround, int groundPerRank, int vergeMargin, int groundBand, int clearanceMargin,
        int soilDepth, int rimDepth, int edgeJitter, int chunksPerTick)
{
    /**
     * How far ground may reach at rank 0 before the verge margin has its say. Not itself a promise
     * that a rank 0 soul has ground out to 18 - growth never runs at rank 0, since there is nothing
     * to catch up to - only the point the ladder is measured from.
     */
    public static final int DEFAULT_BASE_GROUND = 18;

    /** Deliberately below {@code verge_per_rank}: the walls are meant to outrun the ground. */
    public static final int DEFAULT_GROUND_PER_RANK = 12;

    /** How much open verge is kept between the apron and the wall, at every rank. */
    public static final int DEFAULT_VERGE_MARGIN = 6;

    /**
     * How far above the floor a column's highest block may sit and still read as ground rather than
     * as something built. Three is enough for a patio, a path or a laid-out floor to seed growth,
     * and short of anything with walls.
     */
    public static final int DEFAULT_GROUND_BAND = 3;

    /** How wide a moat is kept around anything built. Generating too little ground is the mild failure. */
    public static final int DEFAULT_CLEARANCE_MARGIN = 3;

    /**
     * How deep the apron is cut where it meets the island's own ground - see {@link #depthAt}. Raised
     * from the epic's original 4 (#235): against a shipped island's 26-32 block body, a 4-thick collar
     * still read as a shelf nailed on rather than as the island's own underside continuing outward.
     */
    public static final int DEFAULT_SOIL_DEPTH = 8;

    /**
     * How deep the apron is cut at the far edge of its own band, whatever {@link #soilDepth} says at
     * the island side. Never zero - a rim of no thickness at all is a coastline with a crack in it,
     * one block wide, the whole way round.
     */
    public static final int DEFAULT_RIM_DEPTH = 2;

    /** How ragged the band's outer edge is allowed to be. A straight offset is a machine-cut collar. */
    public static final int DEFAULT_EDGE_JITTER = 3;

    /** Chunks surveyed or written per server tick. See {@code TerrainGrowthService}. */
    public static final int DEFAULT_CHUNKS_PER_TICK = 4;

    public static final TerrainGrowthSettings DEFAULTS = new TerrainGrowthSettings(
            true, DEFAULT_BASE_GROUND, DEFAULT_GROUND_PER_RANK, DEFAULT_VERGE_MARGIN, DEFAULT_GROUND_BAND,
            DEFAULT_CLEARANCE_MARGIN, DEFAULT_SOIL_DEPTH, DEFAULT_RIM_DEPTH, DEFAULT_EDGE_JITTER,
            DEFAULT_CHUNKS_PER_TICK);

    public TerrainGrowthSettings
    {
        if (baseGround < 0)
        {
            throw new IllegalArgumentException("baseGround must not be negative, got " + baseGround);
        }

        if (groundPerRank < 0)
        {
            throw new IllegalArgumentException("groundPerRank must not be negative, got " + groundPerRank);
        }

        if (vergeMargin < 0)
        {
            throw new IllegalArgumentException("vergeMargin must not be negative, got " + vergeMargin);
        }

        if (groundBand < 0)
        {
            throw new IllegalArgumentException("groundBand must not be negative, got " + groundBand);
        }

        if (clearanceMargin < 0)
        {
            throw new IllegalArgumentException("clearanceMargin must not be negative, got " + clearanceMargin);
        }

        if (soilDepth < 1)
        {
            throw new IllegalArgumentException("soilDepth must be at least 1, got " + soilDepth);
        }

        if (rimDepth < 1 || rimDepth > soilDepth)
        {
            throw new IllegalArgumentException(
                    "rimDepth must be between 1 and soilDepth (" + soilDepth + "), got " + rimDepth);
        }

        if (edgeJitter < 0)
        {
            throw new IllegalArgumentException("edgeJitter must not be negative, got " + edgeJitter);
        }

        if (chunksPerTick < 1)
        {
            throw new IllegalArgumentException("chunksPerTick must be at least 1, got " + chunksPerTick);
        }
    }

    /**
     * The hard square, in half-extent from the origin, that generated ground may never cross at
     * this rank. Never less than 1, and never so large that it would reach the wall: a pack that
     * sets {@code verge_margin} wider than its own verge gets a one-block island rather than an
     * exception.
     */
    public int groundLimit(int rank, int vergeHalfExtent)
    {
        final int fromRank = this.baseGround + Math.max(0, rank) * this.groundPerRank;
        final int fromVerge = vergeHalfExtent - this.vergeMargin;

        return Math.max(1, Math.min(fromRank, fromVerge));
    }

    /**
     * How far outward from existing ground one run of growth reaches, catching {@code grownRank} up
     * to {@code rank}. Zero when there is nothing to catch up, which is what makes a repeated
     * trigger for a rank already grown a no-op rather than a second apron.
     */
    public int bandWidth(int rank, int grownRank)
    {
        return Math.max(0, rank - Math.max(0, grownRank)) * this.groundPerRank;
    }

    /**
     * How many layers deep one column of apron is cut, tapering from {@link #soilDepth} where the
     * band meets the island's own ground down to {@link #rimDepth} at the far edge of its own reach
     * (#235). A flat slab the same thickness at the coast as at the shore reads as a shelf nailed on;
     * a taper reads as the island's own underside continuing outward, which is what an apron is meant
     * to look like it is.
     *
     * <p>The box floor does not clamp this any more than it clamps the island's own body - the mod
     * already ships every island 25-31 blocks below the floor datum (#97), so an apron the player
     * cannot dig through the bottom of is consistent with the status quo rather than a new
     * restriction. Whether the floor datum belongs where it sits at all is #236, and is not this
     * method's question to answer.
     *
     * <p>Never deeper than {@code sourceDepth}, the column this apron actually grew from - a shallow
     * shelf grows a shallow apron rather than staging soil under thin air where the source ran out.
     *
     * @param bandDistance how many blocks out from the nearest existing ground this column sits
     * @param bandWidth    how wide this run of growth reaches at its own full extent, before the
     *                     edge jitter has its say - zero only for a band that should not exist, in
     *                     which case there is nothing to taper against and {@link #soilDepth} alone
     *                     decides, capped by the source as always
     * @param sourceDepth  how many solid layers the ground column this apron grew from actually has
     */
    public int depthAt(int bandDistance, int bandWidth, int sourceDepth)
    {
        if (sourceDepth <= 0)
        {
            return 0;
        }

        if (bandWidth <= 0)
        {
            return Math.min(this.soilDepth, sourceDepth);
        }

        final int clampedDistance = Math.max(0, Math.min(bandDistance, bandWidth));
        final int span = this.soilDepth - this.rimDepth;
        final int tapered = this.soilDepth - (span * clampedDistance) / bandWidth;

        return Math.min(tapered, sourceDepth);
    }
}
