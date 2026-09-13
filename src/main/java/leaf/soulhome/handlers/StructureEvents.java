/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.handlers;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.StructureScanService;
import leaf.soulhome.structures.TerrainGrowthService;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Everything that keeps a player's structure buffs current: attaching them, carrying them across
 * death and dimensions, and telling the scan service when a soulhome has changed.
 *
 * <p>Separate from {@code CommonEvents} because this is a self-contained subsystem, and because
 * the alternative is one class that grows a handler every time the feature does.
 */
@EventBusSubscriber(modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public class StructureEvents
{
    // region carrying buffs with the player

    // Nothing attaches or copies the buffs by hand any more. SoulBuffsAttachment declares them
    // copyOnDeath, and NeoForge already copies serializable entity attachments on a return from
    // the End, so the respawn and End-return cases that used to need a PlayerEvent.Clone handler -
    // one that had to revive the old player's capabilities just to read them - are both covered by
    // the attachment type itself. The rank travels with the magnitudes because it is serialized
    // alongside them (#85): a copy that forgot it would hand a rank V player their own numbers
    // held to a rank 0 cap.

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        // recomputed from the soulhome's saved results rather than trusted from the player's own
        // save: a datapack change between sessions should take effect without anyone rebuilding
        if (event.getEntity() instanceof ServerPlayer player)
        {
            StructureScanService.refresh(player);

            // logging back in inside your own soul is the one way to arrive in it without a
            // dimension change, and so the one way ground still owed would otherwise sit unbuilt
            // until the next time you walked out and back (#158)
            TerrainGrowthService.considerGrowth(player.level());
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
            // dimension being left is read here and now. Not merely requested: nothing keeps a
            // soul dimension loaded once its owner has gone, so by the next tick there can be
            // nothing left to read. Hooked here rather than inside DimensionHelper.FlipDimension
            // so that every way out counts - the SoulKey, the /soul command, and falling out of
            // the world.
            final ServerLevel from = server.getLevel(event.getFrom());

            if (from != null)
            {
                StructureScanService.scanNow(from);
            }

            // and arriving is the moment to pick up anything that changed while nobody could see
            // it - a datapack that redefined an archetype between sessions, say. Marked dirty
            // rather than requested outright, so the scan waits out the quiet period and finds
            // the chunks around the player loaded rather than still arriving.
            final ServerLevel to = server.getLevel(event.getTo());

            if (to != null)
            {
                StructureScanService.markDirty(to);

                // and the moment a soulhome's chunks are certain to be readable is the moment to
                // finish any ground it is still owed (#158) - an ascension whose growth the server
                // stopped halfway through, or a soulhome that was already ranked before growth
                // existed. A no-op for the overwhelming majority of arrivals.
                TerrainGrowthService.considerGrowth(to);
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
        // A soulhome that has never been scanned at all. Nothing of it is loaded yet at this
        // point, so this scan usually finds nothing readable and leaves the last saved results
        // alone; the rescan that matters happens when its owner next walks in.
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
            TerrainGrowthService.forget(level);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server != null)
        {
            StructureScanService.onServerTick(server);

            // every tick, unlike the scan service's own check interval: a growth job's whole
            // purpose is to spread its work thinly, and running it one tick in twenty would make
            // each of those ticks twenty times heavier for the same total (#161)
            TerrainGrowthService.onServerTick(server);
        }
    }

    // endregion
}
