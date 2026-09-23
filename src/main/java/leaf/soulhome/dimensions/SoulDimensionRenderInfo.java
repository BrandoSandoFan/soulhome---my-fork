/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.dimensions;

import leaf.soulhome.client.SoulSkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;


import net.minecraft.client.renderer.DimensionSpecialEffects.SkyType;

public class SoulDimensionRenderInfo extends DimensionSpecialEffects
{

    public SoulDimensionRenderInfo()
    {
        this(128,
                false,
                SkyType.NONE,
                false,
                true);
    }

    public SoulDimensionRenderInfo(float cloudLevel, boolean hasGround, SkyType skyType, boolean forceBrightLightmap, boolean constantAmbientLight)
    {
        super(cloudLevel, hasGround, skyType, forceBrightLightmap, constantAmbientLight);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 p_230494_1_, float p_230494_2_)
    {
        //copied from overworld
        return p_230494_1_.multiply(p_230494_2_ * 0.94F + 0.06F, p_230494_2_ * 0.94F + 0.06F, p_230494_2_ * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int p_230493_1_, int p_230493_2_)
    {
        return false;
    }

    /**
     * The soul's own sky (#163), when the ambience has one to draw. The sky type stays {@code NONE},
     * so with the ambience off - or in a soul this client has not been told about - vanilla draws
     * nothing here, exactly as it always did.
     */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
    {
        return SoulSkyRenderer.render(ticks, partialTick, modelViewMatrix, projectionMatrix);
    }
}
