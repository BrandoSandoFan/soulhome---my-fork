/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSoulBoundsMessage;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.BuffBreakdown;
import leaf.soulhome.structures.core.BuffCalculator;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.RegionScanner;
import leaf.soulhome.structures.core.ScanDebouncer;
import leaf.soulhome.structures.core.SoulBounds;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Decides when to rescan a soulhome, and runs the scan without stalling the server.
 *
 * <p>Four things trigger a rescan:
 *
 * <ul>
 *   <li>a block placed or broken inside a soul dimension, debounced so that building a wall costs
 *       one scan rather than one per block</li>
 *   <li>a player leaving their soulhome - the natural moment to say "here is what you built", so
 *       it skips the debounce entirely and reads the level on the spot</li>
 *   <li>a player arriving in one, for a soulhome whose definitions changed between sessions</li>
 *   <li>a player asking, through {@code /soulhome analyse} or the Soul Lens</li>
 * </ul>
 *
 * <p>The work is split across threads by what is safe where. The level is read once on the server
 * thread into a {@link SnapshotBlockVolume}; detection and classification then run on a worker;
 * the results are applied back on the server thread. Nothing touches a live level off-thread.
 *
 * <h2>A scan that cannot see is not a scan that found nothing</h2>
 *
 * A soul dimension keeps none of its own chunks loaded, so most of the time there is nothing
 * there to read. Only {@link SnapshotBlockVolume.Capture.Outcome#EMPTY} - loaded, and genuinely
 * bare - may clear what a soulhome was last found to hold. Every other way a snapshot can fail
 * leaves the saved results exactly as they were.
 *
 * <p>All server-thread state - the debouncer, the analysis cache, the waiting callbacks - is
 * touched only from {@link #onServerTick} and the handlers that feed it.
 */
public final class StructureScanService
{
    private static ScanDebouncer<ResourceKey<Level>> debouncer = newDebouncer();

    /** The periods {@link #debouncer} was built with, so a config change can be noticed. */
    private static long debouncerQuietPeriod = SoulHomeConfig.quietPeriodMillis();
    private static long debouncerMaxDelay = SoulHomeConfig.maxScanDelayMillis();

    /**
     * The last full classification of each loaded soulhome. Not persisted; see {@link SoulAnalysis}.
     */
    private static final Map<ResourceKey<Level>, SoulAnalysis> ANALYSES = new HashMap<>();

    /** Callers waiting for the next completed scan of a soulhome. */
    private static final Map<ResourceKey<Level>, List<Consumer<SoulAnalysis>>> WAITING = new HashMap<>();

    private static int tickCounter;

    private StructureScanService()
    {
    }

    /** A block changed in this level. Ignored for anything that is not a soulhome. */
    public static void markDirty(Level level)
    {
        soulLevel(level).ifPresent(soulhome -> debouncer.markDirty(soulhome.dimension(), now()));
    }

    /** Scan this soulhome at the next opportunity, without waiting out the debounce. */
    public static void requestNow(Level level)
    {
        soulLevel(level).ifPresent(soulhome -> debouncer.requestNow(soulhome.dimension(), now()));
    }

    /**
     * Read this soulhome <i>right now</i>, on this thread, rather than on a later tick.
     *
     * <p>For the one moment where the difference matters: a player leaving their soulhome is the
     * last instant its chunks are certain to still be loaded. Nothing keeps a soul dimension
     * loaded once its owner has gone, so a scan deferred even to the next tick can arrive to find
     * an empty dimension - and "here is what you built" is exactly the moment this feature cannot
     * afford to get wrong.
     *
     * <p>Only the snapshot is taken here; detection and classification still run on a worker.
     */
    public static void scanNow(Level level)
    {
        soulLevel(level).ifPresent(soulhome ->
        {
            final MinecraftServer server = soulhome.getServer();
            final ResourceKey<Level> key = soulhome.dimension();

            if (server == null)
            {
                return;
            }

            if (!debouncer.claim(key))
            {
                // a scan is already running; edits it may have missed are picked up by the next one
                debouncer.requestNow(key, now());
                return;
            }

            beginScan(server, soulhome);
        });
    }

    /** A soul dimension unloaded; stop tracking it. */
    public static void forget(Level level)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            final ResourceKey<Level> key = serverLevel.dimension();

            debouncer.forget(key);
            ANALYSES.remove(key);

            // anything still waiting would wait forever otherwise
            deliver(key, SoulAnalysis.empty(key, now()));
        }
    }

    /**
     * Hand the caller a current analysis of this soulhome.
     *
     * <p>Answers straight from the cache when nothing has changed since the last scan, and
     * otherwise queues the caller behind a fresh one. The callback always runs on the server
     * thread, and always runs exactly once - a player who types the command and gets nothing back
     * would reasonably conclude the feature is broken, which is the whole failure this work
     * exists to prevent.
     */
    public static void analyse(ServerLevel level, Consumer<SoulAnalysis> callback)
    {
        final ResourceKey<Level> key = level.dimension();

        if (DimensionHelper.soulOwner(level).isEmpty())
        {
            callback.accept(SoulAnalysis.empty(key, now()));
            return;
        }

        final SoulAnalysis cached = ANALYSES.get(key);

        if (cached != null && !debouncer.isPending(key) && !debouncer.isInFlight(key))
        {
            callback.accept(cached);
            return;
        }

        WAITING.computeIfAbsent(key, ignored -> new ArrayList<>()).add(callback);
        debouncer.requestNow(key, now());
    }

    /** Whether an answer to {@link #analyse} is still on its way, for a "working on it" message. */
    public static boolean isScanning(ServerLevel level)
    {
        final ResourceKey<Level> key = level.dimension();
        return debouncer.isPending(key) || debouncer.isInFlight(key);
    }

    public static void onServerTick(MinecraftServer server)
    {
        if (++tickCounter < SoulHomeConfig.checkIntervalTicks())
        {
            return;
        }

        tickCounter = 0;

        rebuildDebouncerIfReconfigured();

        if (debouncer.isIdle())
        {
            return;
        }

        for (ResourceKey<Level> key : debouncer.claimDue(now()))
        {
            final ServerLevel level = server.getLevel(key);

            if (level == null)
            {
                // unloaded between being marked dirty and being claimed
                debouncer.release(key);
                debouncer.forget(key);
                deliver(key, SoulAnalysis.empty(key, now()));
                continue;
            }

            beginScan(server, level);
        }
    }

    /**
     * Snapshot on the server thread, scan and classify on a worker, apply back on the server
     * thread.
     */
    private static void beginScan(MinecraftServer server, ServerLevel level)
    {
        final ResourceKey<Level> key = level.dimension();

        if (!SoulHomeConfig.enabled())
        {
            // the feature is off: forget what was found rather than leaving stale buffs in place
            debouncer.release(key);
            applyResults(server, key, List.of(), 0L);
            finishEmpty(key);
            return;
        }

        final SnapshotBlockVolume.Capture capture;

        try
        {
            capture = SnapshotBlockVolume.capture(level, SoulHomeConfig.scanSettings());
        }
        catch (RuntimeException e)
        {
            LogHelper.error("Could not snapshot soulhome " + key.location() + " for scanning: " + e);
            debouncer.release(key);
            deliver(key, currentOrEmpty(key));
            return;
        }

        if (capture.outcome() == SnapshotBlockVolume.Capture.Outcome.EMPTY)
        {
            // an untouched soulhome holds nothing worth scanning
            applyResults(server, key, List.of(), 0L);
            debouncer.release(key);
            finishEmpty(key);
            return;
        }

        if (capture.outcome() != SnapshotBlockVolume.Capture.Outcome.CAPTURED)
        {
            // Unreadable or too large: the scan learned nothing, which is not the same as learning
            // that the soulhome is empty. Recording it as empty here is how a player who walked
            // out of a soulhome they had just finished building lost every buff they had earned -
            // the level unloads the moment they leave, and an unloaded dimension reads as bare.
            debouncer.release(key);
            deliver(key, currentOrEmpty(key));
            return;
        }

        final SnapshotBlockVolume volume = capture.volume();

        Util.backgroundExecutor().execute(() ->
        {
            List<ClassificationResult> results;
            long contentHash;

            try
            {
                List<SoulRegion> regions = RegionScanner.scan(
                        volume, ArchetypeManager.signalFilter(), ArchetypeManager.geometryFilter(),
                        ArchetypeManager.needsClearance(), SoulHomeConfig.scanSettings());

                contentHash = SoulHomeBuffData.hashOf(regions);

                // classification is cheap next to the sweep that just happened, but it is still
                // pure computation over an immutable snapshot, so it belongs off the server thread
                results = ArchetypeManager.classifier().classify(regions);
            }
            catch (RuntimeException e)
            {
                LogHelper.error("Structure scan of soulhome " + key.location() + " failed: " + e);
                server.execute(() ->
                {
                    debouncer.release(key);
                    deliver(key, currentOrEmpty(key));
                });
                return;
            }

            final List<ClassificationResult> found = results;
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
                    debouncer.release(key);
                }
            });
        });
    }

    private static void finishScan(MinecraftServer server, ResourceKey<Level> key, List<ClassificationResult> results, long contentHash)
    {
        final SoulAnalysis analysis = new SoulAnalysis(key, results, now());

        ANALYSES.put(key, analysis);

        try
        {
            if (server.getLevel(key) != null)
            {
                applyResults(server, key, AwardedRoom.from(results), contentHash);
            }
        }
        finally
        {
            // whoever asked gets their answer even if applying the results went wrong
            deliver(key, analysis);
        }
    }

    private static void applyResults(MinecraftServer server, ResourceKey<Level> key, List<AwardedRoom> awarded, long contentHash)
    {
        final ServerLevel level = server.getLevel(key);

        if (level == null)
        {
            return;
        }

        SoulHomeBuffData.get(level).update(awarded, contentHash);

        // Pushed on every completed scan, not only when the saved results changed. A soulhome
        // whose rooms are unchanged can still have an owner whose buffs are not: what a player is
        // carrying and what their soulhome says they earned drifting apart is the one failure this
        // feature cannot explain to them, since /soulhome buffs reads the saved side. Costs
        // nothing to rule out - SoulBuffs.set is already a no-op when nothing is different.
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
        SoulAdvancements.onRoomsAwarded(player, awarded);
    }

    /**
     * Restore a player's buffs from their soulhome's last scan, without scanning anything.
     * Used on login, on respawn and on dimension change.
     *
     * <p>Always the player's <i>own</i> soulhome, never the level they happen to be standing in.
     * A bound soulkey lets people visit each other's souls, and a visitor walking into a
     * well-built library should be impressed rather than temporarily buffed.
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

        final List<AwardedRoom> awarded = SoulHomeBuffData.get(soulhome).awardedRooms();

        SoulBuffs.set(player, buffsFrom(awarded));
        SoulBuffs.sync(player);

        // a room earned while the owner was offline should still produce its toast when they
        // next log in, so the triggers are fired from the restore path too
        SoulAdvancements.onRoomsAwarded(player, awarded);

        sendBounds(player, soulhome);
    }

    /**
     * Tell this player's client the box their own soulhome is currently bounded by (#78/#79), so
     * the firmament and verge can be drawn and the Soul Lens can report on them without either one
     * needing to read server-only config directly. Sent alongside every other resync in
     * {@link #refresh}, since the box only changes with rank and rank does not move on its own -
     * there is no separate trigger for it yet.
     */
    private static void sendBounds(ServerPlayer player, ServerLevel soulhome)
    {
        if (!SoulHomeConfig.enforceBounds())
        {
            return;
        }

        final SoulBounds bounds = SoulHomeConfig.soulBounds(0);

        final List<Integer> legacyBox = SoulHomeBuffData.get(soulhome).legacyBox()
                .map(box -> List.of(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
                .orElse(List.of());

        Network.sendTo(new SyncSoulBoundsMessage(
                soulhome.dimension().location().toString(), bounds.floorY(), bounds.ceilingY(),
                bounds.vergeHalfExtent(), legacyBox), player);
    }

    /** The rooms this player's own soulhome has been awarded, as of its last scan. */
    public static List<AwardedRoom> awardedRoomsOf(ServerPlayer player)
    {
        final MinecraftServer server = player.getServer();

        if (server == null)
        {
            return List.of();
        }

        final ServerLevel soulhome = server.getLevel(soulDimensionKeyOf(player));

        return soulhome == null ? List.of() : SoulHomeBuffData.get(soulhome).awardedRooms();
    }

    /**
     * This player's buffs and the rooms they came from, recomputed from their soulhome's last
     * scan. The same call the buffs themselves are computed by, so what a player is told and what
     * they are given can never drift apart.
     */
    public static BuffBreakdown explainBuffs(ServerPlayer player)
    {
        if (!SoulHomeConfig.enabled())
        {
            return BuffBreakdown.EMPTY;
        }

        return BuffCalculator.explain(
                awardedRoomsOf(player), ArchetypeManager.archetypes(), SoulHomeConfig.buffSettings());
    }

    /** This player's own soul dimension, or null if they have never opened it. */
    public static ServerLevel soulhomeOf(ServerPlayer player)
    {
        final MinecraftServer server = player.getServer();
        return server == null ? null : server.getLevel(soulDimensionKeyOf(player));
    }

    private static SoulBuffSet buffsFrom(List<AwardedRoom> awarded)
    {
        if (!SoulHomeConfig.enabled())
        {
            return SoulBuffSet.empty();
        }

        return BuffCalculator.computeFromAwarded(
                awarded, ArchetypeManager.archetypes(), SoulHomeConfig.buffSettings());
    }

    private static ResourceKey<Level> soulDimensionKeyOf(ServerPlayer player)
    {
        //mirrors how DimensionRegistry names a soul dimension: the owner's UUID
        return ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocationHelper.prefix(player.getUUID().toString()));
    }

    /** The level, if it is a server-side soulhome and the feature is switched on. */
    private static Optional<ServerLevel> soulLevel(Level level)
    {
        if (!SoulHomeConfig.enabled() || !(level instanceof ServerLevel serverLevel))
        {
            return Optional.empty();
        }

        return DimensionHelper.soulOwner(serverLevel).isPresent()
                ? Optional.of(serverLevel)
                : Optional.empty();
    }

    /** Record and hand out "there is nothing here", for a soulhome with nothing in it. */
    private static void finishEmpty(ResourceKey<Level> key)
    {
        final SoulAnalysis empty = SoulAnalysis.empty(key, now());

        ANALYSES.put(key, empty);
        deliver(key, empty);
    }

    /**
     * What is known about this soulhome right now. Used when a scan failed: the previous answer is
     * stale but true, and is a better thing to show than a blank.
     */
    private static SoulAnalysis currentOrEmpty(ResourceKey<Level> key)
    {
        final SoulAnalysis known = ANALYSES.get(key);
        return known == null ? SoulAnalysis.empty(key, now()) : known;
    }

    /** Hand a completed analysis to everyone who asked for one, and clear the queue. */
    private static void deliver(ResourceKey<Level> key, SoulAnalysis analysis)
    {
        final List<Consumer<SoulAnalysis>> waiting = WAITING.remove(key);

        if (waiting == null)
        {
            return;
        }

        for (Consumer<SoulAnalysis> callback : waiting)
        {
            try
            {
                callback.accept(analysis);
            }
            catch (RuntimeException e)
            {
                // one broken listener must not swallow the others' answers
                LogHelper.error("A soulhome analysis listener failed: " + e);
            }
        }
    }

    /**
     * Pick up changed debounce timings. Only while idle: rebuilding mid-flight would drop the
     * in-flight marker and let the same soulhome be scanned twice at once.
     */
    private static void rebuildDebouncerIfReconfigured()
    {
        final long quietPeriod = SoulHomeConfig.quietPeriodMillis();
        final long maxDelay = SoulHomeConfig.maxScanDelayMillis();

        if (quietPeriod == debouncerQuietPeriod && maxDelay == debouncerMaxDelay)
        {
            return;
        }

        if (!debouncer.isIdle())
        {
            return;
        }

        debouncerQuietPeriod = quietPeriod;
        debouncerMaxDelay = maxDelay;
        debouncer = newDebouncer();
    }

    private static ScanDebouncer<ResourceKey<Level>> newDebouncer()
    {
        return new ScanDebouncer<>(
                SoulHomeConfig.quietPeriodMillis(), SoulHomeConfig.maxScanDelayMillis());
    }

    private static long now()
    {
        return System.currentTimeMillis();
    }
}
