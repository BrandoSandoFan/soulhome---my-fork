/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Cold storage: fire and lava hurt less.
 *
 * <p>Deliberately not the vanilla Fire Resistance status effect - that would zero the damage
 * outright, show up on the effects HUD, and be cured by milk. Scaling the damage event instead
 * keeps this the same shape as every other soulhome buff: a fraction that ramps with the room's
 * own tier, invisible until it matters, and immune to anything that clears potion effects.
 *
 * <p>Scoped to {@link DamageTypeTags#IS_FIRE} rather than any specific damage type, so a hit from
 * fire, lava, a blaze fireball or a burning arrow are all reduced alike - the room is cold
 * storage against heat in general, not against one particular way of catching alight.
 */
public class FireResistanceEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FIRE_RESISTANCE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "less damage taken from fire and lava, as a fraction of the hit";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        if (!event.getSource().is(DamageTypeTags.IS_FIRE))
        {
            return;
        }

        final double reduction = Math.min(1d, magnitudeFor(player));
        event.setAmount((float) (event.getAmount() * (1d - reduction)));
    }
}
