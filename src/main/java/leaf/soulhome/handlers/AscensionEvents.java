/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.AscensionRitualService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the ascension ritual (#83): a per-player tick, and every way a player can leave one
 * mid-ritual without ever choosing to abort it.
 *
 * <p>Separate from {@code StructureEvents} for the same reason that class is separate from
 * {@code CommonEvents} - a self-contained subsystem, with its own single reason to change.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AscensionEvents
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
            AscensionRitualService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            AscensionRitualService.abortIfRitualBelongsTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        // covers death - the ritual's cap position is not somewhere a respawned player still
        // stands, so there is nothing to keep running
        if (event.getEntity() instanceof ServerPlayer player)
        {
            AscensionRitualService.abortIfRitualBelongsTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            AscensionRitualService.abortIfRitualBelongsTo(player);
        }
    }
}
