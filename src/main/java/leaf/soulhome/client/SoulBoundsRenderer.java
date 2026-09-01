/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leaf.soulhome.SoulHome;
import leaf.soulhome.network.SyncSoulBoundsMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The firmament and the verge (#78/#81): the ceiling and four walls of a soulhome's box, drawn as
 * a shimmer rather than a lid. Rule 5 of the Ascent epic is that scarcity must be legible - a wall
 * a player only discovers by having a placement refused is a bug report, not a design constraint.
 *
 * <p>Not a solid surface, and not always-on at full strength: visibility is driven by distance to
 * the box's own faces, so the ceiling all but disappears while a player is building on the floor
 * and comes back plainly into view the moment they climb high enough to matter. That is also
 * exactly when a denied placement would otherwise be the first anyone hears of it.
 *
 * <p>Reuses {@code LevelRenderer.renderLineBox}, the same primitive {@code SoulLensRenderer} draws
 * region outlines with, rather than a dedicated shader - one drawing primitive for every box this
 * mod outlines in the world.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulBoundsRenderer
{
    /** Alpha at zero distance from a face - deliberately short of solid, so it always reads as a shimmer. */
    private static final float MAX_ALPHA = 0.55f;

    /** Blocks out from a face at which the shimmer has faded to nothing. */
    private static final double FADE_DISTANCE = 10.0d;

    private SoulBoundsRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null)
        {
            return;
        }

        final SyncSoulBoundsMessage bounds = SyncSoulBoundsMessage.ClientSoulBounds.forDimension(
                minecraft.level.dimension().location().toString());

        if (bounds.getCeilingY() <= bounds.getFloorY())
        {
            // the INVALID sentinel, or a soulhome this client has not been told the box of yet
            return;
        }

        final Vec3 playerPos = minecraft.player.position();
        final float alpha = alphaFor(playerPos, bounds);

        if (alpha <= 0.01f)
        {
            return;
        }

        final Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        final PoseStack pose = event.getPoseStack();

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        final int half = bounds.getVergeHalfExtent();
        final AABB box = new AABB(
                -half, bounds.getFloorY(), -half,
                half + 1d, bounds.getCeilingY(), half + 1d);

        LevelRenderer.renderLineBox(pose, lines, box, 0.65f, 0.8f, 1.0f, alpha);

        pose.popPose();

        buffers.endBatch(RenderType.lines());
    }

    /**
     * How visible the box should be right now: strongest right at a face, faded to nothing past
     * {@link #FADE_DISTANCE}. Only the ceiling and the four walls count - the floor needs no marker
     * of its own, since a player cannot build below it and so never approaches it from outside.
     */
    private static float alphaFor(Vec3 playerPos, SyncSoulBoundsMessage bounds)
    {
        final double half = bounds.getVergeHalfExtent();

        final double distToCeiling = bounds.getCeilingY() - playerPos.y;
        final double distToWallPosX = half - playerPos.x;
        final double distToWallNegX = playerPos.x + half;
        final double distToWallPosZ = half - playerPos.z;
        final double distToWallNegZ = playerPos.z + half;

        final double nearest = Math.max(0d, Math.min(
                Math.min(Math.abs(distToCeiling), Math.min(Math.abs(distToWallPosX), Math.abs(distToWallNegX))),
                Math.min(Math.abs(distToWallPosZ), Math.abs(distToWallNegZ))));

        final double fraction = Math.max(0d, 1d - nearest / FADE_DISTANCE);

        return (float) (fraction * MAX_ALPHA);
    }
}
