/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.entity.SoulBarrageShotEntity;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Powder magazine: a spread of real, flying shells down the line you are looking (#95, #194).
 *
 * <p>Scaling is <b>shot count and cooldown</b> - the latter floored, per #85's rule that rank must
 * never multiply a cooldown toward zero. Never per-shot damage past a cap.
 *
 * <p><b>Each shot is a real projectile, {@link SoulBarrageShotEntity}, that explodes on impact
 * without touching a single block.</b> An earlier version of this class argued that neither a real
 * explosion nor a real projectile could be made safe on 1.20.1; both objections turn out to be
 * answerable rather than fundamental:
 *
 * <ul>
 *   <li>{@code Level.explode(Entity, double, double, double, float, Level.ExplosionInteraction.NONE)}
 *       resolves to {@code Explosion.BlockInteraction.KEEP}, which leaves nothing to blow up and
 *       primes no TNT - #90's rule that nothing here can grief a world holds regardless.</li>
 *   <li>Setting a target or a block alight on hit is a property of vanilla's own fireball
 *       subclasses, not of projectiles in general - a mod-owned entity decides its own
 *       {@code onHit} and simply never sets anything on fire (see
 *       {@link SoulBarrageShotEntity#shouldBurn}).</li>
 * </ul>
 *
 * <p>The shell still deals its splash damage the old hitscan burst's own way - a flat, capped
 * amount over a fixed radius, deduplicated across an activation - rather than through a vanilla
 * explosion's own falloff-and-armour damage model, which is not "capped and flat" in the sense
 * #95 asks for. See {@link SoulBarrageShotEntity} for the rest.
 */
public class BarrageEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.BARRAGE;

    /** 35 seconds at tier 1, per #95. */
    private static final int BASE_RECHARGE_TICKS = 700;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 50;

    /** How far the spread opens either side of the aim, in blocks at the shell's own range. */
    private static final double SPREAD = 2.5d;
    private static final double SPREAD_RANGE = 12d;

    /**
     * Shots fan out in rows at most this wide, stacking a new row above or below rather than
     * widening a single line without bound - a wide magnitude reads as a volley with some depth to
     * it, not a single implausibly broad wall of shells.
     */
    private static final int ROW_WIDTH = 3;

    /** Clear of the caster's own bounding box, so a shell never detonates on its own shooter. */
    private static final double SPAWN_OFFSET = 0.5d;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how many shots Barrage lobs, and how soon it reloads";
    }

    @Override
    public int chargesFor(double magnitude)
    {
        return 1;
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
        final int shots = Math.max(1, (int) Math.round(magnitude) + 1);

        final Vec3 origin = player.getEyePosition();
        final Vec3 look = player.getLookAngle().normalize();

        // a stable frame to fan the shots across, taken from the look direction rather than from
        // the player's yaw, so aiming straight up still spreads sideways instead of collapsing
        final Vec3 sideways = look.cross(new Vec3(0d, 1d, 0d));
        final Vec3 spreadAxis = sideways.lengthSqr() < 1.0e-4d
                ? new Vec3(1d, 0d, 0d)
                : sideways.normalize();

        // the true "up" for this look direction (not world up), so a row stacked above another
        // still reads as above when the player is aiming steeply up or down
        final Vec3 rise = spreadAxis.cross(look);
        final Vec3 verticalAxis = rise.lengthSqr() < 1.0e-4d ? new Vec3(0d, 1d, 0d) : rise.normalize();

        // one damage roll per victim per activation, however many shells land near them - shared
        // by every shell this press fires, since they now arrive over several ticks rather than
        // all resolving inside this one method call
        final Set<LivingEntity> activationHits = new HashSet<>();
        final Vec3 spawnPoint = origin.add(look.scale(SPAWN_OFFSET));

        // rows of at most ROW_WIDTH shots each, stacked above and below the aim rather than one
        // row widened without bound - see the field javadoc
        final int rows = (shots + ROW_WIDTH - 1) / ROW_WIDTH;

        for (int shot = 0; shot < shots; shot++)
        {
            final int row = shot / ROW_WIDTH;
            final int rowStart = row * ROW_WIDTH;
            final int rowSize = Math.min(ROW_WIDTH, shots - rowStart);
            final int col = shot - rowStart;

            final double horizontalOffset = rowSize == 1 ? 0d : ((double) col / (rowSize - 1) - 0.5d) * 2d;
            final double verticalOffset = rows == 1 ? 0d : ((double) row / (rows - 1) - 0.5d) * 2d;

            final Vec3 aim = look
                    .add(spreadAxis.scale(horizontalOffset * SPREAD / SPREAD_RANGE))
                    .add(verticalAxis.scale(verticalOffset * SPREAD / SPREAD_RANGE))
                    .normalize();

            level.addFreshEntity(new SoulBarrageShotEntity(level, player, spawnPoint, aim, activationHits));
        }

        return true;
    }
}
