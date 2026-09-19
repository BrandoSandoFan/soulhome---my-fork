/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.client.render;

import leaf.soulhome.entity.SoulVesselEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A {@link PlayerModel} posed by hand for the Soul Vessel (#182): legs folded, arms resting on the
 * knees, head bowed, with a slow breathing bob so a body left out for an hour does not read as a
 * frozen player. No animation system - the pose is fixed here rather than driven by any of
 * {@code HumanoidModel}'s walk/swim/crouch machinery, none of which this entity ever triggers.
 */
public class SoulVesselModel extends PlayerModel<SoulVesselEntity>
{
    public SoulVesselModel(ModelPart root)
    {
        super(root, false);
    }

    @Override
    public void setupAnim(
            SoulVesselEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        // head tracking only - everything else here overrides whatever this would otherwise set
        super.setupAnim(entity, 0f, 0f, ageInTicks, netHeadYaw, headPitch);

        final float breathe = Mth.sin(ageInTicks * 0.05f) * 0.02f;

        this.head.xRot = 0.35f;
        this.hat.xRot = this.head.xRot;

        this.body.xRot = breathe;

        this.rightArm.xRot = -0.35f + breathe;
        this.rightArm.zRot = 0.1f;
        this.leftArm.xRot = -0.35f + breathe;
        this.leftArm.zRot = -0.1f;

        this.rightLeg.xRot = -1.55f;
        this.rightLeg.zRot = 0.1f;
        this.leftLeg.xRot = -1.55f;
        this.leftLeg.zRot = -0.1f;

        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
        this.jacket.copyFrom(this.body);
    }
}
