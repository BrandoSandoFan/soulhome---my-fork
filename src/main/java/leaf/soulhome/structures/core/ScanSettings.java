/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Knobs for {@link RegionScanner}. These become Forge config entries in the balance pass; until
 * then {@link #DEFAULTS} is the single source of truth.
 *
 * @param maxRoomVolume    interior cells above which a pocket is treated as outdoors rather than a
 *                         room. A cathedral is not a room.
 * @param clusterRadius    how far an open-air cluster will reach through clear space to pick up the
 *                         next signal block. Measured as steps through cells the cluster can
 *                         actually cross, not as a straight line, so a wall between two fields is a
 *                         boundary rather than something the cluster tunnels under. Only a block
 *                         filling its cell stops the spread - see {@link Passability} - so the
 *                         fencing around a track does not cut it off from its own trackside.
 * @param minClusterSize   signal blocks an open-air cluster needs before it counts as a structure,
 *                         so a single planted flower is not a farm
 * @param maxRegions       hard cap on regions returned, to bound downstream classification cost
 * @param maxScannedCells  refuse to scan a volume larger than this; the caller should log and skip
 * @param maxGeometryCells per-region cap on how many structurally-interesting block positions
 *                         {@link RegionGeometry} will index. Past this the index is truncated and
 *                         reports itself as such, rather than silently scoring an arrangement badly
 *                         for a reason that has nothing to do with the build.
 * @param minRoomVolume    interior cells below which a sealed pocket is a crevice rather than a
 *                         room. Every complex build ends up with voids inside a thick wall, under a
 *                         stair or up a hollow pillar; offering each of them as a region is noise a
 *                         player then has to look at in the lens.
 * @param shellDepth       how many further layers of solid blocks packed against a room's shell
 *                         still belong to that building. The shell itself is only the layer touching
 *                         the room's air, so without this a barn's roof, the outer half of a
 *                         double-thick wall and even a box's own corners are loose blocks that go on
 *                         to seed a phantom open-air region on top of the building. Claimed, not
 *                         scored: this decides who owns a block, not what a room is worth.
 */
public record ScanSettings(
        int maxRoomVolume,
        int clusterRadius,
        int minClusterSize,
        int maxRegions,
        long maxScannedCells,
        int maxGeometryCells,
        int minRoomVolume,
        int shellDepth)
{
    /** Suggested default - see the field javadoc above. */
    public static final int DEFAULT_MAX_GEOMETRY_CELLS = 8192;

    /**
     * The scanner tracks a cluster's remaining reach as one byte per cell, so the radius has to fit
     * in one. Well above anything a soulhome-sized build could want: at 64 a single cluster would
     * bridge two builds at opposite ends of a chunk.
     */
    public static final int MAX_CLUSTER_RADIUS = 64;

    /** Suggested default - a pocket smaller than two blocks each way is a crevice, not a room. */
    public static final int DEFAULT_MIN_ROOM_VOLUME = 8;

    /**
     * Suggested default. One layer is what the cases this exists for need: a roof laid straight
     * onto a ceiling, the outer half of a double-thick wall, and the corners and edges of a plain
     * box, which touch no interior air and so are never part of the shell.
     */
    public static final int DEFAULT_SHELL_DEPTH = 1;

    public static final ScanSettings DEFAULTS = new ScanSettings(
            4096, 3, 4, 64, 4_000_000L, DEFAULT_MAX_GEOMETRY_CELLS,
            DEFAULT_MIN_ROOM_VOLUME, DEFAULT_SHELL_DEPTH);

    /** The common case: no geometry indexing limit beyond the suggested default. */
    public ScanSettings(int maxRoomVolume, int clusterRadius, int minClusterSize, int maxRegions, long maxScannedCells)
    {
        this(maxRoomVolume, clusterRadius, minClusterSize, maxRegions, maxScannedCells, DEFAULT_MAX_GEOMETRY_CELLS);
    }

    /** As above, but naming a geometry cap. Room and shell limits stay at their suggested defaults. */
    public ScanSettings(
            int maxRoomVolume,
            int clusterRadius,
            int minClusterSize,
            int maxRegions,
            long maxScannedCells,
            int maxGeometryCells)
    {
        this(maxRoomVolume, clusterRadius, minClusterSize, maxRegions, maxScannedCells, maxGeometryCells,
                DEFAULT_MIN_ROOM_VOLUME, DEFAULT_SHELL_DEPTH);
    }

    /**
     * Both ends at once, because they are checked against each other: setting them one at a time
     * would refuse a perfectly good pair on the way through.
     */
    public ScanSettings withRoomVolumeRange(int min, int max)
    {
        return new ScanSettings(max, this.clusterRadius, this.minClusterSize, this.maxRegions,
                this.maxScannedCells, this.maxGeometryCells, min, this.shellDepth);
    }

    public ScanSettings withClusterRadius(int radius)
    {
        return new ScanSettings(this.maxRoomVolume, radius, this.minClusterSize, this.maxRegions,
                this.maxScannedCells, this.maxGeometryCells, this.minRoomVolume, this.shellDepth);
    }

    public ScanSettings withShellDepth(int depth)
    {
        return new ScanSettings(this.maxRoomVolume, this.clusterRadius, this.minClusterSize, this.maxRegions,
                this.maxScannedCells, this.maxGeometryCells, this.minRoomVolume, depth);
    }

    public ScanSettings
    {
        if (maxRoomVolume < 1)
        {
            throw new IllegalArgumentException("maxRoomVolume must be positive, got " + maxRoomVolume);
        }

        if (clusterRadius < 1 || clusterRadius > MAX_CLUSTER_RADIUS)
        {
            throw new IllegalArgumentException(
                    "clusterRadius must be between 1 and " + MAX_CLUSTER_RADIUS + ", got " + clusterRadius);
        }

        if (minClusterSize < 1)
        {
            throw new IllegalArgumentException("minClusterSize must be positive, got " + minClusterSize);
        }

        if (maxRegions < 1)
        {
            throw new IllegalArgumentException("maxRegions must be positive, got " + maxRegions);
        }

        if (maxScannedCells < 1)
        {
            throw new IllegalArgumentException("maxScannedCells must be positive, got " + maxScannedCells);
        }

        if (maxGeometryCells < 1)
        {
            throw new IllegalArgumentException("maxGeometryCells must be positive, got " + maxGeometryCells);
        }

        if (minRoomVolume < 1)
        {
            throw new IllegalArgumentException("minRoomVolume must be positive, got " + minRoomVolume);
        }

        if (minRoomVolume > maxRoomVolume)
        {
            // a hand-edited file can hold this, and it would silently find no rooms at all
            throw new IllegalArgumentException(
                    "minRoomVolume " + minRoomVolume + " is above maxRoomVolume " + maxRoomVolume
                            + ", which would leave no pocket able to be a room");
        }

        if (shellDepth < 0)
        {
            throw new IllegalArgumentException("shellDepth cannot be negative, got " + shellDepth);
        }
    }
}
