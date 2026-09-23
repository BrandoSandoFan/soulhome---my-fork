/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.entity.SoulSevered;
import leaf.soulhome.structures.GazeService;
import leaf.soulhome.structures.VesselLifecycleService;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything that ends a Soul Vessel (#182) other than its owner returning through it, or the
 * vessel simply being killed - both of those are handled entity-side, by
 * {@code SoulVesselEntity}'s own removal hook - and the spill when a player dies while away (#186).
 *
 * <h2>The order of a death, and why the spill listens where it does</h2>
 *
 * A player's death runs {@code LivingDeathEvent}, then the experience drop, then
 * {@code LivingDropsEvent}. The body has to outlive both drops - its chunk ticket is what keeps the
 * floor the loot lands on loaded - so it is removed by the last of the three, not the first. Both
 * drop listeners run at {@link EventPriority#LOWEST}, after anything that decides <i>what</i>
 * drops (a gravestone mod, a soulbound enchantment): this moves drops, it never decides them. A
 * drop event some other mod cancelled is left cancelled - there is nothing left to move - but the
 * body is still removed, which is why that listener receives cancelled events.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VesselEvents
{
    /** Where a player who died away from their body died, as far as the recovery compass is concerned. */
    private static final Map<UUID, GlobalPos> DIED_AT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            VesselLifecycleService.onOwnerLoggedOut(player);
        }
    }

    /**
     * A dying gazer is handed their own game mode back first, so that vanilla drops their things at
     * all. Then the body's position is noted as where the death happened, for the recovery compass -
     * where the things are, rather than a coordinate in a soul the items are not in (#186).
     *
     * <p>Noted rather than set: vanilla writes the death position at the very end of
     * {@code ServerPlayer#die}, after this event, so anything set here would be overwritten. It is
     * applied to the respawned player in {@link #onClone}, which runs after vanilla has copied the
     * old one across.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        // a gazer is a spectator, and spectators drop nothing - the gaze has to end before vanilla
        // decides the drops, or a gazer killed through their body would take everything with them
        GazeService.onGazerDied(player);

        VesselLifecycleService.deathPositionOf(player).ifPresent(at -> DIED_AT.put(player.getUUID(), at));

        if (SoulSevered.is(event.getSource()))
        {
            SoulAdvancements.onMoment(player, SoulAdvancements.Moment.VESSEL_DEATH);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        final GlobalPos at = DIED_AT.remove(event.getOriginal().getUUID());

        if (event.isWasDeath() && at != null)
        {
            event.getEntity().setLastDeathLocation(Optional.of(at));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperienceDrop(LivingExperienceDropEvent event)
    {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        if (VesselLifecycleService.relocateExperience(player, event.getDroppedExperience()))
        {
            // cancelled rather than zeroed alongside a second drop, so nothing is ever dropped twice
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        if (!event.isCanceled() && VesselLifecycleService.relocateDrops(player, event.getDrops()))
        {
            event.setCanceled(true);
        }

        VesselLifecycleService.removeAfterDeath(player);
    }
}
