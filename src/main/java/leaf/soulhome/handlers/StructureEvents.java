/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.SoulBuffsProvider;
import leaf.soulhome.structures.StructureScanService;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Everything that keeps a player's structure buffs current: attaching them, carrying them across
 * death and dimensions, and telling the scan service when a soulhome has changed.
 *
 * <p>Separate from {@code CommonEvents} because this is a self-contained subsystem, and because
 * the alternative is one class that grows a handler every time the feature does.
 */
@Mod.EventBusSubscriber(modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StructureEvents
{
    // region carrying buffs with the player

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (!(event.getObject() instanceof Player))
        {
            return;
        }

        SoulBuffsProvider provider = new SoulBuffsProvider();
        event.addCapability(ResourceLocationHelper.prefix("buffs"), provider);
        event.addListener(provider::invalidate);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        // Deliberately outside any wasDeath check. Forge fires Clone both for a respawn after
        // death and for a return from the End, and copying only in the second case is the classic
        // way capabilities quietly vanish the first time a player dies.
        event.getOriginal().reviveCaps();

        try
        {
            event.getOriginal().getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(previous ->
                    event.getEntity().getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(fresh ->
                            fresh.set(previous.get())));
        }
        finally
        {
            event.getOriginal().invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        // recomputed from the soulhome's saved results rather than trusted from the player's own
        // save: a datapack change between sessions should take effect without anyone rebuilding
        if (event.getEntity() instanceof ServerPlayer player)
        {
            StructureScanService.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            StructureScanService.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        final MinecraftServer server = player.getServer();

        if (server != null)
        {
            // Leaving a soulhome is the natural moment to say "here is what you built", so the
            // dimension being left is scanned without waiting out the debounce. Hooked here rather
            // than inside DimensionHelper.FlipDimension so that every way out counts - the
            // SoulKey, the /soul command, and falling out of the world.
            final ServerLevel from = server.getLevel(event.getFrom());

            if (from != null)
            {
                StructureScanService.requestNow(from);
            }
        }

        StructureScanService.refresh(player);
    }

    // endregion

    // region knowing when a soulhome changed

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel() instanceof Level level)
        {
            StructureScanService.markDirty(level);
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event)
    {
        if (event.getLevel() instanceof Level level)
        {
            StructureScanService.markDirty(level);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        // a soulhome edited in a previous session, or one that has never been scanned at all
        if (event.getLevel() instanceof Level level)
        {
            StructureScanService.requestNow(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof Level level)
        {
            StructureScanService.forget(level);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server != null)
        {
            StructureScanService.onServerTick(server);
        }
    }

    // endregion
}
