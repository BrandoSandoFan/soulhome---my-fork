/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import leaf.soulhome.SoulHome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The prickle a watched player feels (#189), and the soul's own unease while it is being looked
 * into. Both scale with the obviousness the server worked out from the gazer's observatory - see
 * {@code GazeNotices} - and neither points anywhere: a cue that led to a hidden watcher would make
 * Soulgaze useless.
 *
 * <p>The prickle is a sound and a brief tint at the screen's edge, fading over two seconds. The
 * lingering half is the same tint, faint and steady, for as long as someone is looking into the
 * soul its owner is standing in; the server clears it when the last watcher leaves, and it fades
 * over its last half-second so that clearing does not end in a cut.
 *
 * <p>Drawn with vanilla's own vignette texture and blend, so it reads as the edge of your vision
 * closing in rather than as a coloured frame laid over the game.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GazeNoticeOverlay
{
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

    private static final int PRICKLE_TICKS = 40;

    /** Loudest and darkest the prickle gets, at full obviousness. */
    private static final float PRICKLE_VOLUME = 0.9f;
    private static final float PRICKLE_TINT = 0.85f;

    /** The lingering unease is a fraction of the prickle - flavour, not a second alarm. */
    private static final float LINGER_TINT = 0.3f;

    /**
     * Ticks over which the lingering tint fades out at its end. Only the end fades: the prickle starts
     * at full strength on purpose, as a jolt, so it is never eased in.
     */
    private static final float LINGER_FADE_TICKS = 10f;

    private static final float PRICKLE_PITCH = 0.6f;

    /** How much of each channel the vignette holds back when it darkens the edge; see the blend below. */
    private static final float HOLD_RED = 0.55f;
    private static final float HOLD_GREEN = 0.8f;
    private static final float HOLD_BLUE = 0.45f;

    private static int prickleRemaining;
    private static float prickleStrength;
    private static int lingerRemaining;
    private static float lingerStrength;

    private GazeNoticeOverlay()
    {
    }

    public static void onNotice(double obviousness, int lingerTicks, boolean prickle)
    {
        if (!prickle && lingerTicks <= 0)
        {
            lingerRemaining = 0;
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        if (prickle)
        {
            prickleRemaining = PRICKLE_TICKS;
            prickleStrength = (float) obviousness;

            // a UI sound, not a positional one: a sound placed in the world would have a direction,
            // and direction is the observatory's reward, not the prickle's
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.SCULK_CLICKING, PRICKLE_PITCH, PRICKLE_VOLUME * (float) obviousness));
        }

        if (lingerTicks > 0)
        {
            lingerRemaining = lingerTicks;
            lingerStrength = (float) obviousness;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (prickleRemaining > 0)
        {
            prickleRemaining--;
        }

        if (lingerRemaining > 0)
        {
            lingerRemaining--;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        prickleRemaining = 0;
        lingerRemaining = 0;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        final float partialTick = event.getPartialTick();

        final float prickle = prickleRemaining > 0 ? prickleStrength * PRICKLE_TINT * prickleRemaining / PRICKLE_TICKS : 0f;

        // the countdown only steps at the end of a tick, so between two ticks the linger is still
        // partway through the one that is ending; subtracting the partial tick is what makes the fade
        // move every frame rather than every tick
        final float lingerLeft = Math.max(0f, lingerRemaining - partialTick);
        final float lingerFade = Math.min(1f, lingerLeft / LINGER_FADE_TICKS);
        final float linger = lingerRemaining > 0 ? lingerStrength * LINGER_TINT * lingerFade : 0f;
        final float tint = Math.min(1f, Math.max(prickle, linger));

        if (tint <= 0.001f || Minecraft.getInstance().player == null)
        {
            return;
        }

        final GuiGraphics graphics = event.getGuiGraphics();
        final int width = graphics.guiWidth();
        final int height = graphics.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        // the vignette blend darkens by the colour given: holding back more green than red and blue
        // leaves the edge a dim violet, the colour the rest of the mod's soul effects use
        graphics.setColor(tint * HOLD_RED, tint * HOLD_GREEN, tint * HOLD_BLUE, 1.0f);
        graphics.blit(VIGNETTE, 0, 0, -90, 0.0f, 0.0f, width, height, width, height);

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
