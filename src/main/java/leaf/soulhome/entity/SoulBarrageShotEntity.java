/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.entity;

import leaf.soulhome.buffs.AbilityDamage;
import leaf.soulhome.registry.EntityRegistry;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Barrage's own shell (#194): a real, visible projectile in place of the instant-hitscan cone the
 * ability used to fire.
 *
 * <p>Deliberately not built on a vanilla {@code Explosion} for its impact, even though
 * {@code Level.explode(..., Level.ExplosionInteraction.NONE)} can be made to break no blocks and
 * light no fires - see the rewritten class javadoc on {@link leaf.soulhome.buffs.effects.BarrageEffect}
 * for why that reasoning changed. What this class does instead is simpler and safer to reason
 * about: on impact it never touches a single block, and it deals the ability's own flat, capped
 * damage over a splash radius exactly the way the old hitscan burst did - a vanilla explosion's own
 * damage model scales with distance and armour penetration in ways #95's "capped and flat" rule was
 * written to rule out.
 *
 * <p>{@link #activationHits} is shared by every shell one {@code activate()} call spawns - a plain
 * object reference, not persisted - so a target standing where two shells land still takes one
 * damage roll, the same guarantee the old code's method-local {@code Set} gave when everything
 * happened in a single tick.
 */
public class SoulBarrageShotEntity extends AbstractHurtingProjectile
{
    /** Splash radius of one shell landing - unchanged from the old hitscan burst. */
    private static final double SPLASH_RADIUS = 2.5d;

    /** Capped and flat: rank and magnitude scale shot count and cooldown only, never this. */
    private static final float DAMAGE_PER_SHOT = 4.0f;

    /** Blocks per tick. Fast enough to read as a shot, slow enough to be dodged. */
    public static final double SPEED = 1.6d;

    /** A shell that hits nothing dies at its own range rather than flying to the horizon. */
    private static final double MAX_RANGE = 12d;

    private Vec3 spawnPosition;

    /** Shared by every shell one activation fired - see the class javadoc. */
    private Set<LivingEntity> activationHits;

    public SoulBarrageShotEntity(EntityType<? extends SoulBarrageShotEntity> type, Level level)
    {
        super(type, level);
    }

    public SoulBarrageShotEntity(
            Level level, LivingEntity caster, Vec3 spawnPos, Vec3 direction, Set<LivingEntity> activationHits)
    {
        super(EntityRegistry.SOUL_BARRAGE_SHOT.get(), level);

        this.activationHits = activationHits;
        this.setOwner(caster);
        this.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        this.spawnPosition = spawnPos;

        final Vec3 velocity = direction.normalize().scale(SPEED);
        this.setDeltaMovement(velocity);
        this.lookAt(EntityAnchorArgument.Anchor.EYES, spawnPos.add(velocity));

        // a straight shot, not a fireball's slow curve toward a target - the shell's whole flight
        // is the direction it was fanned along at the moment it was fired
        this.xPower = 0d;
        this.yPower = 0d;
        this.zPower = 0d;
    }

    @Override
    public void tick()
    {
        super.tick();

        // server-only: a client's copy has no spawnPosition (it is never put on the wire) and no
        // business discarding an entity the server has not told it to - it just follows along
        if (!this.level().isClientSide && this.spawnPosition != null && !this.isRemoved()
                && this.position().distanceToSqr(this.spawnPosition) > MAX_RANGE * MAX_RANGE)
        {
            this.discard();
        }
    }

    @Override
    protected boolean shouldBurn()
    {
        // a soul's barrage, not a ghast's - nothing about this shell is fire, on the way or on hit
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle()
    {
        return ParticleTypes.SOUL_FIRE_FLAME;
    }

    @Override
    protected void onHit(HitResult result)
    {
        if (this.level().isClientSide || this.isRemoved())
        {
            return;
        }

        detonate();
        this.discard();
    }

    /**
     * Particles, sound, and the ability's own flat splash damage - never a block, never fire, and
     * never the caster. See the class javadoc for why this is not a vanilla {@code Explosion}.
     */
    private void detonate()
    {
        final ServerLevel level = (ServerLevel) this.level();
        final Vec3 impact = this.position();

        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0d, 0d, 0d, 0d);
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.5f);

        final Entity owner = this.getOwner();

        if (!(owner instanceof LivingEntity caster))
        {
            return;
        }

        final AABB splash = new AABB(impact, impact).inflate(SPLASH_RADIUS);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, splash))
        {
            if (target == caster || !target.isAlive() || this.activationHits == null
                    || !this.activationHits.add(target))
            {
                continue;
            }

            AbilityDamage.hit(target, level.damageSources().explosion(caster, caster), DAMAGE_PER_SHOT);
        }
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    /** Never a real vanilla explosion (#194): this shell can never be primed as one. */
    @Override
    public boolean isAttackable()
    {
        return false;
    }
}
