/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.GazeNotices;
import leaf.soulhome.structures.GazeService;
import leaf.soulhome.structures.SuppressionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The two senses the Meditation epic adds (#187-#189): Soulgaze's spectator session, and suppression.
 * Kept apart from {@code VesselEvents} because a gaze outlives its body's events - it has to be put
 * right on the way back in from a logout or a crash - and apart from {@code CommonEvents} for the
 * same reason {@code MeditationEvents} is: a self-contained subsystem, with its own reason to change.
 */
@EventBusSubscriber(modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GazeEvents
{
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        GazeService.tick(player);
        SuppressionService.tick(player);
    }

    /** Early, so a gazer restored from a crash is back in their own game mode before anything else reads it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            GazeService.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            GazeService.onLogout(player);
            GazeNotices.forget(player.getUUID());
            SuppressionService.forget(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer watched)
        {
            SuppressionService.onStartTracking(observer, watched);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            // the client drops every entity it knew on a dimension change, so it has nothing left to clear
            SuppressionService.forget(player);
        }
    }
}
