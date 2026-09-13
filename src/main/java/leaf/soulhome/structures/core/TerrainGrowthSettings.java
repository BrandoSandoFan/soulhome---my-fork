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
        int soilDepth, int edgeJitter, int chunksPerTick)
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

    /** How deep the apron is cut, before the box floor clamps it - see {@link #soilDepth}. */
    public static final int DEFAULT_SOIL_DEPTH = 4;

    /** How ragged the band's outer edge is allowed to be. A straight offset is a machine-cut collar. */
    public static final int DEFAULT_EDGE_JITTER = 3;

    /** Chunks surveyed or written per server tick. See {@code TerrainGrowthService}. */
    public static final int DEFAULT_CHUNKS_PER_TICK = 4;

    public static final TerrainGrowthSettings DEFAULTS = new TerrainGrowthSettings(
            true, DEFAULT_BASE_GROUND, DEFAULT_GROUND_PER_RANK, DEFAULT_VERGE_MARGIN, DEFAULT_GROUND_BAND,
            DEFAULT_CLEARANCE_MARGIN, DEFAULT_SOIL_DEPTH, DEFAULT_EDGE_JITTER, DEFAULT_CHUNKS_PER_TICK);

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
     * How many layers of the apron actually land, once the box floor has had its say. The box's
     * floor is as real as its ceiling (#79), and generated ground is not exempt from it (#160), so
     * an apron whose surface sits on the floor datum is one layer - a shelf rather than a slab.
     * From the only angle a player standing on it has, that is the island.
     */
    public int layersAt(int surfaceY, int floorY)
    {
        return Math.max(0, Math.min(this.soilDepth, surfaceY - floorY + 1));
    }
}
