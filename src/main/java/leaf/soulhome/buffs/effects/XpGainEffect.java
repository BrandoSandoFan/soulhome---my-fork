/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Library: experience comes in faster.
 *
 * <h2>Why XpChange and not PickupXp</h2>
 *
 * Both fire when a player walks over an orb, and hooking both would multiply that orb twice.
 * {@code XpChange} is the one to keep: it is the single funnel every gain passes through -
 * furnaces, trades, breeding and bottles as well as orbs - so hooking it gives one consistent
 * rule instead of "orbs are worth more but smelting is not".
 *
 * <p>This is a gain-rate buff on the player, so it deliberately does not touch the experience mobs
 * <i>drop</i>: a buffed player's kills should not enrich whoever else is standing nearby.
 */
public class XpGainEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.XP_GAIN;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra experience as a fraction of the amount gained";
    }

    @SubscribeEvent
    public void onExperienceChange(PlayerXpEvent.XpChange event)
    {
        final Player player = event.getEntity();

        if (!appliesTo(player))
        {
            return;
        }

        final int amount = event.getAmount();

        if (amount <= 0)
        {
            // losing experience on death is not something to be generous about
            return;
        }

        final int boosted = Mth.floor(amount * (1d + magnitudeFor(player)));

        // setAmount does not re-fire the event, so there is no risk of compounding here
        if (boosted != amount)
        {
            event.setAmount(boosted);
        }
    }
}
