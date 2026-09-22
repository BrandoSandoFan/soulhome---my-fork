/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.registry.BlocksRegistry;
import leaf.soulhome.structures.core.MeditationAdjacency;
import leaf.soulhome.structures.core.MeditationSettings;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.EntityHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The channel a Meditation Cushion (#183) is held with, and the same channel the same bind returns
 * you with (#181 rule 2's other half). One state map, driven by {@code MeditationEvents}' own
 * per-player tick, mirroring how {@code AscensionRitualService} is driven by {@code AscensionEvents}.
 *
 * <p>Completion calls exactly the same {@link VesselLifecycleService#onKeyUse} the Soul Key calls -
 * a cushion is a second door, never a second vessel code path. The only thing meditation supplies
 * that the key does not is its own, reduced fragility.
 */
public final class MeditationService
{
    /** A player who has not moved at all from where they started - the same test the ascension ritual uses. */
    private record Channel(BlockPos anchor, int ticksElapsed)
    {
    }

    private static final Map<UUID, Channel> ACTIVE = new ConcurrentHashMap<>();

    private MeditationService()
    {
    }

    /** The client bind went down or up - see {@code MeditateMessage}. */
    public static void setHeld(ServerPlayer player, boolean held)
    {
        if (held)
        {
            start(player);
        }
        else
        {
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void start(ServerPlayer player)
    {
        if (ACTIVE.containsKey(player.getUUID()))
        {
            return;
        }

        // returning needs no cushion at all, regardless of how the trip in was made (#181 rule 2) -
        // a player who entered by key and lost it must never be stuck
        if (!DimensionHelper.isInSoulDimension(player) && !nearCushion(player))
        {
            player.sendSystemMessage(TextHelper.createTranslatedText(Constants.StringKeys.MEDITATION_NO_CUSHION));
            return;
        }

        ACTIVE.put(player.getUUID(), new Channel(player.blockPosition(), 0));
    }

    /** Called once per online player per tick, from {@code MeditationEvents}. */
    public static void tick(ServerPlayer player)
    {
        final Channel channel = ACTIVE.get(player.getUUID());

        if (channel == null)
        {
            return;
        }

        //moving at all aborts the channel - the same test the ascension ritual holds a player to,
        //and the one that keeps a channel from being a way to walk out of a fight while it finishes
        if (!channel.anchor().equals(player.blockPosition()))
        {
            ACTIVE.remove(player.getUUID());
            return;
        }

        final MeditationSettings settings = SoulHomeConfig.meditationSettings();
        final int ticksElapsed = channel.ticksElapsed() + 1;

        if (ticksElapsed >= settings.cushionChannelTicks())
        {
            ACTIVE.remove(player.getUUID());
            complete(player);
            return;
        }

        ACTIVE.put(player.getUUID(), new Channel(channel.anchor(), ticksElapsed));
    }

    /** A hit landed on the meditating player - see rule "the channel aborts on damage" in #183. */
    public static void onDamaged(ServerPlayer player)
    {
        ACTIVE.remove(player.getUUID());
    }

    /** The player left in some way that was not the channel completing - logout, death, a dimension change from elsewhere. */
    public static void abort(ServerPlayer player)
    {
        ACTIVE.remove(player.getUUID());
    }

    private static void complete(ServerPlayer player)
    {
        final boolean returning = DimensionHelper.isInSoulDimension(player);

        // fragility only means anything on the way in - the vessel is being removed, not spawned,
        // on the way out, and onKeyUse ignores the value for that direction
        final float fragility = returning
                ? VesselLifecycleService.defaultKeyFragility()
                : VesselLifecycleService.defaultCushionFragility();

        VesselLifecycleService.onKeyUse(player, fragility);

        DimensionHelper.FlipDimension(
                player,
                player.getServer(),
                EntityHelper.getEntitiesInRange(player, 2.5d, true),
                player.getUUID());
    }

    /**
     * On top of the cushion, or - per {@code cushionDiagonalAdjacency} - beside it at floor level or
     * one below. Read fresh at channel start rather than cached: no block entity, per #183.
     *
     * <p>The offsets themselves live in {@link MeditationAdjacency}, Minecraft-free and pinned by
     * a test, precisely because getting them wrong here once (#239: checking the cell below the
     * player instead of the player's own cell) shipped a cushion that silently never detected the
     * one thing it exists for - a player standing on top of it.
     */
    private static boolean nearCushion(ServerPlayer player)
    {
        final Level level = player.level();
        final BlockPos base = player.blockPosition();
        final boolean diagonal = SoulHomeConfig.meditationSettings().cushionDiagonalAdjacency();

        for (int[] offset : MeditationAdjacency.offsets(diagonal))
        {
            if (isCushion(level, base.offset(offset[0], offset[1], offset[2])))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isCushion(Level level, BlockPos pos)
    {
        return level.getBlockState(pos).is(BlocksRegistry.MEDITATION_CUSHION.get());
    }
}
