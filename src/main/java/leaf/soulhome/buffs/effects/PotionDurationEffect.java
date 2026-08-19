/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Alchemy lab: potions last longer.
 *
 * <p>Extends what the player just drank rather than every effect they gain. An effect from a
 * beacon, a cake shared by a friend or a wither's skull is not something the player's brewing
 * room had any hand in, and quietly stretching all of them would make the buff impossible to
 * reason about.
 *
 * <h2>How the extension is applied</h2>
 *
 * Vanilla has already applied the potion by the time {@code Finish} fires, so this reads back the
 * instance now running on the player and re-adds it with a longer duration. Adding an instance
 * that matches on amplifier and beats it on duration is exactly how vanilla itself handles
 * drinking a second potion, so nothing here needs the duration field to be writable.
 *
 * <p>Instantaneous effects - healing, harming - are skipped: they have no duration to extend, and
 * re-adding one would apply it twice.
 */
public class PotionDurationEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.POTION_DURATION;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra potion duration as a fraction of the duration drunk";
    }

    @SubscribeEvent
    public void onFinishedDrinking(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final ItemStack drunk = event.getItem();
        final List<MobEffectInstance> brewed = PotionUtils.getMobEffects(drunk);

        if (brewed.isEmpty())
        {
            // eating bread, drawing a bow: not everything finished is a potion
            return;
        }

        final double magnitude = magnitudeFor(player);

        for (MobEffectInstance instance : brewed)
        {
            extend(player, instance.getEffect(), magnitude);
        }
    }

    private static void extend(Player player, MobEffect effect, double magnitude)
    {
        if (effect.isInstantenous())
        {
            return;
        }

        final MobEffectInstance active = player.getEffect(effect);

        if (active == null)
        {
            // the potion's effect did not take - immune mob, cured milk, another mod's say-so
            return;
        }

        final int extra = (int) (active.getDuration() * magnitude);

        if (extra <= 0)
        {
            return;
        }

        player.addEffect(new MobEffectInstance(
                effect,
                active.getDuration() + extra,
                active.getAmplifier(),
                active.isAmbient(),
                active.isVisible(),
                active.showIcon()));
    }
}
