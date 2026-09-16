/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import leaf.soulhome.entity.SoulBarrageShotEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Barrage's shell (#194) carries no model or texture of its own - it is meant to be seen by its
 * {@code SOUL_FIRE_FLAME} trail, the same way a shot's motion reads before its shape does. Nothing
 * here draws anything; {@link #getTextureLocation} exists only because the base class demands one,
 * and is never actually bound.
 */
public class SoulBarrageShotRenderer extends EntityRenderer<SoulBarrageShotEntity>
{
    public SoulBarrageShotRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(
            SoulBarrageShotEntity entity, float yaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        // deliberately not calling super - there is nothing to draw, only the trail particle
    }

    @Override
    public ResourceLocation getTextureLocation(SoulBarrageShotEntity entity)
    {
        return MissingTextureAtlasSprite.getLocation();
    }
}
