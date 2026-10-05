/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSuppressionMessage;
import leaf.soulhome.structures.core.SuppressionSettings;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's half of suppression (#188): deciding who may perceive whom, and telling them.
 *
 * <p><b>The gate is here, not on the client.</b> An observer is only ever sent another player's
 * suppression if they have at least one awarded room of their own - read off their own soul, on
 * the server. A client-side gate is a gate a modified client ignores.
 *
 * <p>Sent when an observer starts tracking another player, and refreshed every
 * {@link #REFRESH_INTERVAL_TICKS} for everyone an observer can currently track. The refresh is
 * what carries a rank change (the ritual, an operator), an observer's own first room, and an
 * observer's own ascension changing how legible everyone else reads, without a hook into each of
 * those - at the cost of up to that many ticks' delay on a change nobody is watching for anyway.
 */
public final class SuppressionService
{
    public static final int REFRESH_INTERVAL_TICKS = 100;

    /**
     * Observer to the entity ids they have been sent something to draw for, so a clear is only ever
     * sent to undo something - an observer with no rooms costs no packets at all.
     */
    private static final Map<UUID, Set<Integer>> KNOWN = new ConcurrentHashMap<>();

    private SuppressionService()
    {
    }

    /** {@code observer} just began tracking {@code watched}. */
    public static void onStartTracking(ServerPlayer observer, ServerPlayer watched)
    {
        send(observer, watched, perceives(observer));
    }

    /** Once per player per tick, from {@code SuppressionEvents}; does real work every {@link #REFRESH_INTERVAL_TICKS}. */
    public static void tick(ServerPlayer observer)
    {
        if ((observer.tickCount + observer.getId()) % REFRESH_INTERVAL_TICKS != 0 || observer instanceof FakePlayer)
        {
            return;
        }

        final boolean perceives = perceives(observer);
        final ServerLevel level = observer.serverLevel();
        final double trackingRange = level.getServer().getPlayerList().getViewDistance() * 16d;

        for (ServerPlayer watched : level.players())
        {
            if (watched != observer && watched.distanceToSqr(observer) <= trackingRange * trackingRange)
            {
                send(observer, watched, perceives);
            }
        }
    }

    private static void send(ServerPlayer observer, ServerPlayer watched, boolean perceives)
    {
        final SuppressionSettings settings = SoulHomeConfig.suppressionSettings();
        final int theirRank = perceives && settings.enabled() ? GuestPassageService.ownRank(watched) : 0;

        final Set<Integer> known = KNOWN.computeIfAbsent(observer.getUUID(), id -> ConcurrentHashMap.newKeySet());

        if (theirRank <= 0)
        {
            if (known.remove(watched.getId()))
            {
                Network.sendTo(SyncSuppressionMessage.cleared(watched.getId()), observer);
            }

            return;
        }

        known.add(watched.getId());

        final int yourRank = GuestPassageService.ownRank(observer);
        final SuppressionSettings.Signature signature =
                settings.signature(theirRank, yourRank, SoulHomeConfig.maxRank());
        final double range = settings.perceptionRangeFor(theirRank);

        Network.sendTo(new SyncSuppressionMessage(
                watched.getId(),
                signature.rings(),
                (float) signature.radius(),
                (float) signature.strength(),
                (float) signature.legibility(),
                (float) signature.displacement(),
                (float) range,
                settings.distortion(),
                settings.audio()), observer);

        // perceiving is seeing: in range, in plain sight, and not a spectator nobody can see
        if (!watched.isSpectator()
                && !watched.isInvisible()
                && watched.distanceToSqr(observer) <= range * range
                && observer.hasLineOfSight(watched))
        {
            SoulAdvancements.onMoment(observer, SoulAdvancements.Moment.SUPPRESSION);
        }
    }

    /** Forget an observer. Called on logout and on a dimension change, since the client forgets too. */
    public static void forget(ServerPlayer observer)
    {
        KNOWN.remove(observer.getUUID());
    }

    /**
     * Whether this player perceives suppression at all: at least one awarded room in their own soul.
     * A player who has never opened their soul has built nothing in it, and is not given one here.
     */
    private static boolean perceives(ServerPlayer observer)
    {
        final MinecraftServer server = observer.getServer();

        if (server == null)
        {
            return false;
        }

        final ServerLevel soul = server.getLevel(DimensionHelper.soulDimensionKey(observer.getUUID()));
        return soul != null && !SoulHomeBuffData.get(soul).awardedRooms().isEmpty();
    }
}
