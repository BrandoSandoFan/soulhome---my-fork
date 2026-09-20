/*
 * File created ~ 20 - 9 - 2026
 */

package leaf.soulhome.client.render;

import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The powder magazine's own shell (#194) - a small solid ball, not a spell effect, per the owner's
 * own steer that the room stays "one of the least magical" in the mod.
 *
 * <p>A single cube rather than anything that needs to face its direction of travel: every face is
 * shaded the same way in {@code soul_barrage_shot.png}, so the ball reads identically whichever
 * side happens to be toward the camera and no per-tick orientation code is needed.
 */
public class SoulBarrageShotModel extends Model
{
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocationHelper.prefix("soul_barrage_shot"), "main");

    private final ModelPart shell;

    public SoulBarrageShotModel(ModelPart root)
    {
        super(RenderType::entityCutoutNoCull);
        this.shell = root.getChild("shell");
    }

    public static LayerDefinition createBodyLayer()
    {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition parts = mesh.getRoot();

        parts.addOrReplaceChild(
                "shell",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
            float red, float green, float blue, float alpha)
    {
        this.shell.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
