/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.shaders.AbstractUniform;
import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.ClientSoulBuffs;
import leaf.soulhome.mixin.PostChainAccessor;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;

/**
 * Observatory: brightens the screen in proportion to Clear Sight's own magnitude, rather than
 * switching a fixed-strength potion effect on and off - the room's own point is that its strength
 * should grow smoothly rather than snap in at a threshold.
 *
 * <p>A dedicated {@link PostChain} the mod owns and drives itself, not vanilla's own night vision
 * shader - vanilla's is a binary on/off with a fixed intensity, exactly the shape this room is not
 * meant to have. Lazily created on first use and torn down permanently the moment anything about
 * loading or running it goes wrong, so a shader that fails to compile on some driver costs a
 * player the room's visual polish rather than their session.
 *
 * <p>The magnitude this reads is display-only, the same as every other client-side number in this
 * mod - {@link ClientSoulBuffs} is never authoritative, and nothing server-side depends on what
 * this class does with it. See {@code ClearSightEffect}, which does nothing at all on the server
 * for exactly that reason.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ClearSightRenderer
{
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(SoulHome.MODID, "clear_sight");

    /**
     * Mirrors {@code soulhome:clear_sight}'s own ceiling in {@code BuffSettings.DEFAULT_TYPE_CAPS}.
     * Kept as a constant rather than read from config, because the client is never told a server's
     * own (possibly customised) cap - only the magnitude, already clamped, that the server sends.
     * A server that raises its own ceiling simply saturates the shader a little earlier than it
     * otherwise would; nothing about that is unsafe, only slightly under-dramatic.
     */
    private static final double MAX_MAGNITUDE = 3.0d;

    private static PostChain chain;
    private static boolean broken;

    private ClearSightRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (broken || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null)
        {
            return;
        }

        final double magnitude = ClientSoulBuffs.get().magnitude(SoulBuffTypes.CLEAR_SIGHT);

        if (magnitude <= 0d)
        {
            return;
        }

        final float strength = (float) Math.min(1d, magnitude / MAX_MAGNITUDE);

        try
        {
            if (chain == null)
            {
                chain = new PostChain(
                        minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), SHADER);
            }

            chain.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

            for (PostPass pass : ((PostChainAccessor) chain).getPasses())
            {
                final EffectInstance effect = pass.getEffect();
                final AbstractUniform strengthUniform = effect.safeGetUniform("Strength");

                if (strengthUniform != null)
                {
                    strengthUniform.set(strength);
                }
            }

            chain.process(1.0f);
        }
        catch (IOException | RuntimeException e)
        {
            // a shader nobody could load or run is not a reason to try again every frame - the
            // room still grants the buff, it simply stops trying to draw it
            LogHelper.error("Clear Sight's shader failed and has been switched off: " + e.getMessage());
            broken = true;
            chain = null;
        }
    }
}
