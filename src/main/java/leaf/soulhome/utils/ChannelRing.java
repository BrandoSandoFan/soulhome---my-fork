/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.utils;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The particle ring a channel draws while it holds (#183/#184) - lifted out of
 * {@code SoulKeyItem#onUseTick} so meditation at a cushion can draw the same ring without a second
 * copy of the maths. Client-side only; a caller on the server passes nothing worth drawing to, so
 * this checks {@code isClientSide} itself rather than trusting every caller to.
 */
public final class ChannelRing
{
    private static final float MAX_RADIUS = 5f;

    private ChannelRing()
    {
    }

    /**
     * @param ticksRemaining ticks left before the channel completes - counting down, the way
     *                       {@code Item#onUseTick}'s own {@code count} does
     * @param ticksRequired  the channel's whole length; a channel that cannot complete has nothing
     *                       to ring, so this is a no-op if it is not positive
     */
    public static void spawn(LivingEntity entity, int ticksRemaining, int ticksRequired, ParticleOptions particle)
    {
        if (!entity.level().isClientSide || ticksRequired <= 0)
        {
            return;
        }

        final float percentage = MathUtils.clamp01((ticksRequired - ticksRemaining) / (float) ticksRequired);
        final int particlesToCreate = Mth.floor((percentage * percentage * percentage) * ticksRequired);

        //on the very first tick percentage is 0, so there is nothing to draw yet. Without this,
        //360f / 0 is Infinity and the angle below becomes NaN.
        if (particlesToCreate <= 0)
        {
            return;
        }

        final float bits = 360f / particlesToCreate;
        final float radius = percentage * MAX_RADIUS;

        //the angle is stepped in degrees but sin and cos want radians, so it is converted rather
        //than wrapped - wrapping to [-180, 180) and reading that as radians still puts every
        //particle on the ring, but clumps a turn's worth of steps over fifty-seven of them instead
        //of spreading them evenly
        for (int i = 0; i < particlesToCreate; i++)
        {
            final float ang = (bits * i) * Mth.DEG_TO_RAD;

            entity.level().addParticle(
                    particle,
                    entity.getX() + Mth.sin(ang) * radius,
                    entity.getY(),
                    entity.getZ() + Mth.cos(ang) * radius,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }
}
