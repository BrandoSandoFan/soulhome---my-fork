/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.BlockSignature;
import leaf.soulhome.structures.core.BlockVolume;
import leaf.soulhome.structures.core.Passability;
import leaf.soulhome.structures.core.RegionBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A {@link BlockVolume} read straight off the world, position by position, rather than copied out
 * of it first - what {@link SnapshotBlockVolume} is for the classifier, this is for the ascension
 * ritual's pillar check (#83): a snapshot is worth taking once per scan over a whole soulhome, but
 * a ritual re-checks a handful of columns near one anchor every tick, and copying the box the
 * pillar sits in - potentially the whole verge - just to throw it away a moment later would cost
 * far more than the handful of {@code getBlockState} calls this does instead.
 *
 * <p>{@link PillarInspector.Result}'s own lateral bound keeps the actual footprint this is ever
 * asked about small regardless of the soulhome's rank, so there is no snapshot-sized cost being
 * avoided here in name only.
 */
final class LiveBlockVolume implements BlockVolume
{
    private final ServerLevel level;
    private final RegionBounds bounds;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LiveBlockVolume(ServerLevel level, RegionBounds bounds)
    {
        this.level = level;
        this.bounds = bounds;
    }

    @Override
    public RegionBounds bounds()
    {
        return this.bounds;
    }

    @Override
    public Passability passabilityAt(int x, int y, int z)
    {
        if (!this.bounds.contains(x, y, z))
        {
            return Passability.EMPTY;
        }

        this.cursor.set(x, y, z);
        final BlockState state = this.level.getBlockState(this.cursor);
        return SnapshotBlockVolume.passabilityOf(this.level, this.cursor, state);
    }

    @Override
    public BlockSignature signatureAt(int x, int y, int z)
    {
        if (!this.bounds.contains(x, y, z))
        {
            return null;
        }

        this.cursor.set(x, y, z);
        final BlockState state = this.level.getBlockState(this.cursor);
        return state.isAir() ? null : StateSignature.of(state);
    }
}
