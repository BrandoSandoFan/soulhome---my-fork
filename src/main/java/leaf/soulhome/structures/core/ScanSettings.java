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
 * @param clusterRadius    Chebyshev distance within which two signal blocks join the same open-air
 *                         cluster
 * @param minClusterSize   signal blocks an open-air cluster needs before it counts as a structure,
 *                         so a single planted flower is not a farm
 * @param maxRegions       hard cap on regions returned, to bound downstream classification cost
 * @param maxScannedCells  refuse to scan a volume larger than this; the caller should log and skip
 * @param maxGeometryCells per-region cap on how many structurally-interesting block positions
 *                         {@link RegionGeometry} will index. Past this the index is truncated and
 *                         reports itself as such, rather than silently scoring an arrangement badly
 *                         for a reason that has nothing to do with the build.
 */
public record ScanSettings(
        int maxRoomVolume,
        int clusterRadius,
        int minClusterSize,
        int maxRegions,
        long maxScannedCells,
        int maxGeometryCells)
{
    /** Suggested default - see the field javadoc above. */
    public static final int DEFAULT_MAX_GEOMETRY_CELLS = 8192;

    public static final ScanSettings DEFAULTS =
            new ScanSettings(4096, 4, 4, 64, 4_000_000L, DEFAULT_MAX_GEOMETRY_CELLS);

    /** The common case: no geometry indexing limit beyond the suggested default. */
    public ScanSettings(int maxRoomVolume, int clusterRadius, int minClusterSize, int maxRegions, long maxScannedCells)
    {
        this(maxRoomVolume, clusterRadius, minClusterSize, maxRegions, maxScannedCells, DEFAULT_MAX_GEOMETRY_CELLS);
    }

    public ScanSettings
    {
        if (maxRoomVolume < 1)
        {
            throw new IllegalArgumentException("maxRoomVolume must be positive, got " + maxRoomVolume);
        }

        if (clusterRadius < 1)
        {
            throw new IllegalArgumentException("clusterRadius must be positive, got " + clusterRadius);
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
    }
}
