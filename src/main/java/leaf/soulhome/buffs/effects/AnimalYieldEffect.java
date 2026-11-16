/*
 * File created ~ 16 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Farm, as a pasture: more drops from the animals you kill.
 *
 * <p>Each dropped stack grows by the magnitude as a fraction of its own size, and the fractional
 * part is rolled rather than rounded, so a +10% buff on a single piece of mutton is a one-in-ten
 * extra piece and not nothing at all. Animals only, and only a kill by the player themselves: a
 * zombie's flesh or a creeper's powder is not husbandry, and neither is an animal that burned to
 * death in a field the player was not standing in.
 */
public class AnimalYieldEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.ANIMAL_YIELD;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra drops from animals as a fraction of each stack";
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event)
    {
        if (!(event.getEntity() instanceof Animal animal)
                || !(event.getSource().getEntity() instanceof Player player)
                || !appliesTo(player))
        {
            return;
        }

        final double magnitude = magnitudeFor(player);

        for (ItemEntity drop : event.getDrops())
        {
            final ItemStack stack = drop.getItem();
            final double extra = stack.getCount() * magnitude;
            int whole = (int) Math.floor(extra);

            if (animal.getRandom().nextDouble() < extra - whole)
            {
                whole++;
            }

            if (whole > 0)
            {
                stack.grow(Math.min(whole, stack.getMaxStackSize() - stack.getCount()));
            }
        }
    }
}
