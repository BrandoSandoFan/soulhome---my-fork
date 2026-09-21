/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.items;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.properties.PropTypes;
import leaf.soulhome.sound.SoulSounds;
import leaf.soulhome.structures.VesselLifecycleService;
import leaf.soulhome.structures.core.SoulFeedback;
import leaf.soulhome.utils.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.List;

public class SoulKeyItem extends BaseItem
{
    public SoulKeyItem()
    {
        super(PropTypes.Items.ONE.get().rarity(Rarity.RARE));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flagIn)
    {
        tooltip.add(TextHelper.createTranslatedText(Constants.StringKeys.KEY_SOUL_CHARGE));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity)
    {
        //moved into MeditationSettings (#183) rather than left as a local constant, so meditation's
        //own channel and the key's can be tuned - and read - from the one place
        return SoulHomeConfig.meditationSettings().keyChannelTicks();
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn)
    {
        ItemStack stack = playerIn.getItemInHand(handIn);
        if (playerIn instanceof ServerPlayer)
        {
            ServerPlayer player = (ServerPlayer) playerIn;
            player.startUsingItem(handIn);
        }
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    @Nonnull
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity livingEntity)
    {
        if (!livingEntity.level().isClientSide && livingEntity instanceof Player player)
        {
            //the key's own moment, so the soul on the far side of it does not answer the arrival
            //with an ambient one-shot on top of it (#212). Sent to the player rather than to a
            //place, because the place is about to change
            if (player instanceof ServerPlayer serverPlayer)
            {
                SoulSounds.hold(serverPlayer, SoulFeedback.KEY, SoulFeedback.KEY.defaultHoldTicks());

                // the vessel this key leaves behind, or removes - #182/#184. Before the teleport,
                // since an entry needs the vessel to exist at the position the player is about to leave
                VesselLifecycleService.onKeyUse(serverPlayer, VesselLifecycleService.defaultKeyFragility());
            }

            //find all creatures in range
            DimensionHelper.FlipDimension(
                    player,
                    player.getServer(),
                    EntityHelper.getEntitiesInRange(livingEntity,2.5d, true),
                    player.getUUID()
            );
        }

        return stack;
    }

    //deliberately not @OnlyIn(Dist.CLIENT): vanilla calls onUseTick on both sides, so annotating an
    //override of a common-side method is a crash hazard. ChannelRing itself is side-guarded.
    @Override
    public void onUseTick(Level world, LivingEntity livingEntity, ItemStack stack, int count)
    {
        ChannelRing.spawn(livingEntity, count, SoulHomeConfig.meditationSettings().keyChannelTicks(), ParticleTypes.SOUL_FIRE_FLAME);
    }

//region Remaining item from crafting, using the soul key as an ingredient. We want to keep the key.
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack)
    {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack)
    {
        return new ItemStack(stack.getItem());
    }

    //endregion

}