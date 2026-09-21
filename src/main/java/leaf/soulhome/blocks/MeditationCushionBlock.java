/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

/**
 * The mod's good way in (#183): read at channel start by looking at the block under and beside a
 * player, never subscribed to. No block entity, on purpose - everything the cushion means to the
 * scan is "is one of these six positions a cushion", which is a lookup {@code MeditationService}
 * already has to make and not state worth persisting a second time.
 *
 * <p>A cushion's own thickness, not a full cube - a player kneeling on one should look like they
 * are sitting on something rather than standing inside a block.
 */
public class MeditationCushionBlock extends Block
{
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);

    public MeditationCushionBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }
}
