/*
 * File created ~ 19 - 11 - 2026
 */

package leaf.soulhome.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the Luck of the Sea and Lure levels a bobber was cast with (#308).
 *
 * <p>Vanilla reads both from the rod's enchantments in the constructor and keeps them
 * {@code private final}; Forge has no event between "the bobber exists" and "it starts waiting for a
 * bite", and {@code ItemFishedEvent} is too late to change either. An accessor rather than
 * reflection for the reason {@link LivingEntityAccessor} gives: the field's runtime name is not the
 * one in this source file.
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor
{
    @Accessor("luck")
    int getLuck();

    @Mutable
    @Accessor("luck")
    void setLuck(int luck);

    @Accessor("lureSpeed")
    int getLureSpeed();

    @Mutable
    @Accessor("lureSpeed")
    void setLureSpeed(int lureSpeed);
}
