/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.VesselLifecycleService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Everything that ends a Soul Vessel (#182) other than its owner returning through it, or the
 * vessel simply being killed - both of those are handled entity-side, by
 * {@code SoulVesselEntity}'s own removal hook.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VesselEvents
{
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            VesselLifecycleService.onOwnerLoggedOut(player);
        }
    }
}
