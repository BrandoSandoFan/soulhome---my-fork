/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server's half of the Ambience epic (#163): work out what a soul is like, and tell the people
 * standing in it.
 *
 * <p>Deliberately thin, and deliberately a consumer. The classification this reads was computed for
 * buffs and is not re-run, nothing here is scheduled, and nothing here writes to a soulhome's save.
 * The epic's promise is "no performance cost on the server; the classification already exists, this
 * consumes it", and the way to keep that promise is for this file to have nothing in it that could
 * cost anything.
 *
 * <p>Sent to the dimension rather than to an owner - see {@link SyncSoulAmbienceMessage} for why a
 * visitor gets the sky of the soul they are in rather than the one their own soulhome would have.
 */
public final class SoulAmbienceService
{
    private SoulAmbienceService()
    {
    }

    /** Tell everyone in this soulhome what it is like now. A no-op for any other kind of level. */
    public static void broadcast(ServerLevel level)
    {
        final SyncSoulAmbienceMessage message = messageFor(level);

        if (message != null)
        {
            Network.sendToAllInWorld(message, level);
        }
    }

    /**
     * Tell one player about the soul they have just arrived in.
     *
     * <p>Sent on arrival rather than left to the next scan: a scan is debounced by design, and a
     * player who walks into their own soul and watches it look like nothing for twenty seconds has
     * met the ambience equivalent of the "my buffs vanished" fault.
     */
    public static void sendTo(ServerPlayer player, ServerLevel level)
    {
        final SyncSoulAmbienceMessage message = messageFor(level);

        if (message != null)
        {
            Network.sendTo(message, player);
        }
    }

    /** The same, for whichever level the player is standing in. */
    public static void sendTo(ServerPlayer player)
    {
        final MinecraftServer server = player.getServer();

        if (server != null && player.level() instanceof ServerLevel level)
        {
            sendTo(player, level);
        }
    }

    /**
     * What this soul is like, or null if it is not a soul at all.
     *
     * <p>Reads <b>every</b> classified room, attuned or not (#151): attunement decides what a
     * player carries out of their soul, and a library they are not carrying today is still standing
     * in it. Reading the carried subset here would make a loadout change repaint the sky, which is
     * both wrong and the sort of thing that reads as a bug.
     */
    private static SyncSoulAmbienceMessage messageFor(ServerLevel level)
    {
        if (level == null || DimensionHelper.soulOwner(level).isEmpty())
        {
            return null;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(level);
        final int rank = data.ascensionRank();
        final SoulBounds bounds = SoulHomeConfig.soulBounds(rank);

        final SoulCharacter character = SoulHomeConfig.enabled()
                ? SoulCharacter.of(data.awardedRooms(), ArchetypeManager.byId())
                : SoulCharacter.EMPTY;

        return SyncSoulAmbienceMessage.of(
                level.dimension().location().toString(), rank, SoulHomeConfig.maxRank(),
                reach(data, bounds), bounds.ceilingY(), character);
    }

    /**
     * How far this soul reads as going: its box, or its legacy grant (#80) where that reaches
     * further.
     *
     * <p>The distinction matters because fog is placed past this. A grandfathered soulhome can hold
     * a build standing outside today's verge - that build is somewhere its owner may be, and it is
     * theirs, so a haze beginning at the verge would be the sky telling them their own hall is out
     * of bounds. #164 asks for exactly this and it costs one comparison.
     */
    private static int reach(SoulHomeBuffData data, SoulBounds bounds)
    {
        int reach = bounds.vergeHalfExtent();

        if (data.legacyBox().isPresent())
        {
            final var box = data.legacyBox().get();

            reach = Math.max(reach, Math.max(
                    Math.max(Math.abs(box.minX()), Math.abs(box.maxX())),
                    Math.max(Math.abs(box.minZ()), Math.abs(box.maxZ()))));
        }

        return reach;
    }
}
