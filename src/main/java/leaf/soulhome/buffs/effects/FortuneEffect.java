/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Treasury: a hoard of your own draws a little more out of the ground.
 *
 * <p>A flat chance per block broken of one extra copy of whatever it dropped, rolled once against
 * the room's own magnitude rather than pushed through vanilla's fortune enchantment maths - this
 * is a soulhome buff stacking with any tool the player is holding, not a bigger fortune level on
 * it, so it has no enchantment curve of its own to reproduce.
 *
 * <p>Doubles an existing stack's count where that fits under the item's max stack size, and adds a
 * fresh one-count copy otherwise, so a bonus drop is never silently lost to a stack that was
 * already full.
 */
public class FortuneEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FORTUNE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "chance of an extra drop when breaking a block, as a fraction chance";
    }

    @SubscribeEvent
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event)
    {
        if (!(event.getHarvester() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final List<ItemStack> drops = event.getDrops();

        if (drops.isEmpty())
        {
            return;
        }

        final double chance = Math.min(1d, magnitudeFor(player));

        if (player.getRandom().nextDouble() >= chance)
        {
            return;
        }

        final ItemStack sample = drops.get(player.getRandom().nextInt(drops.size()));

        if (sample.getCount() < sample.getMaxStackSize())
        {
            sample.grow(1);
        }
        else
        {
            ItemStack bonus = sample.copy();
            bonus.setCount(1);
            drops.add(bonus);
        }
    }
}
