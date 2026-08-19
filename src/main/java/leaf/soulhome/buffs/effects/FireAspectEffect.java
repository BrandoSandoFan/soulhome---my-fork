/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Hearth: your sword sets what it hits alight, the way the enchantment of the same name does.
 *
 * <p>Same scope as {@link SwordDamageEffect} and for the same reason: only a direct, sword-in-hand
 * melee hit counts. A blade forged in a hearth is what earned this, not an arrow that happened to
 * leave a bow at the same moment.
 *
 * <p>{@code LivingEntity#setSecondsOnFire} already keeps the larger of the target's current burn
 * time and the new one, so a target already alight for longer than this hit would grant is left
 * alone rather than having its fire cut short.
 */
public class FireAspectEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FIRE_ASPECT;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "seconds the target is set on fire";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getSource().getDirectEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final ItemStack weapon = player.getMainHandItem();

        if (!weapon.is(ItemTags.SWORDS))
        {
            return;
        }

        final int seconds = (int) Math.round(magnitudeFor(player));

        if (seconds <= 0)
        {
            return;
        }

        event.getEntity().setSecondsOnFire(seconds);
    }
}
