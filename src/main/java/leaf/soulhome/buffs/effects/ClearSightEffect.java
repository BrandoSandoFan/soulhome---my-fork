/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Observatory: eyes trained on the sky see just as well without it.
 *
 * <p>Reapplied every tick rather than granted once, the same as the ambient Night Vision
 * {@link SwimSpeedEffect} hands out past its own soft ceiling - it never outlives the room that
 * earned it, never shows on the HUD, and is immune to anything that clears potion effects. Unlike
 * that one, this is the room's own buff rather than an overflow of a different one, so it does not
 * wait for a ceiling to be crossed - any magnitude at all is a steady enough hand to read the sky
 * by.
 */
public class ClearSightEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.CLEAR_SIGHT;

    /** Reapplied every tick, so a lapsed room's Night Vision fades within a second rather than lingering. */
    private static final int DURATION_TICKS = 20;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "steady enough to see in the dark - present or not, not a matter of degree";
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;

        if (!appliesTo(player))
        {
            return;
        }

        // ambient, no particles, no icon - the same "off the HUD" promise every soulhome buff makes
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_TICKS, 0, true, false, false));
    }
}
