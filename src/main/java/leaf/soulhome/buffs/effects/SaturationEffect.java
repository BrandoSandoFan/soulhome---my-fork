/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Farm: more saturation out of everything you eat.
 *
 * <p>The author's own headline example for this feature - "building a farm in a specific
 * multiblock structure might mean that you get more saturation out of each thing that you eat".
 *
 * <p>Implemented by topping up {@link FoodData} once vanilla has finished eating, rather than by
 * intercepting the food's properties. That works for any food item including modded ones, and
 * needs no mixin.
 */
public class SaturationEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SATURATION;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra saturation as a fraction of the food's own value";
    }

    @SubscribeEvent
    public void onFinishedEating(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final ItemStack eaten = event.getItem();
        final FoodProperties food = eaten.getFoodProperties(player);

        if (food == null)
        {
            // drinking a potion, drawing a bow: not everything finished is food
            return;
        }

        final FoodData data = player.getFoodData();

        // 1.20.5 turned the old nutrition * saturationModifier * 2 into one absolute number on
        // FoodProperties, so the amount vanilla is about to add is now simply saturation(); this
        // adds a fraction of that again
        final float bonus = (float) (food.saturation() * magnitudeFor(player));

        if (bonus <= 0f)
        {
            return;
        }

        // never past the food level, which is the vanilla ceiling on saturation. Without this the
        // buff would leave a player permanently full rather than merely well fed.
        final float capped = Math.min(data.getSaturationLevel() + bonus, data.getFoodLevel());

        data.setSaturation(capped);
    }
}
