/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.blocks;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.AscensionRitualService;
import leaf.soulhome.structures.SoulHomeBuffData;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The interaction point for the ascension ritual (#83): right-click to hear what a soulhome still
 * needs, one per soulhome, and unopinionated about the pillar it stands beside - {@code use} only
 * ever reports and converts residue, it never itself judges the pillar or spends anything. That
 * happens in {@link AscensionRitualService}, driven by the player physically standing on the
 * pillar's cap, not by clicking this block - see #83's own "you push upward", not "you press a
 * button".
 *
 * <p>Rank lives in {@code SoulHomeBuffData}, not here - breaking this block with a stray pickaxe
 * swing must not cost a single rank of progress. {@link #setPlacedBy} only enforces that a
 * soulhome has at most one, and {@link #onRemove} only forgets where it was.
 */
public class SoulAnchorBlock extends Block
{
    public SoulAnchorBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.SUCCESS;
        }

        if (!SoulHomeConfig.enforceBounds())
        {
            serverPlayer.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_DISABLED).withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        if (!DimensionHelper.isInSoulDimension(player))
        {
            serverPlayer.sendSystemMessage(Component.translatable(Constants.StringKeys.ANCHOR_NOT_HERE).withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        AscensionRitualService.reportStatus(serverLevel, serverPlayer);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide || !(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(serverLevel);
        final Optional<BlockPos> existing = data.anchorPos();

        if (existing.isPresent() && !existing.get().equals(pos))
        {
            // one per soulhome - refuse the second and hand the item back rather than leave a
            // block sitting there that setAnchorPos would never have pointed at
            serverLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

            if (placer instanceof ServerPlayer serverPlayer)
            {
                serverPlayer.sendSystemMessage(
                        Component.translatable(Constants.StringKeys.ANCHOR_ALREADY_EXISTS).withStyle(ChatFormatting.RED));

                if (!serverPlayer.getAbilities().instabuild)
                {
                    final ItemStack refund = new ItemStack(this);

                    if (!serverPlayer.getInventory().add(refund))
                    {
                        serverPlayer.drop(refund, false);
                    }
                }
            }

            return;
        }

        data.setAnchorPos(pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston)
    {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide && level instanceof ServerLevel serverLevel)
        {
            final SoulHomeBuffData data = SoulHomeBuffData.get(serverLevel);

            if (data.anchorPos().map(pos::equals).orElse(false))
            {
                data.clearAnchorPos();
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
