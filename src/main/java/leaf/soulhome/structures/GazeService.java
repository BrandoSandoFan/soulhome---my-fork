/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.SoulHome;
import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.entity.SoulVesselEntity;
import leaf.soulhome.structures.core.GazeSettings;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.utils.PlayerHelper;
import leaf.soulhome.utils.TeleportHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spectator session behind Soulgaze (#187): a player looking into another player's soul, with
 * their own body left standing in the world while they do.
 *
 * <h2>Nobody is stranded as a spectator</h2>
 *
 * This is the part of the epic that can leave a player somewhere they cannot get out of, so every
 * decision here leans toward getting them home. The session is written to the gazer's own
 * persistent NBT the moment it starts - the game mode they had, where they were, when it ends,
 * whose soul - and every exit reads it back:
 *
 * <ul>
 *   <li>the timer runs out, or the gazer presses an ability key again ({@link EndReason#EXPIRED},
 *       {@link EndReason#RECALLED});</li>
 *   <li>the soul's owner was home when the gaze landed and has since left it, or had their own
 *       body disturbed, which ejects them ({@link EndReason#SOUL_CLOSED});</li>
 *   <li>the gazer's own body is disturbed ({@link EndReason#DISTURBED}) - see
 *       {@code VesselLifecycleService#disturb};</li>
 *   <li>the gazer logs out, or the server stops or crashes mid-gaze: the session outlives the
 *       connection in NBT, and {@link #onLogin} restores and closes it on the way back in;</li>
 *   <li>an operator changes their game mode or moves them out of the soul ({@link EndReason#INTERRUPTED});</li>
 *   <li>the gazer dies ({@link EndReason#DIED}) - see {@link #onGazerDied}.</li>
 * </ul>
 *
 * Both legs of the trip go through {@code TeleportHelper}, or {@code SoulTravel} would cancel them.
 *
 * <h2>A gaze is read-only</h2>
 *
 * A spectator can ride a mob's camera; inside someone else's soul that is a foothold nobody
 * designed, so {@link #tick} hands the camera straight back. The gazer never triggers a rescan -
 * {@link #isGazeTravel} is what {@code StructureEvents} checks before treating a dimension change
 * as the owner coming home - and no ability fires while gazing: {@code SoulAbilities} reads a press
 * as asking to come back instead.
 */
public final class GazeService
{
    public enum EndReason
    {
        EXPIRED,
        RECALLED,
        SOUL_CLOSED,
        DISTURBED,
        RESTORED,
        INTERRUPTED,
        DIED
    }

    private static final String NBT_SESSION = "GazeSession";
    private static final String NBT_PREVIOUS_MODE = "PreviousMode";
    private static final String NBT_RETURN_DIM = "ReturnDimension";
    private static final String NBT_RETURN_X = "ReturnX";
    private static final String NBT_RETURN_Y = "ReturnY";
    private static final String NBT_RETURN_Z = "ReturnZ";
    private static final String NBT_RETURN_YAW = "ReturnYaw";
    private static final String NBT_RETURN_PITCH = "ReturnPitch";
    private static final String NBT_EXPIRES_AT = "ExpiresAt";
    private static final String NBT_TARGET = "Target";
    private static final String NBT_TARGET_HOME = "TargetHome";

    /**
     * Everything needed to put a gazer back. {@code expiresAt} is overworld game time, which a save
     * carries across a restart - a server tick count would reset to zero and hand a restored
     * session a fresh timer.
     */
    public record Session(
            GameType previousMode,
            ResourceKey<Level> returnDimension,
            Vec3 returnPosition,
            float returnYaw,
            float returnPitch,
            long expiresAt,
            UUID target,
            boolean targetWasHome)
    {
        CompoundTag save()
        {
            final CompoundTag tag = new CompoundTag();

            tag.putString(NBT_PREVIOUS_MODE, this.previousMode.getName());
            tag.putString(NBT_RETURN_DIM, this.returnDimension.location().toString());
            tag.putDouble(NBT_RETURN_X, this.returnPosition.x);
            tag.putDouble(NBT_RETURN_Y, this.returnPosition.y);
            tag.putDouble(NBT_RETURN_Z, this.returnPosition.z);
            tag.putFloat(NBT_RETURN_YAW, this.returnYaw);
            tag.putFloat(NBT_RETURN_PITCH, this.returnPitch);
            tag.putLong(NBT_EXPIRES_AT, this.expiresAt);
            tag.putUUID(NBT_TARGET, this.target);
            tag.putBoolean(NBT_TARGET_HOME, this.targetWasHome);
            return tag;
        }

        @Nullable
        static Session load(CompoundTag tag)
        {
            if (!tag.hasUUID(NBT_TARGET))
            {
                return null;
            }

            final ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(NBT_RETURN_DIM));

            return new Session(
                    // byName falls back to survival for anything unreadable, which is the right
                    // way to be wrong: nobody is left in spectator by a corrupted save
                    GameType.byName(tag.getString(NBT_PREVIOUS_MODE), GameType.SURVIVAL),
                    ResourceKey.create(Registries.DIMENSION, dimension == null ? Level.OVERWORLD.location() : dimension),
                    new Vec3(tag.getDouble(NBT_RETURN_X), tag.getDouble(NBT_RETURN_Y), tag.getDouble(NBT_RETURN_Z)),
                    tag.getFloat(NBT_RETURN_YAW),
                    tag.getFloat(NBT_RETURN_PITCH),
                    tag.getLong(NBT_EXPIRES_AT),
                    tag.getUUID(NBT_TARGET),
                    tag.getBoolean(NBT_TARGET_HOME));
        }
    }

    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();

    /** Gazers mid-teleport, whose dimension change is not somebody coming home - see {@link #isGazeTravel}. */
    private static final Set<UUID> TRAVELLING = ConcurrentHashMap.newKeySet();

    private GazeService()
    {
    }

    public static boolean isGazing(Player player)
    {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    public static Optional<Session> sessionOf(UUID gazer)
    {
        return Optional.ofNullable(ACTIVE.get(gazer));
    }

    /** Whether this player's current dimension change is one of a gaze's own two legs. */
    public static boolean isGazeTravel(Player player)
    {
        return player != null && TRAVELLING.contains(player.getUUID());
    }

    /**
     * The game mode a player is really in: for a gazer, the one they will return to rather than the
     * spectator mode the gaze lent them. What damage transfer reads to decide whether a creative
     * player's body can hurt them (#185).
     */
    public static GameType effectiveGameType(ServerPlayer player)
    {
        final Session session = ACTIVE.get(player.getUUID());
        return session != null ? session.previousMode() : player.gameMode.getGameModeForPlayer();
    }

    /**
     * Spectator mode is invulnerable, and a gazer's body is not (rule 1 of #181: there is no cheaper
     * version of a body). So for exactly the length of one hurt call, a gazer is not - and nothing
     * else about spectator mode changes. Deliberately not done by tagging soul_severed as bypassing
     * invulnerability, which would also stop a totem of undying from saving anyone.
     */
    public static void hurtWhileGazing(ServerPlayer player, DamageSource source, float amount)
    {
        final boolean wasInvulnerable = player.getAbilities().invulnerable;

        player.getAbilities().invulnerable = false;

        try
        {
            player.hurt(source, amount);
        }
        finally
        {
            // a gazer the hit killed has already been handed their own game mode back by
            // onGazerDied, and must not be left invulnerable by a flag restored on top of it
            if (isGazing(player))
            {
                player.getAbilities().invulnerable = wasInvulnerable;
            }
        }
    }

    /**
     * The ability fired - {@code SoulgazeEffect#activate}. Returns whether a gaze began, which is
     * whether a charge is spent.
     */
    public static boolean begin(ServerPlayer gazer, double magnitude)
    {
        final GazeSettings settings = SoulHomeConfig.gazeSettings();
        final MinecraftServer server = gazer.getServer();

        if (!settings.enabled() || server == null)
        {
            tell(gazer, Constants.StringKeys.GAZE_DISABLED);
            return false;
        }

        if (DimensionHelper.isInSoulDimension(gazer))
        {
            // gazing happens from outside a soul, where a body can be left standing - a gaze cast
            // from inside one would leave a body inside a soul, which is the one place it must not be
            tell(gazer, Constants.StringKeys.GAZE_FROM_OUTSIDE);
            return false;
        }

        if (gazer.isSpectator() || VesselLifecycleService.vesselOf(gazer.getUUID()).isPresent())
        {
            return false;
        }

        final UUID target = findTarget(gazer, settings.rangeFor(magnitude)).orElse(null);

        if (target == null)
        {
            tell(gazer, Constants.StringKeys.GAZE_NO_TARGET);
            return false;
        }

        final ServerLevel soul = server.getLevel(DimensionHelper.soulDimensionKey(target));

        if (soul == null)
        {
            // a soul that has never once been opened has nothing in it to read - and making one
            // exist so a stranger can look at an empty island is not something to do on their behalf
            tell(gazer, Constants.StringKeys.GAZE_NO_SOUL);
            return false;
        }

        final ServerPlayer owner = server.getPlayerList().getPlayer(target);
        final boolean targetHome = owner != null && owner.level() == soul;
        final long now = server.overworld().getGameTime();

        final Session session = new Session(
                gazer.gameMode.getGameModeForPlayer(),
                gazer.level().dimension(),
                gazer.position(),
                gazer.getYRot(),
                gazer.getXRot(),
                now + settings.durationFor(magnitude, targetHome),
                target,
                targetHome);

        // the session exists before the body does, so that the body's own first tick finds a gaze
        // behind it rather than reading as a leftover to tidy away
        ACTIVE.put(gazer.getUUID(), session);
        save(gazer, session);

        if (!VesselLifecycleService.spawnForGaze(gazer))
        {
            ACTIVE.remove(gazer.getUUID());
            clear(gazer);
            return false;
        }

        setMode(gazer, GameType.SPECTATOR);
        travel(gazer, soul, 0.5d, DimensionHelper.FLOOR_LEVEL + 2, 0.5d, gazer.getYRot(), gazer.getXRot());

        final String ownerName = PlayerHelper.getPlayerName(target, server);
        gazer.sendSystemMessage(TextHelper.createTranslatedText(Constants.StringKeys.GAZE_BEGIN, ownerName));

        GazeNotices.onGazeBegan(gazer, target, magnitude, (int) (session.expiresAt() - now));
        SoulAdvancements.onMoment(gazer, SoulAdvancements.Moment.GAZED);
        return true;
    }

    /** Once per server tick per online player, from {@code GazeEvents}. */
    public static void tick(ServerPlayer gazer)
    {
        final Session session = ACTIVE.get(gazer.getUUID());

        if (session == null)
        {
            return;
        }

        // no possession: spectator mode would otherwise let a gazer ride any mob in the target's soul
        if (gazer.getCamera() != gazer)
        {
            gazer.setCamera(gazer);
        }

        final MinecraftServer server = gazer.getServer();

        if (server == null)
        {
            return;
        }

        final ResourceKey<Level> soulKey = DimensionHelper.soulDimensionKey(session.target());

        // anything other than this service taking a gazer out of spectator mode is an operator's
        // hand - /gamemode - and the session ends on it rather than fighting it
        if (!gazer.isSpectator())
        {
            end(gazer, EndReason.INTERRUPTED);
            return;
        }

        if (!gazer.level().dimension().equals(soulKey) && !TRAVELLING.contains(gazer.getUUID()))
        {
            end(gazer, EndReason.INTERRUPTED);
            return;
        }

        if (server.overworld().getGameTime() >= session.expiresAt())
        {
            end(gazer, EndReason.EXPIRED);
            return;
        }

        if (session.targetWasHome())
        {
            final ServerPlayer owner = server.getPlayerList().getPlayer(session.target());

            if (owner == null || !owner.level().dimension().equals(soulKey))
            {
                end(gazer, EndReason.SOUL_CLOSED);
            }
        }
    }

    /**
     * Close a gaze and put the gazer back: their own game mode (unless an operator already chose
     * another), where their body is (or where they cast from, if the body is gone), and no body
     * left standing. A gazer who died is only handed their game mode back - see {@link #onGazerDied}.
     */
    public static void end(ServerPlayer gazer, EndReason reason)
    {
        final Session session = ACTIVE.remove(gazer.getUUID());

        if (session == null)
        {
            return;
        }

        clear(gazer);

        final MinecraftServer server = gazer.getServer();

        // an operator who set a game mode by hand has decided what it should be; anything else
        // returns the one the gaze borrowed from
        if (!(reason == EndReason.INTERRUPTED && !gazer.isSpectator()))
        {
            setMode(gazer, session.previousMode());
        }

        if (reason != EndReason.DIED && server != null)
        {
            returnHome(gazer, session, server);
            VesselLifecycleService.endGaze(gazer);
        }

        if (server != null)
        {
            GazeNotices.onGazeEnded(session.target(), server);
        }

        final String key = switch (reason)
        {
            case EXPIRED -> Constants.StringKeys.GAZE_END_EXPIRED;
            case SOUL_CLOSED -> Constants.StringKeys.GAZE_END_SOUL_CLOSED;
            case DISTURBED -> Constants.StringKeys.VESSEL_DISTURBED;
            case DIED -> null;
            default -> Constants.StringKeys.GAZE_END_RECALLED;
        };

        if (key != null)
        {
            gazer.sendSystemMessage(TextHelper.createTranslatedText(key));
        }
    }

    /**
     * A gazer is dying. Spectators drop nothing on death - vanilla keeps their inventory - so a
     * gazer killed through their body would otherwise take everything with them, and #186's spill
     * would never happen. So the gaze ends here, before the drops are decided, by handing back the
     * game mode it borrowed; the body is left standing for the spill to land at, and is removed by
     * {@code VesselEvents} once it has.
     */
    public static void onGazerDied(ServerPlayer gazer)
    {
        end(gazer, EndReason.DIED);
    }

    /**
     * A session found in a player's own save on the way in: a logout, a restart or a crash
     * mid-gaze. Nothing about the old session is trusted beyond where to put them back.
     */
    public static void onLogin(ServerPlayer player)
    {
        final CompoundTag soulNBT = PlayerHelper.getPersistentTag(player, SoulHome.SOULHOME_LOC.toString());

        if (!soulNBT.contains(NBT_SESSION))
        {
            return;
        }

        final Session session = Session.load(soulNBT.getCompound(NBT_SESSION));

        if (session == null)
        {
            soulNBT.remove(NBT_SESSION);
            return;
        }

        LogHelper.info("Restoring " + player.getGameProfile().getName() + " from a Soulgaze that did not end before they left.");
        ACTIVE.put(player.getUUID(), session);
        end(player, EndReason.RESTORED);
    }

    /**
     * The gazer is leaving the server. The session stays in their save - that is the whole point of
     * it - so the next login finds it and puts them back; only the in-memory copy goes.
     */
    public static void onLogout(ServerPlayer player)
    {
        ACTIVE.remove(player.getUUID());
        TRAVELLING.remove(player.getUUID());
    }

    /** Every gazer currently looking into {@code soulOwner}'s soul - for the notices in #189. */
    public static boolean anyoneGazingInto(UUID soulOwner)
    {
        return ACTIVE.values().stream().anyMatch(session -> session.target().equals(soulOwner));
    }

    private static void returnHome(ServerPlayer gazer, Session session, MinecraftServer server)
    {
        // the body may have been shoved since it was left; come back to it, not to where it began
        final Optional<SoulVesselEntity> body = VesselLifecycleService.vesselOf(gazer.getUUID())
                .filter(SoulVesselEntity::isGaze);

        ServerLevel destination = body.map(vessel -> (ServerLevel) vessel.level())
                .orElseGet(() -> server.getLevel(session.returnDimension()));
        Vec3 at = body.map(Entity::position).orElse(session.returnPosition());

        if (destination == null)
        {
            LogHelper.warn("Soulgaze return dimension " + session.returnDimension().location()
                    + " no longer exists, falling back to overworld spawn.");
            destination = server.overworld();

            final BlockPos spawn = destination.getSharedSpawnPos();
            at = new Vec3(spawn.getX() + 0.5d, spawn.getY(), spawn.getZ() + 0.5d);
        }

        travel(gazer, destination, at.x, at.y, at.z, session.returnYaw(), session.returnPitch());
    }

    private static void travel(ServerPlayer gazer, ServerLevel destination, double x, double y, double z, float yaw, float pitch)
    {
        TRAVELLING.add(gazer.getUUID());

        try
        {
            TeleportHelper.teleportEntity(gazer, destination, x, y, z, yaw, pitch);
        }
        finally
        {
            TRAVELLING.remove(gazer.getUUID());
        }
    }

    private static void setMode(ServerPlayer player, GameType mode)
    {
        player.setGameMode(mode);
    }

    /**
     * The player or Soul Vessel in the gazer's crosshair, as the soul it would open: a vessel's
     * owner's, or the player's own. Blocks stop the ray - a gaze is cast at something you can see.
     */
    private static Optional<UUID> findTarget(ServerPlayer gazer, double range)
    {
        final Level level = gazer.level();
        final Vec3 eye = gazer.getEyePosition();
        final Vec3 reach = gazer.getViewVector(1.0f).scale(range);
        Vec3 end = eye.add(reach);

        final HitResult blocks = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, gazer));

        if (blocks.getType() != HitResult.Type.MISS)
        {
            end = blocks.getLocation();
        }

        final AABB swept = gazer.getBoundingBox().expandTowards(reach).inflate(1.0d);
        final EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, gazer, eye, end, swept, entity ->
                (entity instanceof Player player && player != gazer && !player.isSpectator())
                        || (entity instanceof SoulVesselEntity vessel
                                && vessel.getOwnerId().filter(id -> !id.equals(gazer.getUUID())).isPresent()));

        if (hit == null)
        {
            return Optional.empty();
        }

        if (hit.getEntity() instanceof SoulVesselEntity vessel)
        {
            return vessel.getOwnerId();
        }

        return Optional.of(hit.getEntity().getUUID());
    }

    private static void save(ServerPlayer gazer, Session session)
    {
        PlayerHelper.getPersistentTag(gazer, SoulHome.SOULHOME_LOC.toString()).put(NBT_SESSION, session.save());
    }

    private static void clear(ServerPlayer gazer)
    {
        PlayerHelper.getPersistentTag(gazer, SoulHome.SOULHOME_LOC.toString()).remove(NBT_SESSION);
    }

    private static void tell(ServerPlayer player, String key)
    {
        player.displayClientMessage(TextHelper.createTranslatedText(key), true);
    }
}
