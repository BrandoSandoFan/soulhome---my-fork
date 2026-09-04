/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leaf.soulhome.SoulHome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Draws the ore Surveyor's Eye (#88) turned up, through whatever is in the way.
 *
 * <p>Deliberately the same line-box treatment the Soul Lens uses for regions, in a different
 * colour: a player who has learned to read one set of outlines should not have to learn a second
 * visual language for the other. Amber, because green and yellow are already spoken for by the
 * lens's classified and ambiguous states.
 *
 * <p>Drawn after translucent blocks with no depth test of its own, which is what puts the outline
 * through stone rather than behind it - the whole point of the ability.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurveyorsEyeRenderer
{
    private static final float LINE_ALPHA = 0.9f;
    private static final double INFLATE = 0.02d;

    private SurveyorsEyeRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            return;
        }

        final List<Long> positions = SurveyedBlocks.current();

        if (positions.isEmpty())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        final PoseStack pose = event.getPoseStack();

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        for (long packed : positions)
        {
            final BlockPos position = BlockPos.of(packed);
            final AABB box = new AABB(position).inflate(INFLATE);

            LevelRenderer.renderLineBox(pose, lines, box, 1.0f, 0.72f, 0.28f, LINE_ALPHA);
        }

        pose.popPose();

        buffers.endBatch(RenderType.lines());
    }
}
