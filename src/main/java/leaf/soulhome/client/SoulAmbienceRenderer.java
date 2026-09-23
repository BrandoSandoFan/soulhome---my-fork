/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What a soul looks like from inside it (#163/#164/#165): the colour of the void, and how far off
 * it stands.
 *
 * <p>Two hooks and a tick, and every one of them does nothing unless {@link ClientAmbience} says a
 * soul is being looked at - so a client outside a soulhome runs one field read per frame and
 * changes nothing at all.
 *
 * <h2>Why fog, and why it can never be in the way</h2>
 *
 * <p>The soul dimension draws no sky ({@code SkyType.NONE}), so what fills the view past the last
 * block <i>is</i> the fog colour. That makes fog the only lever that changes what the place looks
 * like from the middle of it, which is where a player stands.
 *
 * <p>It is also the lever most able to ruin the dimension, since a building space you cannot see
 * across is not a building space. That is handled in {@code SoulAmbience.of} rather than here:
 * the distance it hands back is derived from the soulhome's own verge and is always past the far
 * corner of it, so there is no rank, no blend and no intensity at which the haze stands between a
 * player and something they were allowed to place.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SoulAmbienceRenderer
{
    private SoulAmbienceRenderer()
    {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        final Minecraft minecraft = Minecraft.getInstance();

        ClientAmbience.tick(minecraft);
        SoulFirmamentMotes.tick(minecraft);
        SoulWeatherParticles.tick(minecraft);
        SoulAmbienceSounds.tick(minecraft);
        SoulAmbienceBed.tick(minecraft);
        SoulMusicPlayer.tick(minecraft);
    }

    /**
     * The colour of the void, and of the haze against it.
     *
     * <p>Left alone entirely when a soul has no character to speak of, rather than set to the same
     * colour it already was: a hook that writes on every frame regardless is a hook that will
     * eventually disagree with something else about what it was supposed to leave alone.
     */
    @SubscribeEvent
    public static void onComputeFogColour(ViewportEvent.ComputeFogColor event)
    {
        if (!ClientAmbience.tinted())
        {
            return;
        }

        if (event.getCamera().getFluidInCamera() != FogType.NONE)
        {
            // underwater and in lava the fog colour is telling the player something they need
            return;
        }

        event.setRed(ClientAmbience.red());
        event.setGreen(ClientAmbience.green());
        event.setBlue(ClientAmbience.blue());
    }

    /**
     * How far off the void stands - the rank half of #164.
     *
     * <p>A rank 0 soul is a small box under a close sky; a rank V soul opens out to something like
     * ten times the depth. It is legible standing still, which is what the issue asks for, and it
     * is the one cue in the mod that is felt rather than read.
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event)
    {
        if (!ClientAmbience.hasFog() || event.getType() != FogType.NONE)
        {
            return;
        }

        final float far = ClientAmbience.fogFar();
        final float near = ClientAmbience.fogNear();

        // only ever brings the void closer, never pushes it further out than the game was already
        // drawing. Past the render distance there is nothing to show anyway, and fog pushed beyond
        // it would put the hard edge of the loaded world on display instead of hiding it - so a
        // soul opened up past what this machine draws simply looks like the machine's own horizon.
        if (far >= event.getFarPlaneDistance())
        {
            return;
        }

        event.setFarPlaneDistance(far);

        // and the gradient only ever gets longer, so the haze arrives gradually rather than as a
        // wall at the far plane
        event.setNearPlaneDistance(Math.min(near, event.getNearPlaneDistance()));
    }
}
