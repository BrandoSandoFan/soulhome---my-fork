/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.AbilityDamage;
import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * Storm spire: lightning called down on what is trying to kill you (#94).
 *
 * <p>Scaling is <b>bolt count and radius, never per-bolt damage past a cap</b>. A rank V player
 * calls down a small storm over a battlefield; they do not get one bolt that deletes whatever it
 * touches. Same reasoning as #86 - a number that stops being fun above a threshold should stop
 * growing at that threshold and grow sideways instead.
 *
 * <p><b>The bolt is visual-only, and the damage is dealt separately.</b> A real
 * {@link LightningBolt} sets fires, and converts what it hits: a pig becomes a zombified piglin, a
 * villager becomes a witch, a creeper becomes charged. That is a griefing and
 * mob-transformation surface nobody asked for, and it would arrive attached to a room whose only
 * promise was damage. {@code setVisualOnly} keeps the flash and the thunder and does none of it.
 */
public class ThunderclapEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.THUNDERCLAP;

    /** 8 blocks at tier 1, per #94. */
    private static final double BASE_RADIUS = 6d;
    private static final double RADIUS_PER_MAGNITUDE = 2d;

    /** 40 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 800;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 80;

    /**
     * Damage per bolt, flat and deliberately not scaled by magnitude. This is the cap #94 asks for,
     * expressed as the simplest thing that cannot be got round: there is nothing to grow.
     *
     * <p>Dealt as lightning rather than as magic, and past the target's invulnerability window -
     * see {@link AbilityDamage} for what each of those was costing.
     */
    private static final float DAMAGE_PER_BOLT = 6.0f;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how many bolts Thunderclap calls, and how far they reach";
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
        final double radius = BASE_RADIUS + magnitude * RADIUS_PER_MAGNITUDE;
        final int bolts = Math.max(1, (int) Math.round(magnitude));

        final AABB box = player.getBoundingBox().inflate(radius);

        List<LivingEntity> hostiles = level.getEntitiesOfClass(
                LivingEntity.class, box, target -> target instanceof Enemy && target.isAlive());

        if (hostiles.isEmpty())
        {
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_THUNDERCLAP_NOTHING), true);
            return false;
        }

        // nearest first, so a player who presses this with one thing on top of them and three
        // across the room gets the one on top of them struck rather than a random selection
        hostiles.sort(Comparator.comparingDouble(target -> target.distanceToSqr(player)));

        final int struck = Math.min(bolts, hostiles.size());
        final DamageSource lightning = AbilityDamage.sourceOf(level, DamageTypes.LIGHTNING_BOLT, player);

        int hurt = 0;

        for (int i = 0; i < struck; i++)
        {
            if (strike(level, player, hostiles.get(i), lightning))
            {
                hurt++;
            }
        }

        if (hurt == 0)
        {
            // the bolts fell and nothing took damage from them - a mob immune to lightning, or
            // another mod refusing the hit. Either way the player watched an ability do nothing,
            // and charging them for it on top would be the mod taking payment for nothing
            player.displayClientMessage(
                    Component.translatable(Constants.StringKeys.ABILITY_NO_DAMAGE), true);
            return false;
        }

        return true;
    }

    /** One bolt, and whether what it landed on actually took damage. */
    private boolean strike(ServerLevel level, ServerPlayer caster, LivingEntity target, DamageSource lightning)
    {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);

        if (bolt != null)
        {
            bolt.moveTo(target.position());

            // the whole reason this room is not a griefing tool: no fire, no conversions, no
            // charged creepers - the flash and the thunder, and nothing else
            bolt.setVisualOnly(true);
            bolt.setCause(caster);

            level.addFreshEntity(bolt);
        }
        else
        {
            // a level that will not make a bolt is not a reason to skip the damage
            level.playSound(
                    null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 1.0f);
        }

        return AbilityDamage.hit(target, lightning, DAMAGE_PER_BOLT);
    }
}
