/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Mine: blocks come apart faster.
 *
 * <p>Scales the break speed rather than handing out Haste, for the same reason the armoury scales
 * the damage event: a status effect would be visible, curable and would stack oddly with the
 * beacon a player may already be standing in.
 *
 * <h2>Why this one runs on both sides</h2>
 *
 * Block breaking is predicted by the client and confirmed by the server, and the two have to agree
 * or the block snaps back. Every other effect in this package is server-only on purpose, but a
 * break speed applied on the server alone would leave the player watching a normal-speed crack
 * appear. The client's copy of the buff set is synced for exactly this kind of use, so
 * {@link #appliesTo} is widened here rather than the buff being read from anywhere new.
 */
public class MiningSpeedEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.MINING_SPEED;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra block breaking speed as a fraction of the speed";
    }

    /** Both sides, so the client's prediction and the server's confirmation agree. */
    @Override
    public boolean appliesTo(Player player)
    {
        return player != null && magnitudeFor(player) > 0d;
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        final Player player = event.getEntity();

        if (!appliesTo(player))
        {
            return;
        }

        final float speed = event.getNewSpeed();

        if (speed <= 0f)
        {
            // already unbreakable by hand; making nothing faster is still nothing
            return;
        }

        event.setNewSpeed((float) (speed * (1d + magnitudeFor(player))));
    }
}
