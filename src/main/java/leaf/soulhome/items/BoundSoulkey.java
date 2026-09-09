/*
 * File created ~ 6 - 8 - 2023 ~Leaf
 */

package leaf.soulhome.items;

import leaf.soulhome.registry.DataComponentsRegistry;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.EntityHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

public class BoundSoulkey extends SoulKeyItem
{
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flagIn)
	{
		super.appendHoverText(stack, context, tooltip, flagIn);

		final SoulBinding binding = stack.get(DataComponentsRegistry.SOUL_BINDING);

		if (binding != null)
		{
			tooltip.add(
					TextHelper.createTextWithTooltip(
									TextHelper.createText(binding.name()),
									TextHelper.createText(binding.soul()))
							.withStyle(ChatFormatting.GRAY)
			);
		}
	}

	@Override
	public void onCraftedBy(ItemStack itemStack, Level level, Player player)
	{
		if(level.isClientSide)
			return;

		bindKeyToDimension(itemStack, player);
	}

	private static void bindKeyToDimension(ItemStack itemStack, Player player)
	{
		itemStack.set(
				DataComponentsRegistry.SOUL_BINDING,
				new SoulBinding(player.getUUID(), player.getGameProfile().getName()));
	}

	@Nonnull
	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity livingEntity)
	{
		if (!livingEntity.level().isClientSide && livingEntity instanceof Player player)
		{
			//fix creative mode keys
			if (stack.get(DataComponentsRegistry.SOUL_BINDING) == null)
			{
				bindKeyToDimension(stack, player);
			}

			final SoulBinding binding = stack.get(DataComponentsRegistry.SOUL_BINDING);

			//find all creatures in range
			DimensionHelper.FlipDimension(
					player,
					player.getServer(),
					EntityHelper.getEntitiesInRange(livingEntity,2.5d, true),
					binding == null ? player.getUUID() : binding.soul()
			);
		}

		return stack;
	}
}
