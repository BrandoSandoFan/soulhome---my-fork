/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.entity;

import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * {@code soulhome:soul_severed} (#185): the damage a player takes when their body is struck while
 * they are away. Declared as datapack JSON under {@code data/soulhome/damage_type/}, since 1.20.1
 * moved damage types out of code; this is only the key to look it up by.
 *
 * <p>It is the one damage type that gets through a soul dimension's blanket cancel in
 * {@code CommonEvents#onLivingHurt}, and so the only way anyone can die inside a soul. It carries
 * the original attacker, which is what makes the death message name a PvP killer, and what makes
 * the owner's own armour, enchantments, absorption and resistance apply without a line of code:
 * the entity actually being hurt is the player.
 *
 * <p>It is tagged {@code minecraft:no_knockback} - the attacker is standing in another dimension,
 * and a shove from a direction that does not exist would fling a meditating player across their
 * own soul - and {@code minecraft:bypasses_shield}, because a shield raised in a soul guards the
 * soul, not a body sitting somewhere else.
 */
public final class SoulSevered
{
    public static final ResourceKey<DamageType> KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocationHelper.prefix("soul_severed"));

    private SoulSevered()
    {
    }

    /**
     * A soul-severed source standing in for {@code original}: the same direct entity (the arrow)
     * and the same causing entity (whoever loosed it), so a kill is credited to them.
     */
    public static DamageSource from(ServerLevel level, DamageSource original)
    {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KEY),
                original.getDirectEntity(),
                original.getEntity());
    }

    public static boolean is(DamageSource source)
    {
        return source.is(KEY);
    }
}
