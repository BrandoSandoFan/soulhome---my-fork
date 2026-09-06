/*
 * File created ~ 6 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Gale Roost: a burst of updraft under the caster, and a stretch of slow falling to ride it out -
 * the same bubble-column push a stack of soul sand under water already gives an entity, handed to
 * a player standing nowhere near one.
 *
 * <p>Scaling is <b>launch height and how long the slow fall after it lasts</b>, never a knockback
 * or a damage figure - there is nothing offensive about this room, and the only failure mode worth
 * guarding against is a player launched into a ceiling and then falling the rest of the way at
 * full speed, which the slow fall exists to prevent.
 *
 * <p><b>Always fires.</b> Unlike an ability that can miss a target or find nothing to act on,
 * standing somewhere is never a reason to refuse a launch - see {@link RuptureEffect} for the
 * other active with the same always-succeeds shape.
 *
 * <p>Velocity is set the same way {@code DoubleJumpEffect} grants its extra jump: a direct
 * {@code setDeltaMovement} plus {@code hasImpulse}, which is what actually gets a server-set
 * velocity change synced to the client rather than overwritten by its next movement packet.
 */
public class UpdraftEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.UPDRAFT;

    /** A bit stronger than vanilla's own bubble column push, so the room feels worth building. */
    private static final double BASE_VELOCITY = 1.2d;
    private static final double VELOCITY_PER_MAGNITUDE = 0.2d;

    /** 5 seconds of slow falling at tier 1. */
    private static final int BASE_SLOW_FALL_TICKS = 100;
    private static final int SLOW_FALL_PER_MAGNITUDE = 20;

    /** 30 seconds at tier 1 - a mobility active, not a combat one, so the cooldown stays short. */
    private static final int BASE_RECHARGE_TICKS = 600;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 60;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how high Gale Roost throws you, and how long the slow fall after it lasts";
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
        final double velocity = BASE_VELOCITY + magnitude * VELOCITY_PER_MAGNITUDE;
        final int slowFallTicks = BASE_SLOW_FALL_TICKS + (int) Math.round(magnitude * SLOW_FALL_PER_MAGNITUDE);

        final Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, velocity, motion.z);
        player.hasImpulse = true;
        player.resetFallDistance();

        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, slowFallTicks, 0, false, true, true));

        player.serverLevel().playSound(
                null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS,
                1.0f, 1.0f);

        return true;
    }
}
