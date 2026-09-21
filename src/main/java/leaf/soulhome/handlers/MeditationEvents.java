/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.MeditationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drives a meditation channel (#183): a per-player tick, damage aborting it, and every way a
 * player can leave one mid-channel without ever choosing to. Separate from
 * {@code AscensionEvents} for the same reason that class is separate from {@code CommonEvents} - a
 * self-contained subsystem, with its own single reason to change.
 */
@EventBusSubscriber(modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public class MeditationEvents
{
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            MeditationService.tick(player);
        }
    }

    /**
     * A player under attack must not be able to escape a fight by leaving the world - the exact
     * behaviour #181 exists to remove. Every hit counts, not only ones that land in a soul: the
     * entry channel is held in the overworld, beside the cushion.
     *
     * <p>{@link LivingIncomingDamageEvent} rather than Forge's {@code LivingHurtEvent} - NeoForge's
     * equivalent, fired after invulnerability checks but before any damage is applied (see
     * {@code KnockbackResistanceEffect} for the same substitution made against #196).
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event)
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
