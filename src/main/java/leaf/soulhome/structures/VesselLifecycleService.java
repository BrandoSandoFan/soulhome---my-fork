/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.SoulHome;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.entity.SoulSevered;
import leaf.soulhome.entity.SoulVesselEntity;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.utils.PlayerHelper;
import leaf.soulhome.utils.ResourceLocationHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Soul Vessel's whole lifecycle (#182): spawning one where the Soul Key or a Meditation
 * Cushion finds a player, and everything that can end one afterwards. {@code SoulKeyItem},
 * {@code BoundSoulkey} and {@code MeditationService} are the only callers of {@link #onKeyUse};
 * Soulgaze (#187) comes in through {@link #spawnForGaze} and {@link #endGaze}. One code path for
 * every body, with only the fragility - and which trip a disturbance ends - differing.
 *
 * <p>Also the body's two consequences: {@link #forwardHit} (#185), and the spill when a forwarded
 * hit kills its owner, {@link #relocateDrops} and {@link #relocateExperience} (#186).
 *
 * <p>{@link #ACTIVE} exists purely as a fast owner-to-vessel lookup; {@link SoulVesselEntity}
 * itself is what actually persists (it is a normal saved entity), so a server restart rebuilds this
 * map for free as each vessel's {@code onAddedToWorld} re-registers it.
 */
public final class VesselLifecycleService
{
    /**
     * NeoForge's forced-chunk API is controller-based rather than the flat static calls Forge had:
     * a mod registers one of these (see {@code EntityRegistry#registerTicketControllers}) and every
     * ticket it ever forces goes through it, including the validation callback below.
     */
    public static final TicketController TICKET_CONTROLLER =
            new TicketController(ResourceLocationHelper.prefix("soul_vessel"), VesselLifecycleService::validateTickets);

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

        SoulVesselEntity.spawn(level, player, fragility, false);
    }

    /**
     * The body a gazer leaves standing while they look into someone else's soul (#187) - the same
     * entity, the same ticket, the same damage transfer, and only its own fragility. Returns false
     * if a body could not be left, which refuses the gaze rather than letting it happen bodiless:
     * rule 2 of #181, there is no way into a soul that does not leave a vessel.
     */
    public static boolean spawnForGaze(ServerPlayer player)
    {
        if (ACTIVE.containsKey(player.getUUID()) || !(player.level() instanceof ServerLevel level))
        {
            return false;
        }

        SoulVesselEntity.spawn(level, player, SoulHomeConfig.vesselSettings().gazeFragility(), true);
        return true;
    }

    /** A gaze ended on its own terms - the timer, the gazer's choice, the target's soul closing. No ejection. */
    public static void endGaze(ServerPlayer player)
    {
        final SoulVesselEntity vessel = ACTIVE.get(player.getUUID());

        if (vessel != null && vessel.isGaze())
        {
            vessel.markReturningPeacefully();
            vessel.discard();
        }
    }

    /** The live body a player left, if any - a meditator's, a key user's, or a gazer's alike. */
    public static Optional<SoulVesselEntity> vesselOf(UUID ownerId)
    {
        return Optional.ofNullable(ACTIVE.get(ownerId));
    }

    /**
     * A hit on {@code vessel}, dealt to its owner as {@code soulhome:soul_severed} (#185). Scaled by
     * the vessel's fragility and gated by {@code vessel.damage_transfer}; with the switch off this
     * does nothing and a vessel is a decoration again.
     *
     * <p>Nothing is forwarded to an owner in creative or spectator, read through
     * {@link GazeService#effectiveGameType} so a gazer is judged by the mode they will return to
     * rather than the spectator mode the gaze lent them. And a gazer is the one owner who needs
     * help being hurt at all, since spectator mode is invulnerable - see
     * {@link GazeService#hurtWhileGazing}.
     */
    public static void forwardHit(SoulVesselEntity vessel, DamageSource source, float amount)
    {
        final float forwarded = SoulHomeConfig.vesselSettings().forwardedDamage(amount, vessel.getFragility());

        if (forwarded <= 0f || !(vessel.level() instanceof ServerLevel level))
        {
            return;
        }

        final UUID ownerId = vessel.getOwnerId().orElse(null);
        final ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);

        // a body whose owner is not away in some soul is not currently anyone's - an owner who got
        // out by a door this service did not see should not be hurt by a stale body left behind
        if (owner == null || !owner.isAlive() || !DimensionHelper.isInSoulDimension(owner))
        {
            return;
        }

        final GameType mode = GazeService.effectiveGameType(owner);

        if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR)
        {
            return;
        }

        final DamageSource severed = SoulSevered.from(owner.serverLevel(), source);

        if (GazeService.isGazing(owner))
        {
            GazeService.hurtWhileGazing(owner, severed, forwarded);
        }
        else
        {
            owner.hurt(severed, forwarded);
        }
    }

    /**
     * Moves a dead owner's drops out of the soul they died in and onto the floor where their body
     * was (#186), each with a small outward push so it reads as a spill. Returns false - leaving
     * the drops where vanilla put them - for a death with no body to spill at.
     *
     * <p>Spawned at the vessel's own feet, nudged up out of its block if that block is solid, so a
     * spill is never spawned inside a wall.
     */
    public static boolean relocateDrops(ServerPlayer owner, Collection<ItemEntity> drops)
    {
        final SoulVesselEntity vessel = ACTIVE.get(owner.getUUID());

        if (vessel == null || !(vessel.level() instanceof ServerLevel level) || !DimensionHelper.isInSoulDimension(owner))
        {
            return false;
        }

        final double scatter = SoulHomeConfig.vesselSettings().dropScatter();
        final Vec3 at = spillPosition(level, vessel);

        for (ItemEntity original : drops)
        {
            final ItemStack stack = original.getItem();

            if (stack.isEmpty())
            {
                continue;
            }

            final ItemEntity spilled = new ItemEntity(level, at.x, at.y, at.z, stack.copy());
            final double angle = level.random.nextDouble() * Math.PI * 2d;
            final double push = scatter * (0.5d + level.random.nextDouble() * 0.5d);

            spilled.setDeltaMovement(Math.cos(angle) * push, 0.2d, Math.sin(angle) * push);
            spilled.setDefaultPickUpDelay();
            level.addFreshEntity(spilled);
        }

        return true;
    }

    /** The experience half of {@link #relocateDrops}. Returns false if there is no body to drop it at. */
    public static boolean relocateExperience(ServerPlayer owner, int amount)
    {
        final SoulVesselEntity vessel = ACTIVE.get(owner.getUUID());

        if (vessel == null || !(vessel.level() instanceof ServerLevel level) || !DimensionHelper.isInSoulDimension(owner))
        {
            return false;
        }

        if (amount > 0)
        {
            ExperienceOrb.award(level, spillPosition(level, vessel), amount);
        }

        return true;
    }

    /**
     * The body goes with its owner's death (#186): with particles rather than by blinking out, and
     * only once the drops are already on the floor - its ticket is what keeps the chunk they landed
     * in loaded until they have. Marked peaceful, since a dead owner is not someone to eject.
     */
    public static void removeAfterDeath(ServerPlayer owner)
    {
        final SoulVesselEntity vessel = ACTIVE.get(owner.getUUID());

        if (vessel == null)
        {
            return;
        }

        if (vessel.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.SOUL, vessel.getX(), vessel.getY() + 0.6d, vessel.getZ(),
                    24, 0.3d, 0.4d, 0.3d, 0.02d);
            level.playSound(null, vessel.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        }

        vessel.markReturningPeacefully();
        vessel.discard();
    }

    /**
     * Where the vessel's owner died, as far as the recovery compass and the death screen are
     * concerned: at the body, which is where their things are, not at a coordinate in a soul
     * dimension where they are not (#186).
     */
    public static Optional<GlobalPos> deathPositionOf(ServerPlayer owner)
    {
        final SoulVesselEntity vessel = ACTIVE.get(owner.getUUID());

        if (vessel == null || !DimensionHelper.isInSoulDimension(owner))
        {
            return Optional.empty();
        }

        return Optional.of(GlobalPos.of(vessel.level().dimension(), vessel.blockPosition()));
    }

    private static Vec3 spillPosition(ServerLevel level, SoulVesselEntity vessel)
    {
        BlockPos pos = vessel.blockPosition();

        // never inside a block: a body knocked against a wall, or sat in a slab-height nook, would
        // otherwise spawn its owner's inventory inside the wall where nobody can reach it
        for (int lift = 0; lift < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); lift++)
        {
            pos = pos.above();
        }

        return new Vec3(vessel.getX(), Math.max(vessel.getY(), pos.getY()) + 0.25d, vessel.getZ());
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

        // a gazer's body disturbed ends the gaze, the same instant a meditator's would end their
        // trip (#187) - through the session's own exit, which restores their game mode as well as
        // their position, rather than through FlipDimension, which knows nothing about either
        if (vessel.isGaze())
        {
            if (owner != null)
            {
                GazeService.end(owner, GazeService.EndReason.DISTURBED);
            }

            return;
        }

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
     * {@link #TICKET_CONTROLLER}'s validation callback, run once by NeoForge when a level reinstates
     * its forced chunks. NeoForge persists a ticket across a restart even if the vessel that
     * requested it never got the chance to release it - a crash mid-meditation is the ordinary way
     * that happens - so a ticket whose owner is not a live {@link SoulVesselEntity} is dropped here
     * rather than being held forever. {@code level.getEntity(UUID)} only finds an already-loaded
     * entity; if the vessel's own chunk has not finished loading by the time this runs, that is
     * indistinguishable from a genuine orphan and the ticket is dropped early. That has not been
     * exercised against a live server in this change - if it turns out to fire before the vessel
     * deserialises, this needs a delayed second look rather than a same-tick verdict.
     */
    private static void validateTickets(ServerLevel level, TicketHelper helper)
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

    /** The Soul Key's fragility, from {@code vessel.key_fragility}. */
    public static float defaultKeyFragility()
    {
        return SoulHomeConfig.vesselSettings().keyFragility();
    }

    /** The cushion's own, reduced fragility (#183), from {@code vessel.cushion_fragility}. */
    public static float defaultCushionFragility()
    {
        return SoulHomeConfig.vesselSettings().cushionFragility();
    }
}
