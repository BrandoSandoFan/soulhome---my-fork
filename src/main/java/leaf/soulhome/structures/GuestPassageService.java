/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entering a soul that is not your own requires ascension rank {@code dimension.guest_rank_required}
 * or above (#184), of a maximum of {@link SoulBounds#MAX_RANK}. Walking into another person's soul
 * in the flesh is near-endgame, and rank now has a second job beyond widening your own box.
 *
 * <p>The gate reads the <b>traveller's own</b> rank, not the rank of the soul being entered - the
 * ability to walk into someone else's soul is something a high-rank person can do, so it is a
 * property of the visitor.
 *
 * <p>Two different moments read this gate, both funnelled through {@code DimensionHelper.FlipDimension}
 * so neither the Soul Key, a bound key, nor meditation has to know about the other:
 *
 * <ul>
 *   <li>the traveller actually using a key or channel to enter someone else's soul - checked by the
 *       caller, before the vessel and before the sweep, so a refusal costs nothing;</li>
 *   <li>anyone merely swept along within range of that traveller - filtered here, in
 *       {@link #filterGuests}, since a bystander who happens to be standing nearby did not choose
 *       to go anywhere and is left behind rather than silently dragged in.</li>
 * </ul>
 */
public final class GuestPassageService
{
    private GuestPassageService()
    {
    }

    /**
     * Whether {@code traveller} may enter a soul that is not their own, read off their own rank -
     * never the soul being entered.
     */
    public static boolean canEnterGuestSoul(ServerPlayer traveller)
    {
        return ownRank(traveller) >= SoulHomeConfig.guestRankRequired();
    }

    /**
     * The traveller's own ascension rank, read from their own soul without creating it. A player
     * who has never once visited their own soul has never ascended it either, so the same "rank 0"
     * a fresh soulhome starts at is the correct answer here - not a reason to spin up a dimension
     * nobody asked for just to find that out.
     */
    public static int ownRank(ServerPlayer traveller)
    {
        final MinecraftServer server = traveller.getServer();

        if (server == null)
        {
            return 0;
        }

        final ServerLevel ownSoul = server.getLevel(DimensionHelper.soulDimensionKey(traveller.getUUID()));

        return ownSoul == null ? 0 : SoulHomeBuffData.get(ownSoul).ascensionRank();
    }

    /**
     * Filters the entities a soul entry is about to sweep along, dropping any player among them who
     * is entering a soul that is not their own and does not clear the gate. {@code traveller} - the
     * one whose key or channel started this trip - is never filtered here: whichever gate applies
     * to them has already been checked by the caller before this sweep began.
     *
     * <p>Non-player entities always come along ("bring your dog"), and a player entering their own
     * soul (which happens whenever {@code targetSoulUUID} is their own UUID - the ordinary case for
     * everyone but the guest being escorted in) is never gated either.
     */
    public static List<Entity> filterGuests(Player traveller, List<Entity> entitiesInRange, UUID targetSoulUUID)
    {
        final List<Entity> allowed = new ArrayList<>(entitiesInRange.size());

        for (Entity entity : entitiesInRange)
        {
            if (entity == traveller
                    || !(entity instanceof ServerPlayer passenger)
                    || targetSoulUUID.equals(passenger.getUUID())
                    || canEnterGuestSoul(passenger))
            {
                allowed.add(entity);
                continue;
            }

            passenger.sendSystemMessage(TextHelper.createTranslatedText(Constants.StringKeys.GUEST_LEFT_BEHIND));
        }

        return allowed;
    }

    /** Told to the traveller themselves when their own rank refuses the trip outright. */
    public static void tellGateRefused(ServerPlayer traveller)
    {
        traveller.sendSystemMessage(TextHelper.createTranslatedText(
                Constants.StringKeys.GUEST_RANK_REQUIRED,
                SoulBounds.rankLabel(SoulHomeConfig.guestRankRequired())));
    }
}
