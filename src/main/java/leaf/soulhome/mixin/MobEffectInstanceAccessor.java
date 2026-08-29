/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the {@code duration} field directly - #53's harmful-effect reduction.
 *
 * <p>{@code MobEffectInstance#update} refuses to shorten a running effect: a shorter incoming
 * instance loses to whatever is already there, which is exactly right for two potions competing,
 * but leaves no public way to shrink a duration once vanilla has already applied it. Removing the
 * effect and re-adding a shorter one would work, but fires {@code MobEffectEvent.Remove} and a
 * second {@code MobEffectEvent.Added} for a change that is not really a removal - side effects
 * other mods may react to for no reason, and the whole thing this mod tries to avoid. Writing the
 * field directly fires neither.
 *
 * <p>A mixin accessor rather than reflection, for the same reason {@link LivingEntityAccessor} is
 * one: the field's name at runtime is not the name in this source file, and the mixin annotation
 * processor is what remaps it correctly in both a development workspace and an installed jar - a
 * reflective lookup written against the readable name would resolve in one and silently never
 * resolve in the other. Registered on both sides in {@code soulhome.mixins.json} - unlike
 * {@code LivingEntityAccessor}'s {@code jumping} flag, this one is read on the server, which is
 * where potions are actually applied.
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor
{
    @Accessor("duration")
    void setDuration(int duration);
}
