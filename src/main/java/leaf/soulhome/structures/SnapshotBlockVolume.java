/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.BlockSignature;
import leaf.soulhome.structures.core.BlockVolume;
import leaf.soulhome.structures.core.Passability;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Optional;

/**
 * A copy of a slice of a {@link ServerLevel}, taken once so that region detection can then run
 * anywhere.
 *
 * <p>Scanning reads a few hundred thousand block positions. Doing that straight off a live level
 * from a worker thread is not safe, and doing it on the server thread is a stutter, so the level
 * is read once - on the server thread, where {@link #capture} must be called - and the scan works
 * from the copy.
 */
public final class SnapshotBlockVolume implements BlockVolume
{
    /**
     * Cells of empty space kept around the build. The outside-in flood fill seeds from the faces
     * of this box, so the build needs room to be surrounded or every room would read as open to
     * the sky.
     */
    private static final int PADDING = 2;

    /** Cached: {@code values()} allocates, and this is read once per block position per scan. */
    private static final Passability[] PASSABILITY_VALUES = Passability.values();

    /** How far out from the origin to look for chunks. Soulhomes are a single small island. */
    private static final int SEARCH_CHUNK_RADIUS = 8;

    private final RegionBounds bounds;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] passability;
    private final BlockSignature[] signatures;

    private SnapshotBlockVolume(RegionBounds bounds)
    {
        this.bounds = bounds;
        this.sizeY = bounds.sizeY();
        this.sizeZ = bounds.sizeZ();

        final int cells = (int) bounds.volume();
        this.passability = new byte[cells];
        this.signatures = new BlockSignature[cells];
    }

    /**
     * Copy the given box out of the level. <b>Server thread only.</b>
     */
    public static SnapshotBlockVolume capture(ServerLevel level, RegionBounds bounds)
    {
        SnapshotBlockVolume snapshot = new SnapshotBlockVolume(bounds);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
        {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
            {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                {
                    cursor.set(x, y, z);

                    final BlockState state = level.getBlockState(cursor);
                    final int index = snapshot.index(x, y, z);
                    final Passability passability = passabilityOf(level, cursor, state);

                    snapshot.passability[index] = (byte) passability.ordinal();

                    if (passability != Passability.EMPTY)
                    {
                        snapshot.signatures[index] = StateSignature.of(state);
                    }
                }
            }
        }

        return snapshot;
    }

    private static Passability passabilityOf(ServerLevel level, BlockPos pos, BlockState state)
    {
        if (state.isAir())
        {
            return Passability.EMPTY;
        }

        // doors, trapdoors and gates are walls whether they are open or shut. Treating an open
        // door as a leak would make a player's buffs blink out as they walked through their own
        // front door, and would merge every room joined by a corridor into one.
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES))
        {
            return Passability.BLOCKING;
        }

        // torches, carpets, crops, flowers, water: part of the build, but the room's air flows
        // through them
        return state.getCollisionShape(level, pos).isEmpty() ? Passability.PASSABLE : Passability.BLOCKING;
    }

    /**
     * The box worth scanning in this soulhome, derived from which chunk sections actually hold
     * blocks rather than from a fixed guess. A soulhome is one small island in an otherwise empty
     * 384-block-tall void; sweeping all of it would be almost entirely wasted work.
     *
     * @return empty when the dimension holds nothing at all
     */
    public static Optional<RegionBounds> populatedBounds(ServerLevel level)
    {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int chunkX = -SEARCH_CHUNK_RADIUS; chunkX <= SEARCH_CHUNK_RADIUS; chunkX++)
        {
            for (int chunkZ = -SEARCH_CHUNK_RADIUS; chunkZ <= SEARCH_CHUNK_RADIUS; chunkZ++)
            {
                // hasChunk first: getChunk would generate anything missing, and generating the
                // whole search square every scan would be a fine way to melt a server
                if (!level.hasChunk(chunkX, chunkZ))
                {
                    continue;
                }

                final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                final LevelChunkSection[] sections = chunk.getSections();

                for (int index = 0; index < sections.length; index++)
                {
                    if (sections[index].hasOnlyAir())
                    {
                        continue;
                    }

                    final int sectionMinY = (level.getMinSection() + index) << 4;

                    minX = Math.min(minX, chunkX << 4);
                    maxX = Math.max(maxX, (chunkX << 4) + 15);
                    minZ = Math.min(minZ, chunkZ << 4);
                    maxZ = Math.max(maxZ, (chunkZ << 4) + 15);
                    minY = Math.min(minY, sectionMinY);
                    maxY = Math.max(maxY, sectionMinY + 15);
                }
            }
        }

        if (minX > maxX)
        {
            return Optional.empty();
        }

        return Optional.of(new RegionBounds(
                minX - PADDING,
                Math.max(level.getMinBuildHeight(), minY - PADDING),
                minZ - PADDING,
                maxX + PADDING,
                Math.min(level.getMaxBuildHeight() - 1, maxY + PADDING),
                maxZ + PADDING));
    }

    /**
     * Convenience for the common path: work out what to scan and copy it, or report why not.
     * <b>Server thread only.</b>
     */
    public static Optional<SnapshotBlockVolume> capture(ServerLevel level)
    {
        Optional<RegionBounds> bounds = populatedBounds(level);

        if (bounds.isEmpty())
        {
            return Optional.empty();
        }

        final RegionBounds box = bounds.get();

        if (box.volume() > Integer.MAX_VALUE)
        {
            LogHelper.warn("Soulhome " + level.dimension().location() + " spans " + box
                    + ", which is too large to snapshot. Skipping structure scan.");
            return Optional.empty();
        }

        return Optional.of(capture(level, box));
    }

    @Override
    public RegionBounds bounds()
    {
        return this.bounds;
    }

    @Override
    public Passability passabilityAt(int x, int y, int z)
    {
        return PASSABILITY_VALUES[this.passability[index(x, y, z)]];
    }

    @Override
    public BlockSignature signatureAt(int x, int y, int z)
    {
        return this.signatures[index(x, y, z)];
    }

    private int index(int x, int y, int z)
    {
        final int dx = x - this.bounds.minX();
        final int dy = y - this.bounds.minY();
        final int dz = z - this.bounds.minZ();
        return (dx * this.sizeY + dy) * this.sizeZ + dz;
    }
}
