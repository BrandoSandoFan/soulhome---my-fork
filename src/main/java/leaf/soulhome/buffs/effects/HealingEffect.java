/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Bedchamber: you mend faster.
 *
 * <p>Scales the amount healed rather than granting Regeneration. An effect would show up in the
 * player's status bar, be cured by milk and stack strangely with a potion of the same name; a
 * multiplier on healing is invisible until it matters and cannot be dispelled by drinking
 * something.
 *
 * <p>Deliberately every source of healing, not only the slow tick of a full hunger bar. Golden
 * apples, regeneration and a friend's splash potion all mend faster in a body that has somewhere
 * to rest, and carving out exceptions would make the buff harder to predict than it is worth.
 * Damage is untouched: this is a room for recovering in, not armour.
 */
public class HealingEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.HEALING;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra healing as a fraction of the health restored";
    }

    @SubscribeEvent
    public void onHeal(LivingHealEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final float amount = event.getAmount();

        if (amount <= 0f)
        {
            return;
        }

        event.setAmount((float) (amount * (1d + magnitudeFor(player))));
    }
}
