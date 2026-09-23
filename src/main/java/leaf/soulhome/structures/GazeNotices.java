/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.entity.SoulVesselEntity;
import leaf.soulhome.network.GazeNoticeMessage;
import leaf.soulhome.network.Network;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.GazeSettings;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player perceives when someone looks into their soul (#189). Rule 5 of #181: a gaze always
 * leaves a trace, fainter with a better observatory, never absent.
 *
 * <h2>What was chosen</h2>
 *
 * #189 left this open with an inclination, and the inclination is what shipped:
 *
 * <ul>
 *   <li><b>A prickle, for everyone.</b> A brief, non-directional cue - a sound, a vignette at the
 *       edge of the screen, and one line that says only that something is looking. Its intensity
 *       is the gazer's {@link GazeSettings#obviousnessFor} - fainter for a stronger observatory,
 *       never below the floor - and the line itself does not fade at all, which is what makes
 *       "never silent" a property of the mechanism. It reaches a player with no rooms and no
 *       ascensions, and it reveals nothing about where the watcher is: a cue that pointed at a
 *       hidden watcher would make Soulgaze useless.</li>
 *   <li><b>Identity and direction, for an observatory's owner.</b> The telescope that lets you look
 *       also lets you notice being looked at, at full strength whatever the gazer's tier: who it
 *       is, and which way their body stands from you. This is the reward, and the reason the
 *       prickle alone carries neither.</li>
 *   <li><b>The soul itself, as flavour.</b> A target who is inside their soul when the gaze lands -
 *       the common case, since gazing at a meditating body is the strongly preferred play - keeps
 *       a faint vignette for as long as the gaze lasts, scaled the same way, and it clears when the
 *       last watcher leaves. It cannot be the whole answer, since it only reaches someone who is
 *       home, and it is not.</li>
 * </ul>
 *
 * <p>{@code observatory.gaze.notify_owner} switches all of it off, for a server that wants gazes
 * silent. A notice to one target is rate-limited by {@link GazeSettings#noticeCooldownTicks}, so a
 * watcher cannot strobe someone who has no way to answer.
 */
public final class GazeNotices
{
    private static final String OBSERVATORY = "soulhome:observatory";

    /** Target to the overworld game time of the last notice they were sent. */
    private static final Map<UUID, Long> LAST_NOTICE = new ConcurrentHashMap<>();

    private GazeNotices()
    {
    }

    static void onGazeBegan(ServerPlayer gazer, UUID targetId, double magnitude, int durationTicks)
    {
        final GazeSettings settings = SoulHomeConfig.gazeSettings();
        final MinecraftServer server = gazer.getServer();

        if (!settings.notifyOwner() || server == null)
        {
            return;
        }

        final ServerPlayer target = server.getPlayerList().getPlayer(targetId);

        if (target == null)
        {
            // nobody to tell right now. The body the gazer left standing is the trace a bystander
            // can find; a live cue to an absent target has nobody to reach
            return;
        }

        final long now = server.overworld().getGameTime();
        final Long last = LAST_NOTICE.get(targetId);
        final boolean home = target.level().dimension().equals(DimensionHelper.soulDimensionKey(targetId));
        final boolean observatory = ownsObservatory(server, targetId);
        final double obviousness = observatory ? 1d : settings.obviousnessFor(magnitude);

        if (last != null && now - last < settings.noticeCooldownTicks())
        {
            // too soon for a second prickle - but the soul still knows it is being watched
            if (home)
            {
                Network.sendTo(new GazeNoticeMessage(obviousness, durationTicks, false), target);
            }

            return;
        }

        LAST_NOTICE.put(targetId, now);

        if (observatory)
        {
            target.sendSystemMessage(TextHelper.createTranslatedText(
                    Constants.StringKeys.GAZE_NOTICE_OBSERVATORY,
                    gazer.getDisplayName(),
                    whereTheBodyIs(target, gazer)));
        }
        else
        {
            target.displayClientMessage(TextHelper.createTranslatedText(Constants.StringKeys.GAZE_NOTICE_PRICKLE), true);
        }

        Network.sendTo(new GazeNoticeMessage(obviousness, home ? durationTicks : 0, true), target);
        SoulAdvancements.onMoment(target, SoulAdvancements.Moment.GAZED_AT);
    }

    /** The last watcher left a soul: the owner's vignette, if they are home to see it, clears. */
    static void onGazeEnded(UUID targetId, MinecraftServer server)
    {
        if (GazeService.anyoneGazingInto(targetId))
        {
            return;
        }

        final ServerPlayer target = server.getPlayerList().getPlayer(targetId);

        if (target != null)
        {
            Network.sendTo(GazeNoticeMessage.CLEAR, target);
        }
    }

    /** Forget a player's rate limit. Called on logout so the map does not grow forever. */
    public static void forget(UUID player)
    {
        LAST_NOTICE.remove(player);
    }

    /**
     * Which way the gazer's body stands from the target, when the two are in the same world, or
     * that it stands in another one. The body rather than the gazer: the gazer is standing inside
     * the target's soul as a spectator, which is not a direction anyone can walk.
     */
    private static Component whereTheBodyIs(ServerPlayer target, ServerPlayer gazer)
    {
        final SoulVesselEntity body = VesselLifecycleService.vesselOf(gazer.getUUID()).orElse(null);

        if (body == null || body.level() != target.level())
        {
            return TextHelper.createTranslatedText(Constants.StringKeys.GAZE_NOTICE_ELSEWHERE);
        }

        final Vec3 offset = body.position().subtract(target.position());
        final int distance = (int) Math.round(Math.sqrt(offset.x * offset.x + offset.z * offset.z));

        // atan2 of east against south gives Minecraft's own compass: 0 is south, a quarter turn west
        final double degrees = Math.toDegrees(Math.atan2(-offset.x, offset.z));
        final int octant = Math.floorMod((int) Math.round(degrees / 45d), 8);

        return TextHelper.createTranslatedText(
                Constants.StringKeys.GAZE_NOTICE_DIRECTION,
                distance,
                TextHelper.createTranslatedText(Constants.StringKeys.COMPASS_PREFIX + COMPASS[octant]));
    }

    private static final String[] COMPASS = {
            "south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

    /** Whether this player has a classified observatory of their own - read off their own soul, not their buffs. */
    private static boolean ownsObservatory(MinecraftServer server, UUID player)
    {
        final ServerLevel soul = server.getLevel(DimensionHelper.soulDimensionKey(player));

        if (soul == null)
        {
            return false;
        }

        for (AwardedRoom room : SoulHomeBuffData.get(soul).awardedRooms())
        {
            if (OBSERVATORY.equals(room.archetypeId()))
            {
                return true;
            }
        }

        return false;
    }
}
