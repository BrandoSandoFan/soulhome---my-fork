/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Powder magazine: a spread of bursts down the line you are looking (#95).
 *
 * <p>Scaling is <b>shot count and cooldown</b> - the latter floored, per #85's rule that rank must
 * never multiply a cooldown toward zero. Never per-shot damage past a cap.
 *
 * <p><b>Nothing here is a real explosion, and nothing here is a real projectile.</b> Both were
 * considered and both are worse than they look:
 *
 * <ul>
 *   <li>An {@code Explosion} craters terrain even with a small radius, and would make this the
 *       first thing in SoulHome capable of wrecking a world. #90 went to some trouble to make sure
 *       a blink could not land a player somewhere they could grief from; a room that comes with a
 *       world-editing key would undo that on its own.</li>
 *   <li>A vanilla fireball entity sets its target alight on hit and its block on fire on miss, so
 *       "no block damage" would have to be enforced by cancelling the thing the entity exists to
 *       do.</li>
 * </ul>
 *
 * <p>So each shot is a point along the look ray that deals splash damage to what is near it and
 * draws an explosion particle. It reads as a barrage, it breaks nothing, it primes no TNT, and it
 * consumes no items - the TNT in the room is scenery and stays scenery, because a room that eats
 * your building materials when you press a key is a room people learn to stop building.
 */
public class BarrageEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.BARRAGE;

    /** 35 seconds at tier 1, per #95. */
    private static final int BASE_RECHARGE_TICKS = 700;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 50;

    /** How far down the look ray the shots land. */
    private static final double RANGE = 12d;

    /** How far the spread opens either side of the aim, in blocks at full range. */
    private static final double SPREAD = 2.5d;

    /** Splash radius of one shot. */
    private static final double SPLASH = 2.5d;

    /** Capped and flat, so nothing about rank makes a single shot hit harder. */
    private static final float DAMAGE_PER_SHOT = 4.0f;

    private static final double STEP = 0.5d;

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

        // one damage roll per victim per activation, however many shots land near them - the
        // alternative multiplies the "capped" per-shot damage by the shot count for anything
        // standing in the middle of the fan, which is exactly the growth #95 rules out
        Set<LivingEntity> hit = new HashSet<>();

        for (int shot = 0; shot < shots; shot++)
        {
            final double offset = shots == 1 ? 0d : ((double) shot / (shots - 1) - 0.5d) * 2d;
            final Vec3 aim = look.add(spreadAxis.scale(offset * SPREAD / RANGE)).normalize();

            burst(level, player, origin, aim, hit);
        }

        level.playSound(
                null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.5f);

        return true;
    }

    /** Walks one shot out until it meets something solid, then splashes where it stopped. */
    private void burst(ServerLevel level, ServerPlayer caster, Vec3 origin, Vec3 aim, Set<LivingEntity> hit)
    {
        Vec3 impact = origin.add(aim.scale(RANGE));

        for (double travelled = STEP; travelled <= RANGE; travelled += STEP)
        {
            final Vec3 point = origin.add(aim.scale(travelled));
            final net.minecraft.core.BlockPos position = net.minecraft.core.BlockPos.containing(point);

            if (!level.isLoaded(position))
            {
                impact = point;
                break;
            }

            if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty())
            {
                impact = point;
                break;
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0d, 0d, 0d, 0d);

        final AABB splash = new AABB(impact, impact).inflate(SPLASH);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, splash))
        {
            if (target == caster || !target.isAlive() || !hit.add(target))
            {
                continue;
            }

            target.hurt(level.damageSources().explosion(caster, caster), DAMAGE_PER_SHOT);
        }
    }
}
