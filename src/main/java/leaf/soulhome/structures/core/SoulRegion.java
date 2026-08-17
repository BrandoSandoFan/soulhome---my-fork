/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Map;

/**
 * One candidate structure found in a soulhome.
 *
 * <p>Boundary and contents are tracked separately because they mean different things to a player -
 * a library is as much its walls as its furniture - but the classifier scores over
 * {@link #allBlocks()}, the union of the two. The split exists so feedback can say "you have
 * bookshelves on the walls" rather than just "you have bookshelves".
 *
 * @param type         enclosed room or open-air cluster
 * @param bounds       the box the region occupies, walls included
 * @param boundary     blocks forming the shell: walls, floor, ceiling
 * @param contents     blocks inside the shell: furniture, crops, decoration
 * @param allBlocks    boundary and contents combined; precomputed because the classifier walks it
 *                     once per archetype
 * @param volume       interior cell count for an enclosed room, bounding-box volume for an open
 *                     cluster. Drives the density term in scoring.
 * @param identityHash a stable digest of the region's shape and contents, so an unchanged
 *                     soulhome can skip rescanning
 */
public record SoulRegion(
        RegionType type,
        RegionBounds bounds,
        BlockCounts boundary,
        BlockCounts contents,
        BlockCounts allBlocks,
        int volume,
        long identityHash)
{
    public static SoulRegion create(
            RegionType type,
            RegionBounds bounds,
            BlockCounts boundary,
            BlockCounts contents,
            int volume)
    {
        BlockCounts combined = boundary.plus(contents);
        return new SoulRegion(
                type,
                bounds,
                boundary,
                contents,
                combined,
                volume,
                computeIdentityHash(type, bounds, combined));
    }

    /**
     * Order-independent digest of what this region is made of. Two scans of an untouched build
     * must agree, and any block placed or broken inside it must not.
     */
    private static long computeIdentityHash(RegionType type, RegionBounds bounds, BlockCounts blocks)
    {
        long hash = 1125899906842597L;

        hash = hash * 31 + type.ordinal();
        hash = hash * 31 + bounds.minX();
        hash = hash * 31 + bounds.minY();
        hash = hash * 31 + bounds.minZ();
        hash = hash * 31 + bounds.maxX();
        hash = hash * 31 + bounds.maxY();
        hash = hash * 31 + bounds.maxZ();

        // sortedEntries gives a stable order regardless of how the world was walked
        for (Map.Entry<BlockSignature, Integer> entry : blocks.sortedEntries())
        {
            hash = hash * 31 + entry.getKey().id().hashCode();
            hash = hash * 31 + entry.getValue();
        }

        return hash;
    }

    @Override
    public String toString()
    {
        return "SoulRegion{" + this.type.getSerializedName() + " " + this.bounds
                + " volume=" + this.volume
                + " boundary=" + this.boundary.total()
                + " contents=" + this.contents.total() + "}";
    }
}
