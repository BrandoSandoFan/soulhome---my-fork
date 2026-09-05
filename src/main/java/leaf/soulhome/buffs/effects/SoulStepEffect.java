/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.SnapshotBlockVolume;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Rift chamber: a short blink in the direction you are looking, through blocks (#90).
 *
 * <p>The ability that most justifies the whole framework - a longer blink is better, more blinks
 * are better, and neither becomes unusable the way movement speed does (#86). It is also the one
 * that can break a server, so the constraints matter more than the effect:
 *
 * <ul>
 *   <li><b>Follows the crosshair, not the feet.</b> The ray is cast from the player's eye along
 *       their exact look vector, then walked back down to a foot position - anything cast from
 *       {@code player.position()} instead drifts off the sightline the moment pitch is not
 *       exactly zero, which is most of the time, and the landing spot stops matching where the
 *       player was actually looking.</li>
 *   <li><b>Never across dimensions.</b> This is a within-level move and stays one. Everything that
 *       crosses a soulhome boundary goes through {@code TeleportHelper} so the exit position and
 *       the rescan happen; a blink deliberately cannot become a second way out that skips both.
 *       Nothing here touches the level, so there is nothing for the travel guard to catch.</li>
 *   <li><b>Respects the verge.</b> Inside a soulhome a blink cannot land outside the box (#79),
 *       which would otherwise make this a way to build outside it by way of a shulker box.</li>
 *   <li><b>No blink into unloaded chunks</b>, and none into solid rock: the ray stops at the
 *       furthest safe point it passed rather than failing silently or burying the player.</li>
 *   <li><b>Claims are a config switch, not a pretence.</b> There is no general protection API to
 *       consult on 1.20.1, so a server owner who needs this off gets an honest
 *       {@code abilities.disabled} entry rather than an integration that does not exist.</li>
 * </ul>
 */
public class SoulStepEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.SOUL_STEP;

    /** 6 blocks at tier 1, per #90. */
    private static final double BASE_DISTANCE = 4d;
    private static final double DISTANCE_PER_MAGNITUDE = 1d;

    /** 45 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 900;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 60;

    /** How finely the ray is walked. Half a block, so a one-block gap is never stepped over. */
    private static final double STEP = 0.5d;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Soul Step blinks, and how many blinks are banked";
    }

    @Override
    public int chargesFor(double magnitude)
    {
        // two at tier 1, a third at the top - #90 puts the growth in distance first and charges
        // second, since a longer blink is the thing a player actually feels
        return 1 + (int) Math.round(magnitude / 2d);
    }

    @Override
    public int rechargeTicksFor(double magnitude)
    {
        return BASE_RECHARGE_TICKS - (int) Math.round(magnitude * RECHARGE_SAVED_PER_MAGNITUDE);
    }

    @Override
    public boolean activate(ServerPlayer player, double magnitude)
    {
        final ServerLevel level = player.serverLevel();
        final double distance = BASE_DISTANCE + magnitude * DISTANCE_PER_MAGNITUDE;

        // the ray has to start at the eye, not the feet (#90 follow-up) - a player aiming
        // anywhere but dead level otherwise blinks along a line several degrees off their own
        // crosshair, which is what made the landing spot feel arbitrary rather than "where I
        // looked". The eye-height offset is carried through the walk and removed again below so
        // the player's own eye, not their feet, ends up on the sightline they aimed along.
        final double eyeHeight = player.getEyeHeight();
        final Vec3 origin = player.getEyePosition();
        final Vec3 look = player.getLookAngle().normalize();

        final RegionBounds box = boxFor(level);
        Vec3 landing = null;

        // walk out along the look ray keeping the last safe spot seen. Stopping at the first
        // obstruction would make this a dash rather than a blink; keeping the furthest safe point
        // is what lets it pass through a wall and still refuse to end inside one.
        for (double travelled = STEP; travelled <= distance; travelled += STEP)
        {
            final Vec3 candidate = origin.add(look.scale(travelled)).subtract(0, eyeHeight, 0);

            if (box != null && !box.contains(
                    (int) Math.floor(candidate.x), (int) Math.floor(candidate.y), (int) Math.floor(candidate.z)))
            {
                // the verge. Everything past this point on the ray is outside the box too, so
                // there is nothing further to consider
                break;
            }

            if (!isLoaded(level, candidate))
            {
                break;
            }

            if (canStandAt(level, candidate))
            {
                landing = candidate;
            }
        }

        if (landing == null)
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_SOUL_STEP_NO_ROOM), true);
            return false;
        }

        // both ends get a sound, so someone watching sees where the blink went as well as that it
        // happened - the departure has to be played before the move, while the player is still there
        final BlockPos from = player.blockPosition();
        level.playSound(null, from, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.4f);

        // teleportTo on the player's own level, never the overload that takes one - this must not
        // be able to become a dimension change
        player.teleportTo(landing.x, landing.y, landing.z);
        player.resetFallDistance();

        level.playSound(
                null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.4f);

        return true;
    }

    /** The box a blink may not leave, or {@code null} outside a bounded soulhome. */
    private RegionBounds boxFor(ServerLevel level)
    {
        if (!SoulHomeConfig.enforceBounds() || DimensionHelper.soulOwner(level).isEmpty())
        {
            return null;
        }

        return SnapshotBlockVolume.declaredBox(level);
    }

    private boolean isLoaded(ServerLevel level, Vec3 position)
    {
        return level.isLoaded(BlockPos.containing(position));
    }

    /**
     * Whether a player fits here: two blocks of clear space, and something under them. The floor
     * check is what stops a blink ending in mid-air over a ravine - which would be a blink that
     * kills you, and a fall a player never chose is a worse outcome than the ability refusing.
     */
    private boolean canStandAt(ServerLevel level, Vec3 position)
    {
        final BlockPos feet = BlockPos.containing(position);
        final BlockPos head = feet.above();

        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty())
        {
            return false;
        }

        if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty())
        {
            return false;
        }

        final BlockPos floor = feet.below();
        return !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
    }
}
