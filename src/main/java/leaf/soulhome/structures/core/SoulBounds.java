/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The box a soulhome may be built inside of, by ascension rank: a floor, a ceiling and four walls.
 * See #78/#79 - a ceiling alone is not a limit in a void dimension, because a player denied a
 * second storey simply builds one downward instead, so the floor is exactly as load-bearing as the
 * ceiling.
 *
 * <p>{@link #DEFAULT_FLOOR_Y} is 70, matching {@code DimensionHelper.FLOOR_LEVEL} rather than the
 * 64 first drafted for this epic before anyone checked it against the code. #97 found the
 * mismatch: the entry point sits at {@code FLOOR_LEVEL + 2}, so a floor datum of 64 would place
 * every player's own arrival point six layers above their soulhome's floor, comfortably inside
 * what should be unbuildable void. Anchoring the floor at 70 instead keeps a fresh soulhome's own
 * ground - the surface the player is standing on the instant they arrive - inside the box from the
 * first tick. This package cannot import {@code DimensionHelper} to enforce the two constants
 * staying equal directly (it stays Minecraft-free on purpose); keep them in sync by hand.
 *
 * <p>Rank is not tracked yet - the ascension mechanism that raises it is a later stage of the same
 * epic (#82-#84) - so every caller today passes rank 0 and gets the starting box.
 */
public record SoulBounds(int floorY, int ceilingY, int vergeHalfExtent)
{
    /** See the class javadoc for why this is not the 64 the epic issue first proposed. */
    public static final int DEFAULT_FLOOR_Y = 70;

    /** Build layers at rank 0: one storey - a floor, four of air, a ceiling. Deliberately mean. */
    public static final int DEFAULT_BASE_CEILING_HEIGHT = 6;

    public static final int DEFAULT_CEILING_HEIGHT_PER_RANK = 6;

    public static final int DEFAULT_BASE_VERGE = 24;

    public static final int DEFAULT_VERGE_PER_RANK = 16;

    /** Ranks run 0 (unascended) to 5 (V). */
    public static final int MAX_RANK = 5;

    public SoulBounds
    {
        if (ceilingY <= floorY)
        {
            throw new IllegalArgumentException("ceilingY " + ceilingY + " must be above floorY " + floorY);
        }

        if (vergeHalfExtent < 1)
        {
            throw new IllegalArgumentException("vergeHalfExtent must be positive, got " + vergeHalfExtent);
        }
    }

    /**
     * The box for one rank, from the config knobs under {@code ascent}. Rank is clamped to
     * {@code [0, MAX_RANK]} rather than rejected outright - a corrupt or future save holding an
     * out-of-range rank should not take a scan down with it.
     */
    public static SoulBounds forRank(
            int rank, int floorY, int baseCeilingHeight, int ceilingHeightPerRank, int baseVerge, int vergePerRank)
    {
        final int clampedRank = Math.max(0, Math.min(MAX_RANK, rank));

        return new SoulBounds(
                floorY,
                floorY + baseCeilingHeight + clampedRank * ceilingHeightPerRank,
                baseVerge + clampedRank * vergePerRank);
    }

    /** As above, at the suggested defaults - what a fresh install reads before any config exists. */
    public static SoulBounds forRank(int rank)
    {
        return forRank(rank, DEFAULT_FLOOR_Y, DEFAULT_BASE_CEILING_HEIGHT, DEFAULT_CEILING_HEIGHT_PER_RANK,
                DEFAULT_BASE_VERGE, DEFAULT_VERGE_PER_RANK);
    }

    /**
     * Whether a block may be placed at this position. The floor is inclusive (its own layer is the
     * lowest buildable one) and the ceiling is exclusive (it is the boundary the firmament sits on,
     * not itself a buildable layer) - together they make {@link #buildLayers()} the true count of
     * usable Y values.
     */
    public boolean contains(int x, int y, int z)
    {
        return y >= this.floorY && y < this.ceilingY
                && x >= -this.vergeHalfExtent && x <= this.vergeHalfExtent
                && z >= -this.vergeHalfExtent && z <= this.vergeHalfExtent;
    }

    /** How many Y values are actually buildable - the floor's own layer through one below the ceiling. */
    public int buildLayers()
    {
        return this.ceilingY - this.floorY;
    }

    /**
     * This box as an inclusive {@link RegionBounds}, for the scanner and scan-box code that already
     * work in inclusive coordinates. The ceiling is exclusive for placement but the region's own
     * top layer is the last buildable one, so it becomes {@code ceilingY - 1} here.
     */
    public RegionBounds toRegionBounds()
    {
        return new RegionBounds(
                -this.vergeHalfExtent, this.floorY, -this.vergeHalfExtent,
                this.vergeHalfExtent, this.ceilingY - 1, this.vergeHalfExtent);
    }
}
