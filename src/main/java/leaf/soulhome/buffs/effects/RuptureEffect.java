/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.AbilityDamage;
import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Infected grotto: an expanding shockwave in a cone (#96).
 *
 * <p>Scaling is <b>cone width and knockback, never raw damage</b>. What a rank V grotto buys is
 * catching more of a crowd and throwing them further, not hitting any one of them harder.
 *
 * <p><b>The muffle is cosmetic.</b> #96 asks for a brief muffled-audio effect on those it hits, and
 * this plays a sound rather than applying a status effect, deliberately: every vanilla effect that
 * would actually muffle or blind a player - Darkness, Blindness, Nausea - is a real debuff, and
 * applying real debuffs to other players is a much bigger PvP conversation than one room should
 * open on its own.
 */
public class RuptureEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.RUPTURE;

    /** 50 seconds at tier 1, per #96. */
    private static final int BASE_RECHARGE_TICKS = 1000;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 70;

    private static final double BASE_RANGE = 6d;
    private static final double RANGE_PER_MAGNITUDE = 1d;

    /** Half-angle of the cone. Narrow at tier 1, per #96, opening as the grotto deepens. */
    private static final double BASE_HALF_ANGLE_DEGREES = 22d;
    private static final double HALF_ANGLE_PER_MAGNITUDE = 6d;

    private static final double BASE_KNOCKBACK = 0.5d;
    private static final double KNOCKBACK_PER_MAGNITUDE = 0.15d;

    /** Flat and capped: there is no version of this that hits harder with rank. */
    private static final float DAMAGE = 5.0f;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how wide Rupture's cone opens, and how hard it throws";
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

        final double range = BASE_RANGE + magnitude * RANGE_PER_MAGNITUDE;
        final double knockback = BASE_KNOCKBACK + magnitude * KNOCKBACK_PER_MAGNITUDE;
        final double halfAngle = Math.toRadians(
                Math.min(90d, BASE_HALF_ANGLE_DEGREES + magnitude * HALF_ANGLE_PER_MAGNITUDE));

        final Vec3 origin = player.getEyePosition();
        final Vec3 look = player.getLookAngle().normalize();
        final double cosHalfAngle = Math.cos(halfAngle);

        // a sculk shockwave is the warden's own damage, which is both what this looks like and a
        // type no spell mod has a reason to intercept - see AbilityDamage. It carries the caster,
        // so what it kills is credited to them and what survives turns on them rather than on
        // nobody, which plain magic() with no attacker did not
        final DamageSource shockwave = AbilityDamage.sourceOf(level, DamageTypes.SONIC_BOOM, player);

        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(range)))
        {
            if (target == player || !target.isAlive())
            {
                continue;
            }

            final Vec3 toTarget = target.getBoundingBox().getCenter().subtract(origin);

            if (toTarget.lengthSqr() < 1.0e-6d)
            {
                continue;
            }

            // the dot product against the normalised offset is the cosine of the angle off aim;
            // comparing cosines rather than angles keeps an acos out of a per-entity loop
            if (toTarget.normalize().dot(look) < cosHalfAngle)
            {
                continue;
            }

            AbilityDamage.hit(target, shockwave, DAMAGE);

            // knockback is applied along the shockwave's own direction rather than away from the
            // player, so everything in the cone is thrown the same way - a wave, not a shove
            final Vec3 push = look.scale(knockback).add(0d, knockback * 0.35d, 0d);
            target.push(push.x, push.y, push.z);
            target.hurtMarked = true;

            level.playSound(
                    null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.5f, 0.7f);
        }

        drawWave(level, origin, look, range);

        level.playSound(
                null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.8f, 0.8f);

        // fires whether or not it caught anything - unlike a summon or a blink, an empty Rupture is
        // a miss rather than an ability that could not run, and a miss costs a charge
        return true;
    }

    private void drawWave(ServerLevel level, Vec3 origin, Vec3 look, double range)
    {
        for (double travelled = 1d; travelled <= range; travelled += 1d)
        {
            final Vec3 point = origin.add(look.scale(travelled));

            level.sendParticles(
                    ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0d, 0d, 0d, 0d);
        }
    }
}
