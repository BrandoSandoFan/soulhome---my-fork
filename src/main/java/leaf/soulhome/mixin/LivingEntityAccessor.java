/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the {@code jumping} flag - whether the jump key is held right now.
 *
 * <p>Vanilla keeps it {@code protected} because nothing outside the entity was ever meant to need
 * it, and Forge offers no hook for a jump that happens mid-air. A mixin accessor rather than
 * reflection, because the field's name at runtime is not the name in this source file: an
 * {@code ObfuscationReflectionHelper} lookup would have to be given the obfuscated name by hand,
 * and one written against the readable one resolves in a development workspace and silently
 * never resolves in an installed jar. The mixin annotation processor remaps this for us.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor
{
    @Accessor("jumping")
    boolean getJumping();
}
