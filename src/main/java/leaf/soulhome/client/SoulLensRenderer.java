/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leaf.soulhome.SoulHome;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.RegionHighlight;
import leaf.soulhome.network.SyncSoulRegionsMessage;
import leaf.soulhome.registry.ItemsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Draws the regions the Soul Lens revealed.
 *
 * <p>Region detection is the one step of this feature with no visible output of its own - a player
 * sees their blocks and their buffs, and nothing in between. Outlining what the scanner decided
 * was one room turns "it just doesn't work" into "ah, the mod thinks my library and my hallway are
 * the same room", which is a problem a player can actually fix.
 *
 * <p>Colour carries the classification: green was awarded, yellow was too close to call between
 * two archetypes, grey was found but is not anything yet.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulLensRenderer
{
    private static final float LINE_ALPHA = 0.85f;

    /** Nudged out by a hair so an outline on a wall does not fight the wall for the same pixels. */
    private static final double INFLATE = 0.02d;

    /** How many region labels the overlay lists before it is in the way rather than helpful. */
    private static final int MAX_LABELS = 6;

    private static final int LABEL_LEFT = 6;
    private static final int LABEL_TOP = 6;
    private static final int LABEL_LINE_HEIGHT = 10;

    private SoulLensRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            return;
        }

        final List<RegionHighlight> regions = current();

        if (regions.isEmpty())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        final PoseStack pose = event.getPoseStack();

        pose.pushPose();
        //world space is relative to the camera at this point in the frame
        pose.translate(-camera.x, -camera.y, -camera.z);

        for (RegionHighlight region : regions)
        {
            //bounds are inclusive block positions, so the far corner runs to the far side of that block
            final AABB box = new AABB(
                    region.minX(), region.minY(), region.minZ(),
                    region.maxX() + 1d, region.maxY() + 1d, region.maxZ() + 1d)
                    .inflate(INFLATE);

            if (region.isClassified())
            {
                LevelRenderer.renderLineBox(pose, lines, box, 0.35f, 1.0f, 0.45f, LINE_ALPHA);
            }
            else if (region.isAmbiguous())
            {
                LevelRenderer.renderLineBox(pose, lines, box, 1.0f, 0.85f, 0.25f, LINE_ALPHA);
            }
            else
            {
                LevelRenderer.renderLineBox(pose, lines, box, 0.65f, 0.65f, 0.7f, LINE_ALPHA);
            }
        }

        pose.popPose();

        //lines are not batched with anything else this frame, so they have to be flushed here
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Names the outlines, and marks the one being stood in. Drawn only while the lens is held, so
     * the corner of the screen goes back to normal as soon as it is put away.
     */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;

        if (player == null || minecraft.options.hideGui || !isHoldingLens(player))
        {
            return;
        }

        final List<RegionHighlight> regions = current();

        if (regions.isEmpty())
        {
            return;
        }

        final GuiGraphics graphics = event.getGuiGraphics();
        final Vec3 position = player.position();

        int line = 0;

        for (RegionHighlight region : regions)
        {
            if (line >= MAX_LABELS)
            {
                break;
            }

            final boolean standingIn = region.contains(position.x, position.y, position.z);

            graphics.drawString(
                    minecraft.font,
                    label(region, standingIn),
                    LABEL_LEFT,
                    LABEL_TOP + line * LABEL_LINE_HEIGHT,
                    colourOf(region),
                    true);

            line++;
        }
    }

    private static Component label(RegionHighlight region, boolean standingIn)
    {
        final Component name = region.displayName().isBlank()
                ? Component.translatable(Constants.StringKeys.REGION_UNCLASSIFIED)
                : Component.translatable(region.displayName());

        //a tier for a room that earned one, a question mark for one that did not
        final Component body = Component.literal("")
                .append(name)
                .append(Component.literal(region.isClassified() ? " T" + region.tier() : " ?"));

        return standingIn ? Component.literal("> ").append(body) : body;
    }

    /** The same three colours as the outlines, so a label and its box read as one thing. */
    private static int colourOf(RegionHighlight region)
    {
        if (region.isClassified())
        {
            return 0x55FF55;
        }

        return region.isAmbiguous() ? 0xFFFF55 : 0xAAAAAA;
    }

    private static boolean isHoldingLens(LocalPlayer player)
    {
        return player.getMainHandItem().is(ItemsRegistry.SOUL_LENS.get())
                || player.getOffhandItem().is(ItemsRegistry.SOUL_LENS.get());
    }

    private static List<RegionHighlight> current()
    {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            return List.of();
        }

        return SyncSoulRegionsMessage.ClientSoulRegions.get(
                minecraft.level.dimension().location().toString());
    }
}
