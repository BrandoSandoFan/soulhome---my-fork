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
 * Mine: blocks come apart faster - up to a point (#86).
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
 *
 * <h2>The soft ceiling (#86)</h2>
 *
 * Past instant-break on the block actually being mined, further speed is worth nothing at all - but
 * that point depends on the tool, the enchantments and the block, not on a fixed number, and working
 * it out properly is its own piece of work. {@link #softCeiling} is a fixed approximation instead:
 * generous enough that the shipped mine archetype never reaches it on its own, and a hard stop
 * rather than a trade for something else once a datapack or a config change pushes past it. An
 * earlier draft converted the overflow into a chance to break the block behind or reduced tool
 * damage; asked about it directly, the room's own designer asked for that deferred too, so this
 * ships as a plain stop like {@link ReachEffect} rather than guessing at the real conversion.
 */
public class MiningSpeedEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.MINING_SPEED;

    /** A fixed stand-in for "already instant-break" - see the class doc (#86). */
    private static final double SOFT_CEILING = 0.75d;

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

    @Override
    public double softCeiling()
    {
        return SOFT_CEILING;
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
