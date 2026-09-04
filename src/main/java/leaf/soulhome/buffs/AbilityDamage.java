/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.buffs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * How an active ability hurts something. Two rules, both learned from an ability that landed and
 * did nothing.
 *
 * <p><b>The damage type is never magic.</b> Thunderclap dealt {@code indirect_magic} and Rupture
 * plain {@code magic}, and in a pack with a spell mod in it - which is most packs that would want
 * these rooms - vanilla magic damage is exactly the thing another mod has reason to intercept. The
 * bolt fell, the mob flinched, and its health did not move. A lightning ability dealing lightning
 * damage is both harder to swallow by accident and the more honest description of what happened.
 *
 * <p><b>The damage cooldown is cleared first.</b> A player presses an offensive ability in the
 * middle of a fight, which is to say within half a second of their own sword landing - and
 * vanilla's invulnerability window discards any hit in that window that is no larger than the one
 * before it. Six damage after an eight damage swing is silently nothing. That window exists to
 * stop a mob being hit sixty times a second by the same source, which is not what a keypress on a
 * forty-second recharge is; skipping it is right here and wrong almost everywhere else, so it
 * lives in this class and not in a general helper.
 */
public final class AbilityDamage
{
    private AbilityDamage()
    {
    }

    /**
     * A damage source of this type, caused by and delivered by the caster - so a kill counts as
     * theirs, the mob turns on them rather than on nothing, and the death message names them.
     */
    public static DamageSource sourceOf(ServerLevel level, ResourceKey<DamageType> type, Entity caster)
    {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                caster);
    }

    /**
     * Hurt something with an ability, past its invulnerability window.
     *
     * @return whether the target actually took damage. An ability that was told "no" should say so
     *         and refund its charge rather than spending one on nothing.
     */
    public static boolean hit(LivingEntity target, DamageSource source, float amount)
    {
        target.invulnerableTime = 0;

        return target.hurt(source, amount);
    }
}
