/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.MeditationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives a meditation channel (#183): a per-player tick, damage aborting it, and every way a
 * player can leave one mid-channel without ever choosing to. Separate from
 * {@code AscensionEvents} for the same reason that class is separate from {@code CommonEvents} - a
 * self-contained subsystem, with its own single reason to change.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeditationEvents
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
            MeditationService.tick(player);
        }
    }

    /**
     * A player under attack must not be able to escape a fight by leaving the world - the exact
     * behaviour #181 exists to remove. Every hit counts, not only ones that land in a soul: the
     * entry channel is held in the overworld, beside the cushion.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        final LivingEntity entity = event.getEntity();

        if (entity instanceof ServerPlayer player)
        {
            MeditationService.onDamaged(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            MeditationService.abort(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            MeditationService.abort(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            MeditationService.abort(player);
        }
    }
}
