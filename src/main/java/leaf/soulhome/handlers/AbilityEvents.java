/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.SoulAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the active abilities (#87): the recharge tick, and the three moments a player's banked
 * charges have to be dealt with rather than left to drift.
 *
 * <p>Separate from {@code AscensionEvents} and {@code StructureEvents} for the reason those two are
 * separate from each other - a self-contained subsystem with its own single reason to change.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AbilityEvents
{
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (event.player instanceof ServerPlayer player)
        {
            SoulAbilities.tick(player);
        }
    }

    /**
     * A fresh login gets the whole state pushed once, unprompted. The client has just cleared its
     * copy on the way out of the last world, and nothing else would tell it what this player owns
     * until the first charge happened to land.
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            SoulAbilities.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            SoulAbilities.forget(player);
        }
    }

    /**
     * Charges do not survive death, per #87 - so this is where they are emptied, not on the clone
     * that carries the magnitudes across. The respawned player is a different entity object from
     * the one that died, which is why this reads the event's own player rather than remembering the
     * corpse.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered())
        {
            SoulAbilities.onDeath(player);
        }
    }
}
