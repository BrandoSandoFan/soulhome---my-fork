/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.SoulHome;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.entity.SoulVesselEntity;
import leaf.soulhome.structures.core.VesselSettings;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.utils.PlayerHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Soul Vessel's whole lifecycle (#182): spawning one where the Soul Key or a Meditation
 * Cushion finds a player, and everything that can end one afterwards. {@code SoulKeyItem},
 * {@code BoundSoulkey} and {@code MeditationService} are the only callers of {@link #onKeyUse} -
 * Soulgaze (#187) is expected to call its own way in through here too, rather than touching
 * {@link SoulVesselEntity} directly. One code path for every entry, with only the fragility differing.
 *
 * <p>{@link #ACTIVE} exists purely as a fast owner-to-vessel lookup; {@link SoulVesselEntity}
 * itself is what actually persists (it is a normal saved entity), so a server restart rebuilds this
 * map for free as each vessel's {@code onAddedToWorld} re-registers it.
 */
public final class VesselLifecycleService
{
    private static final Map<UUID, SoulVesselEntity> ACTIVE = new ConcurrentHashMap<>();

    private VesselLifecycleService()
    {
    }

    /**
     * Called by both Soul Key items, before {@code DimensionHelper.FlipDimension} - see #184.
     * Whichever direction the key is about to travel, a vessel is either about to be needed or
     * about to have done its job; nothing here decides that direction itself, since
     * {@code isInSoulDimension} is exactly the same test {@code FlipDimension} makes a moment later.
     */
    public static void onKeyUse(ServerPlayer player, float fragility)
    {
        if (DimensionHelper.isInSoulDimension(player))
        {
            removeForReturn(player);
        }
        else
        {
            spawn(player, fragility);
        }
    }

    private static void spawn(ServerPlayer player, float fragility)
    {
        if (ACTIVE.containsKey(player.getUUID()) || !(player.level() instanceof ServerLevel level))
        {
            // a player cannot already have a live vessel and still be outside their soul - this is
            // a safety net against double-spawning, not a path anything is expected to take
            return;
        }

        SoulVesselEntity.spawn(level, player, fragility);
    }

    /** The owner's own return through the key or a cushion - no message, no ejection, the trip simply ends. */
    private static void removeForReturn(ServerPlayer player)
    {
        final SoulVesselEntity vessel = ACTIVE.get(player.getUUID());

        if (vessel != null)
        {
            // the body may have been pushed, dragged or knocked since it was left - resync the
            // saved return position to where it actually is before it is gone, or FlipDimension
            // would send the player back to where they stood the moment they entered instead (#183)
            syncReturnPosition(player, vessel);
            vessel.markReturningPeacefully();
            vessel.discard();
        }
    }

    /**
     * Overwrites the same {@code LAST_DIMENSION_*} tag {@code DimensionHelper.FlipDimension} reads
     * on the way out, with the vessel's own live position rather than the one recorded the moment
     * the soul was entered. Cheaper than teaching {@code FlipDimension} to consult a live vessel
     * itself, and correct for the same reason: whichever door the return trip uses, it is the same
     * tag being read a moment later.
     */
    private static void syncReturnPosition(ServerPlayer player, SoulVesselEntity vessel)
    {
        if (!(vessel.level() instanceof ServerLevel vesselLevel))
        {
            return;
        }

        // a vessel is never supposed to be inside a soul dimension at all (see EntityHelper's own
        // exclusion of it from FlipDimension's sweep) - but trusting that unconditionally here would
        // turn any future slip in that guarantee back into exactly the "the key cannot find its way
        // out" bug it exists to prevent: resyncing from a vessel that is itself inside a soul would
        // overwrite LAST_DIMENSION with the soul's own address, and the owner's next return trip
        // would find "home" and "away" pointing at the same place
        if (DimensionHelper.isInSoulDimension(vessel))
        {
            LogHelper.warn("Soul vessel for " + player.getGameProfile().getName()
                    + " was found inside a soul dimension; leaving the saved return position untouched.");
            return;
        }

        final CompoundTag soulNBT = PlayerHelper.getPersistentTag(player, SoulHome.SOULHOME_LOC.toString());
        final ResourceLocation dimension = vesselLevel.dimension().location();

        soulNBT.putDouble(Constants.NBTKeys.LAST_DIMENSION_X, vessel.getX());
        soulNBT.putDouble(Constants.NBTKeys.LAST_DIMENSION_Y, vessel.getY());
        soulNBT.putDouble(Constants.NBTKeys.LAST_DIMENSION_Z, vessel.getZ());
        soulNBT.putString(Constants.NBTKeys.LAST_DIMENSION_MOD_ID, dimension.getNamespace());
        soulNBT.putString(Constants.NBTKeys.LAST_DIMENSION_MOD_DIMENSION, dimension.getPath());
    }

    /**
     * The owner logging out while their vessel sits in the overworld. Reuses the peaceful path
     * rather than {@link #disturb}: nobody is currently exposed by the vessel disappearing, since
     * the player they would otherwise eject is not connected to eject.
     */
    public static void onOwnerLoggedOut(ServerPlayer player)
    {
        removeForReturn(player);
    }

    /** Called by {@link SoulVesselEntity#onAddedToWorld} - including every reload, not just a fresh spawn. */
    public static void onVesselAdded(SoulVesselEntity vessel)
    {
        vessel.getOwnerId().ifPresent(id -> ACTIVE.put(id, vessel));
    }

    /**
     * Called by {@link SoulVesselEntity#onRemovedFromWorld}. A peaceful return has already been
     * marked by {@link #removeForReturn}; anything else reaching here - killed, {@code /kill}'d, a
     * ticket an operator cleared - is a disturbance and ejects the owner per rule 3 of #181.
     */
    public static void onVesselRemoved(SoulVesselEntity vessel)
    {
        vessel.getOwnerId().ifPresent(id -> ACTIVE.remove(id, vessel));

        if (!vessel.isReturningPeacefully())
        {
            disturb(vessel);
        }
    }

    private static void disturb(SoulVesselEntity vessel)
    {
        final UUID ownerId = vessel.getOwnerId().orElse(null);

        if (ownerId == null || !(vessel.level() instanceof ServerLevel level))
        {
            return;
        }

        final MinecraftServer server = level.getServer();
        final ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);

        // not currently exposed by this vessel disappearing: offline, or already back out under
        // their own steam a tick earlier than this event landed
        if (owner == null || !DimensionHelper.isInSoulDimension(owner))
        {
            return;
        }

        owner.sendSystemMessage(TextHelper.createTranslatedText(Constants.StringKeys.VESSEL_DISTURBED));

        // the same fallback position FlipDimension's own voluntary exit reads - a disturbed vessel
        // is not a different kind of leaving, just an uninvited one
        DimensionHelper.FlipDimension(owner, server, List.of(owner), ownerId);
    }

    /**
     * Registered once, from the mod constructor, against every forced-chunk ticket this mod owns.
     * Forge persists a ticket across a restart even if the vessel that requested it never got the
     * chance to release it - a crash mid-meditation is the ordinary way that happens - so a ticket
     * whose owner is not a live {@link SoulVesselEntity} is dropped here rather than being held
     * forever. {@code level.getEntity(UUID)} only finds an already-loaded entity; if the vessel's
     * own chunk has not finished loading by the time this runs, that is indistinguishable from a
     * genuine orphan and the ticket is dropped early. That has not been exercised against a live
     * server in this change - if it turns out to fire before the vessel deserialises, this needs a
     * delayed second look rather than a same-tick verdict.
     */
    public static void validateTickets(ServerLevel level, ForgeChunkManager.TicketHelper helper)
    {
        for (UUID ticketOwner : new ArrayList<>(helper.getEntityTickets().keySet()))
        {
            if (!(level.getEntity(ticketOwner) instanceof SoulVesselEntity))
            {
                LogHelper.warn("Releasing a stale soul vessel chunk ticket with no vessel behind it: " + ticketOwner);
                helper.removeAllTickets(ticketOwner);
            }
        }
    }

    /** {@code VesselSettings.DEFAULTS} today - a config knob is #185's to add once fragility does anything. */
    public static float defaultKeyFragility()
    {
        return VesselSettings.DEFAULTS.keyFragility();
    }

    /** The cushion's own, reduced fragility (#183) - see {@link #defaultKeyFragility}. */
    public static float defaultCushionFragility()
    {
        return VesselSettings.DEFAULTS.cushionFragility();
    }
}
