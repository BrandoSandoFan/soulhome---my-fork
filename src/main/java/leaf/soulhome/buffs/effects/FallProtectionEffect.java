/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Training yard: falls hurt less - the other half of learning to jump higher.
 *
 * <p>Scales the damage multiplier rather than the fall distance itself. Shortening the distance
 * would also quiet whatever else cares how far the player actually fell - particles, sound,
 * advancement criteria - where this only changes what the fall costs.
 */
public class FallProtectionEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FALL_PROTECTION;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "less fall damage, as a fraction of the fall";
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        // never past 100% off, however generous a datapack's magnitude gets
        final double reduction = Math.min(1d, magnitudeFor(player));

        event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1d - reduction)));
    }
}
