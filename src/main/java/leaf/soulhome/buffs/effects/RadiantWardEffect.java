/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Beacon Hall: a beacon wards whoever stands where its light reaches, and its owner carries a
 * little of that ward with them.
 *
 * <p>Scales the damage event directly rather than granting the vanilla Resistance status effect,
 * the same reasoning {@link FireResistanceEffect} gives for cold storage: a fraction that ramps
 * with the room's own tier, invisible until it matters, and immune to anything that clears potion
 * effects. Unlike fire resistance this is not scoped to one damage tag - a beacon's light does not
 * pick and choose what it wards against.
 */
public class RadiantWardEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.RADIANT_WARD;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "less damage taken from anything, as a fraction of the hit";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final double reduction = Math.min(1d, magnitudeFor(player));
        event.setAmount((float) (event.getAmount() * (1d - reduction)));
    }
}
