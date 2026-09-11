/*
 * File created ~ 11 - 9 - 2026
 */

package leaf.soulhome.mixin;

import com.mojang.blaze3d.shaders.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Reaches {@code PostChain}'s own list of passes, so {@code ClearSightRenderer} can set a uniform
 * on the shader it drives every frame.
 *
 * <p>{@code PostChain} exposes {@link PostChain#process} and nothing finer-grained than that -
 * there is no public way to hand a custom pass a value that changes from one frame to the next,
 * which is exactly what a shader whose strength has to track a buff's own magnitude needs. A mixin
 * accessor rather than reflection, for the same reason {@link LivingEntityAccessor} and
 * {@link MobEffectInstanceAccessor} are: the field's name at runtime is not necessarily the name in
 * this source file once obfuscation is in play, and the mixin annotation processor is what remaps
 * it correctly in both a development workspace and an installed jar.
 *
 * <p>{@link PostPass#getEffect()} is already public and returns {@link EffectInstance} - only the
 * list that holds the passes needed reaching into.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor
{
    @Accessor("passes")
    List<PostPass> getPasses();
}
