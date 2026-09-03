/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.structures.core.BlockSignature;
import leaf.soulhome.structures.core.BlockVolume;
import leaf.soulhome.structures.core.Passability;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.RegionScanner;
import leaf.soulhome.structures.core.ScanSettings;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    /**
     * Package-private rather than private: {@code LiveBlockVolume} (#83) reuses this exact
     * judgment for the ascension ritual's pillar check, live against the world rather than off a
     * captured snapshot - the ritual re-checks every tick, and a pillar has to be seen as solid or
     * not by the same rule a scan would use, or the two could disagree about the same block.
     */
    static Passability passabilityOf(ServerLevel level, BlockPos pos, BlockState state)
    {
        if (state.isAir())
        {
            return Passability.EMPTY;
        }

        // doors, trapdoors and gates seal a room whether they are open or shut. Treating an open
        // door as a leak would make a player's buffs blink out as they walked through their own
        // front door, and would merge every room joined by a corridor into one. PARTIAL rather
        // than BLOCKING: a door is a way through a wall, not a wall in its own right.
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES))
        {
            return Passability.PARTIAL;
        }

        // ladders and vines are how a player gets between floors, not a wall between them. Vines
        // already fall out with an empty collision shape below, but a ladder's is a thin box
        // against the wall it's mounted on - non-empty and short of a full cube - so without this
        // it reads as PARTIAL and seals whatever gap it happens to be plugging. A single-wide
        // shaft between two floors, connected only by a ladder, would then score as two sealed
        // rooms instead of one build the player can walk between, purely because the ladder's
        // footprint happened to cover the whole hole.
        if (state.is(BlockTags.CLIMBABLE))
        {
            return Passability.PASSABLE;
        }

        final VoxelShape collision = state.getCollisionShape(level, pos);

        // torches, carpets, crops, flowers, water: part of the build, but the room's air flows
        // through them
        if (collision.isEmpty())
        {
            return Passability.PASSABLE;
        }

        // whether the block fills its cell, which is what separates a wall from a fence. Read off
        // the collision shape we already have rather than from a tag, so a modded block is judged
        // by what it does rather than by whether anyone remembered to tag it. A handful of vanilla
        // blocks are a pixel short of a full cube - farmland and soul sand among them - and so
        // come out PARTIAL; that is the right answer for both, since neither is a wall.
        return Block.isShapeFullBlock(collision) ? Passability.BLOCKING : Passability.PARTIAL;
    }

    /**
     * Whether any of this soulhome is loaded right now.
     *
     * <p>The single most important question a scan can ask, and the reason it is asked separately
     * from {@link #populatedBounds}: an unloaded dimension and an empty one look identical to a
     * block-by-block sweep, and recording "there is nothing here" for the first would delete
     * everything the owner built. A soul dimension holds no chunks of its own accord - nothing
     * keeps them loaded once its owner walks out - so this is false far more often than not.
     */
    public static boolean hasLoadedChunks(ServerLevel level)
    {
        for (int chunkX = -SEARCH_CHUNK_RADIUS; chunkX <= SEARCH_CHUNK_RADIUS; chunkX++)
        {
            for (int chunkZ = -SEARCH_CHUNK_RADIUS; chunkZ <= SEARCH_CHUNK_RADIUS; chunkZ++)
            {
                if (level.hasChunk(chunkX, chunkZ))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * As {@link #hasLoadedChunks(ServerLevel)}, but restricted to one box's own chunks rather than
     * the whole search square.
     *
     * <p>Once the scan box is declared (#79) rather than inferred, checking the full search square
     * would call a soulhome "readable" on the strength of chunks nowhere near its actual box - and,
     * just as wrongly, call it unreadable over a chunk far outside the box that happens not to be
     * loaded. The box being scanned is the only thing that matters here.
     */
    public static boolean hasLoadedChunks(ServerLevel level, RegionBounds box)
    {
        final int minChunkX = box.minX() >> 4;
        final int maxChunkX = box.maxX() >> 4;
        final int minChunkZ = box.minZ() >> 4;
        final int maxChunkZ = box.maxZ() >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
        {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            {
                if (level.hasChunk(chunkX, chunkZ))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * The box worth scanning in this soulhome, derived from which chunk sections actually hold
     * blocks rather than from a fixed guess. A soulhome is one small island in an otherwise empty
     * 384-block-tall void; sweeping all of it would be almost entirely wasted work.
     *
     * @return empty when the dimension holds nothing at all - which includes holding nothing
     *         <i>loaded</i>, so callers that are about to act on "nothing" must ask
     *         {@link #hasLoadedChunks} first
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
     * Work out what to scan and copy it, saying which of the four things happened.
     *
     * <p>Only one of them means "this soulhome is empty". The others are a scan that could not
     * see, and a caller that treats them the same way erases a build it merely failed to read.
     *
     * <b>Server thread only.</b>
     */
    public static Capture capture(ServerLevel level, ScanSettings settings)
    {
        if (!SoulHomeConfig.enforceBounds())
        {
            // #79 off: byte-for-byte what this method did before the box existed
            if (!hasLoadedChunks(level))
            {
                return Capture.of(Capture.Outcome.UNREADABLE);
            }

            Optional<RegionBounds> bounds = populatedBounds(level);

            if (bounds.isEmpty())
            {
                return Capture.of(Capture.Outcome.EMPTY);
            }

            return captureBox(level, bounds.get(), settings);
        }

        // The scan box is declared, not inferred, once #79 is on - the box *is* the verge (plus
        // whatever a legacy grant adds, #80), so there is no "chunks populated but the box is
        // empty" case left to detect: an untouched box is just air, and the scanner returns no
        // regions for it same as it always has. What still has to be checked, and checked against
        // the declared box rather than the old full search square, is whether that box can
        // currently be seen at all - see #hasLoadedChunks(ServerLevel, RegionBounds).
        SoulHomeBuffData.get(level).migrateLegacyBoundsIfNeeded(level);

        final RegionBounds box = declaredBox(level);

        if (!hasLoadedChunks(level, box))
        {
            return Capture.of(Capture.Outcome.UNREADABLE);
        }

        return captureBox(level, box, settings);
    }

    private static Capture captureBox(ServerLevel level, RegionBounds box, ScanSettings settings)
    {
        // asked before the arrays are allocated rather than after: a box past the limit would
        // otherwise be copied in full, at a byte and a reference per cell, only for the scan that
        // received it to refuse it
        if (!RegionScanner.isScannable(box, settings))
        {
            LogHelper.warn("Soulhome " + level.dimension().location() + " spans " + box
                    + " (" + box.volume() + " cells), above the scan limit of "
                    + settings.maxScannedCells() + ". Its buffs are left as they were.");
            return Capture.of(Capture.Outcome.TOO_LARGE);
        }

        return new Capture(Capture.Outcome.CAPTURED, capture(level, box));
    }

    /**
     * The box for this soulhome's current ascension rank (#84), unioned with the legacy box if this
     * soulhome has one. {@link RegionBounds} has no union of its own, so this takes the enclosing
     * box of both rather than their true (possibly non-rectangular) combined shape; the only effect
     * is that a soulhome with an oddly-placed legacy build may have slightly more of the void around
     * it become placeable and scannable than strictly necessary, never less.
     *
     * <p>Shared with placement enforcement ({@code SoulBoundsEnforcement}), so a block that can be
     * scanned is always a block that was allowed to be placed, and vice versa - the two can never
     * disagree about where a soulhome's edge is.
     *
     * <p><b>Server thread only</b>, same as the capture that is this method's main caller: rank is
     * read once here, before the box is handed off to a worker thread, which is what keeps an
     * ascension landing mid-scan from producing a half-ranked result (#84).
     */
    public static RegionBounds declaredBox(ServerLevel level)
    {
        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        final RegionBounds rankBox = SoulHomeConfig.soulBounds(data.ascensionRank()).toRegionBounds();

        return data.legacyBox()
                .map(legacy -> rankBox.encompass(legacy.minX(), legacy.minY(), legacy.minZ())
                        .encompass(legacy.maxX(), legacy.maxY(), legacy.maxZ()))
                .orElse(rankBox);
    }

    /**
     * What {@link #capture(ServerLevel, ScanSettings)} found.
     *
     * @param outcome which of the four things happened
     * @param volume  the copy, present only for {@link Outcome#CAPTURED}
     */
    public record Capture(Outcome outcome, SnapshotBlockVolume volume)
    {
        public enum Outcome
        {
            /** A usable copy of the build. */
            CAPTURED,

            /**
             * Loaded, and genuinely holds nothing. The one outcome that means a soulhome's rooms
             * really are gone, and so the one outcome that may clear its owner's buffs.
             */
            EMPTY,

            /** Nothing of it is loaded, so nothing can be said about what it holds. */
            UNREADABLE,

            /** Populated well past what a scan will read. Also says nothing about what it holds. */
            TOO_LARGE
        }

        private static Capture of(Outcome outcome)
        {
            return new Capture(outcome, null);
        }
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
