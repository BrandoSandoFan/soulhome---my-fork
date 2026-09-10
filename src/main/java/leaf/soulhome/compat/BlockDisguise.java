/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * "What is this block really?" for a block whose visible material lives in a block entity rather
 * than in its own {@code BlockState} - Create's copycats are the motivating case, but the
 * interface asks nothing Create-specific so Framed Blocks, Chipped and the like can each get a
 * package-private implementation of their own.
 *
 * <p>Package-private on purpose: callers go through {@link BlockDisguises}, which is the one
 * place that needs to know how many of these exist.
 */
interface BlockDisguise
{
    /**
     * @return the material this block entity is dressed as, or empty if it has none - a bare
     *         copycat, or a block entity this disguise does not recognise at all
     */
    Optional<BlockState> materialOf(BlockEntity blockEntity);
}
