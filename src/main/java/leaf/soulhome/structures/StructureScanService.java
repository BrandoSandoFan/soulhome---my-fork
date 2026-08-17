/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.BuffCalculator;
import leaf.soulhome.structures.core.BuffSettings;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.RegionScanner;
import leaf.soulhome.structures.core.ScanDebouncer;
import leaf.soulhome.structures.core.ScanSettings;
import leaf.soulhome.structures.core.SoulBuffSet;
import leaf.soulhome.structures.core.SoulRegion;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides when to rescan a soulhome, and runs the scan without stalling the server.
 *
 * <p>Three things trigger a rescan:
 *
 * <ul>
 *   <li>a block placed or broken inside a soul dimension, debounced so that building a wall costs
 *       one scan rather than one per block</li>
 *   <li>a player leaving their soulhome - the natural moment to say "here is what you built", and
 *       so it skips the debounce</li>
 *   <li>a soul dimension loading, for a soulhome edited in a previous session</li>
 * </ul>
 *
 * <p>The work is split across threads by what is safe where. The level is read once on the server
 * thread into a {@link SnapshotBlockVolume}; detection and classification then run on a worker;
 * the results are applied back on the server thread. Nothing touches a live level off-thread.
 */
public final class StructureScanService
{
    /** How often the pending set is checked. Once a second is far finer than the debounce. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    private static final ScanDebouncer<ResourceKey<Level>> DEBOUNCER = new ScanDebouncer<>();

    private static int tickCounter;

    private StructureScanService()
    {
    }

    /** A block changed in this level. Ignored for anything that is not a soulhome. */
    public static void markDirty(Level level)
    {
        if (level instanceof ServerLevel serverLevel && DimensionHelper.soulOwner(serverLevel).isPresent())
        {
            DEBOUNCER.markDirty(serverLevel.dimension(), now());
        }
    }

    /** Scan this soulhome at the next opportunity, without waiting out the debounce. */
    public static void requestNow(Level level)
    {
        if (level instanceof ServerLevel serverLevel && DimensionHelper.soulOwner(serverLevel).isPresent())
        {
            DEBOUNCER.requestNow(serverLevel.dimension(), now());
        }
    }

    /** A soul dimension unloaded; stop tracking it. */
    public static void forget(Level level)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            DEBOUNCER.forget(serverLevel.dimension());
        }
    }

    public static void onServerTick(MinecraftServer server)
    {
        if (++tickCounter < CHECK_INTERVAL_TICKS)
        {
            return;
        }

        tickCounter = 0;

        if (DEBOUNCER.isIdle())
        {
            return;
        }

        for (ResourceKey<Level> key : DEBOUNCER.claimDue(now()))
        {
            final ServerLevel level = server.getLevel(key);

            if (level == null)
            {
                // unloaded between being marked dirty and being claimed
                DEBOUNCER.release(key);
                DEBOUNCER.forget(key);
                continue;
            }

            beginScan(server, level);
        }
    }

    /**
     * Snapshot on the server thread, scan on a worker, apply back on the server thread.
     */
    private static void beginScan(MinecraftServer server, ServerLevel level)
    {
        final ResourceKey<Level> key = level.dimension();

        final Optional<SnapshotBlockVolume> snapshot;

        try
        {
            snapshot = SnapshotBlockVolume.capture(level);
        }
        catch (RuntimeException e)
        {
            LogHelper.error("Could not snapshot soulhome " + key.location() + " for scanning: " + e);
            DEBOUNCER.release(key);
            return;
        }

        if (snapshot.isEmpty())
        {
            // an untouched soulhome holds nothing worth scanning
            applyResults(server, key, List.of(), 0L);
            DEBOUNCER.release(key);
            return;
        }

        final SnapshotBlockVolume volume = snapshot.get();

        Util.backgroundExecutor().execute(() ->
        {
            List<SoulRegion> regions;
            long contentHash;

            try
            {
                regions = RegionScanner.scan(volume, ArchetypeManager.signalFilter(), ScanSettings.DEFAULTS);
                contentHash = SoulHomeBuffData.hashOf(regions);
            }
            catch (RuntimeException e)
            {
                LogHelper.error("Structure scan of soulhome " + key.location() + " failed: " + e);
                server.execute(() -> DEBOUNCER.release(key));
                return;
            }

            final List<SoulRegion> found = regions;
            final long hash = contentHash;

            server.execute(() ->
            {
                try
                {
                    finishScan(server, key, found, hash);
                }
                finally
                {
                    // released whatever happened, or this soulhome would never be scanned again
                    DEBOUNCER.release(key);
                }
            });
        });
    }

    private static void finishScan(MinecraftServer server, ResourceKey<Level> key, List<SoulRegion> regions, long contentHash)
    {
        final ServerLevel level = server.getLevel(key);

        if (level == null)
        {
            return;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(level);

        // nothing moved since the last scan: no need to reclassify or to touch the player
        if (data.hasBeenScanned() && data.contentHash() == contentHash)
        {
            return;
        }

        List<ClassificationResult> results = ArchetypeManager.classifier().classify(regions);
        applyResults(server, key, AwardedRoom.from(results), contentHash);
    }

    private static void applyResults(MinecraftServer server, ResourceKey<Level> key, List<AwardedRoom> awarded, long contentHash)
    {
        final ServerLevel level = server.getLevel(key);

        if (level == null)
        {
            return;
        }

        if (!SoulHomeBuffData.get(level).update(awarded, contentHash))
        {
            return;
        }

        DimensionHelper.soulOwner(level).ifPresent(owner -> pushBuffs(server, owner, awarded));
    }

    private static void pushBuffs(MinecraftServer server, UUID owner, List<AwardedRoom> awarded)
    {
        final ServerPlayer player = server.getPlayerList().getPlayer(owner);

        if (player == null)
        {
            // offline: the results are saved, and login recomputes from them
            return;
        }

        SoulBuffs.set(player, buffsFrom(awarded));
    }

    /**
     * Restore a player's buffs from their soulhome's last scan, without scanning anything.
     * Used on login, on respawn and on dimension change.
     */
    public static void refresh(ServerPlayer player)
    {
        final MinecraftServer server = player.getServer();

        if (server == null)
        {
            return;
        }

        final ServerLevel soulhome = server.getLevel(soulDimensionKeyOf(player));

        if (soulhome == null)
        {
            // the player has never opened their soulhome, so there is nothing to restore.
            // Sync anyway so a client that reconnected knows it has nothing.
            SoulBuffs.sync(player);
            return;
        }

        SoulBuffs.set(player, buffsFrom(SoulHomeBuffData.get(soulhome).awardedRooms()));
        SoulBuffs.sync(player);
    }

    private static SoulBuffSet buffsFrom(List<AwardedRoom> awarded)
    {
        return BuffCalculator.computeFromAwarded(
                awarded, ArchetypeManager.archetypes(), BuffSettings.DEFAULTS);
    }

    private static ResourceKey<Level> soulDimensionKeyOf(ServerPlayer player)
    {
        //mirrors how DimensionRegistry names a soul dimension: the owner's UUID
        return ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocationHelper.prefix(player.getUUID().toString()));
    }

    private static long now()
    {
        return System.currentTimeMillis();
    }
}
