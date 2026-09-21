/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leaf.soulhome.entity.SoulBarrageShotEntity;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Barrage's shell (#194), drawn as a small solid ball rather than left invisible behind its trail
 * particle - the owner's own steer was that the powder magazine is "one of the least magical"
 * rooms and the shot should keep that identity, so this is a plain cannon shell rather than
 * anything that reads as a spell.
 *
 * <p>{@link SoulBarrageShotModel} is a single untextured-orientation cube, so nothing here needs to
 * rotate the model to face the shell's direction of travel.
 */
public class SoulBarrageShotRenderer extends EntityRenderer<SoulBarrageShotEntity>
{
    private static final ResourceLocation TEXTURE = ResourceLocationHelper.prefix("textures/entity/soul_barrage_shot.png");

    private final SoulBarrageShotModel model;

    public SoulBarrageShotRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new SoulBarrageShotModel(context.bakeLayer(SoulBarrageShotModel.LAYER_LOCATION));
    }

    @Override
    public void render(
            SoulBarrageShotEntity entity, float yaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        poseStack.pushPose();

        final VertexConsumer vertexConsumer = buffer.getBuffer(this.model.renderType(this.getTextureLocation(entity)));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulBarrageShotEntity entity)
    {
        return TEXTURE;
    }
}
