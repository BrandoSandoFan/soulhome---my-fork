/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Draws a soul's sky (#163) - see {@code SoulSky} for what each part of it means and why the
 * numbers are what they are. This class only draws: a gradient dome from the fog colour at the
 * horizon to the soul's own zenith, a glow along the horizon, slow veils of light in the colour of
 * the soul's second trait, and a star field that comes out with rank.
 *
 * <p>Called from {@code SoulDimensionRenderInfo#renderSky}, Forge's hook for a dimension that wants
 * a sky of its own, in the view-rotated pose vanilla draws its own sky in. Nothing here writes depth,
 * so every block in the world is in front of all of it.
 *
 * <h2>Nothing moves fast</h2>
 *
 * <p>The veils drift on cycles of a minute or more and the stars turn once every few hours. Nothing
 * blinks, twinkles or pulses (#167) - a sky that moves at all is a sky that can be made to move too
 * fast, and the only defence that holds at every setting is that nothing here has a fast cycle to
 * begin with.
 */
public final class SoulSkyRenderer
{
    private static final float RADIUS = 100f;
    private static final int AZIMUTH_STEPS = 48;
    private static final int ELEVATION_STEPS = 18;

    /** Veils: how many, where they hang, and how slowly they drift, in radians per second. */
    private static final float[] VEIL_ELEVATIONS = {22f, 38f, 55f};
    private static final float[] VEIL_HEIGHTS = {16f, 13f, 10f};
    private static final float[] VEIL_DRIFT = {0.021f, -0.016f, 0.012f};

    /** One turn of the star field every four hours - motion you notice by looking away and back. */
    private static final float STAR_DEGREES_PER_SECOND = 360f / (4f * 60f * 60f);

    private static VertexBuffer starBuffer;

    private SoulSkyRenderer()
    {
    }

    /**
     * @return whether a sky was drawn - false leaves the dimension's own (empty) sky alone, which is
     *         what an ambience switched off, or a soul the client has not been told about, looks like
     */
    public static boolean render(int ticks, float partialTick, PoseStack poseStack, Matrix4f projection)
    {
        if (!ClientAmbience.skyActive())
        {
            return false;
        }

        final float seconds = (ticks + partialTick) / 20f;
        final float[] fog = RenderSystem.getShaderFogColor();
        final float[] horizon = {fog[0], fog[1], fog[2]};

        RenderSystem.depthMask(false);
        // the dome is seen from inside, which is the side culling throws away
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        final Matrix4f pose = poseStack.last().pose();

        drawDome(pose, horizon);

        RenderSystem.enableBlend();

        // additive: light added to the sky behind it, which is what a veil or a star is
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        if (ClientAmbience.veilStrength() > 0.005f)
        {
            drawVeils(pose, seconds);
        }

        if (ClientAmbience.stars() > 0.005f)
        {
            drawStars(poseStack, projection, seconds);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        return true;
    }

    private static void drawDome(Matrix4f pose, float[] horizon)
    {
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();
        final float[] zenith = ClientAmbience.zenith();
        final float[] glow = ClientAmbience.glow();
        final float height = ClientAmbience.skyHeight();
        final float glowStrength = ClientAmbience.glowStrength();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // below the horizon is the horizon's own colour, all the way down: the void under an island
        // is the fog a player has always seen there
        for (int step = -1; step < ELEVATION_STEPS; step++)
        {
            final float low = step < 0 ? -90f : 90f * step / ELEVATION_STEPS;
            final float high = step < 0 ? 0f : 90f * (step + 1) / ELEVATION_STEPS;
            final float[] lowColour = domeColour(low, horizon, zenith, glow, height, glowStrength);
            final float[] highColour = domeColour(high, horizon, zenith, glow, height, glowStrength);

            for (int around = 0; around < AZIMUTH_STEPS; around++)
            {
                final float a0 = (float) (2d * Math.PI * around / AZIMUTH_STEPS);
                final float a1 = (float) (2d * Math.PI * (around + 1) / AZIMUTH_STEPS);

                vertex(builder, pose, low, a0, lowColour, 1f);
                vertex(builder, pose, high, a0, highColour, 1f);
                vertex(builder, pose, high, a1, highColour, 1f);
                vertex(builder, pose, low, a1, lowColour, 1f);
            }
        }

        BufferUploader.drawWithShader(builder.end());
    }

    /**
     * The dome's colour at one elevation. Eased toward the zenith by a curve that stays near the
     * horizon colour low down, so the lower sky still reads as distance, then glows at the horizon
     * itself where the soul's own colour sits brightest.
     */
    private static float[] domeColour(
            float elevation, float[] horizon, float[] zenith, float[] glow, float height, float glowStrength)
    {
        if (elevation <= 0f)
        {
            return horizon;
        }

        final float up = elevation / 90f;
        final float toward = height * (1f - (1f - up) * (1f - up));
        final float halo = glowStrength * (float) Math.exp(-up * 7f);
        final float[] colour = new float[3];

        for (int channel = 0; channel < 3; channel++)
        {
            final float gradient = horizon[channel] + (zenith[channel] - horizon[channel]) * toward;

            colour[channel] = gradient + (glow[channel] - gradient) * halo;
        }

        return colour;
    }

    /**
     * Ribbons of light, bright at their lower edge and fading upward, their line rising and falling
     * gently around the sky and their brightness gathered into slow-moving patches - which is the
     * shape an aurora has, and what keeps three bands from reading as three stripes.
     */
    private static void drawVeils(Matrix4f pose, float seconds)
    {
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();
        final float[] colour = ClientAmbience.veil();
        final float strength = ClientAmbience.veilStrength();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int veil = 0; veil < VEIL_ELEVATIONS.length; veil++)
        {
            final float drift = seconds * VEIL_DRIFT[veil];
            final float phase = veil * 2.1f;

            for (int around = 0; around < AZIMUTH_STEPS * 2; around++)
            {
                final float a0 = (float) (2d * Math.PI * around / (AZIMUTH_STEPS * 2));
                final float a1 = (float) (2d * Math.PI * (around + 1) / (AZIMUTH_STEPS * 2));

                final float base0 = veilBase(veil, a0, drift, phase);
                final float base1 = veilBase(veil, a1, drift, phase);
                final float alpha0 = strength * patch(a0, drift, phase);
                final float alpha1 = strength * patch(a1, drift, phase);

                vertex(builder, pose, base0, a0, colour, alpha0);
                vertex(builder, pose, base0 + VEIL_HEIGHTS[veil], a0, colour, 0f);
                vertex(builder, pose, base1 + VEIL_HEIGHTS[veil], a1, colour, 0f);
                vertex(builder, pose, base1, a1, colour, alpha1);
            }
        }

        BufferUploader.drawWithShader(builder.end());
    }

    private static float veilBase(int veil, float azimuth, float drift, float phase)
    {
        return VEIL_ELEVATIONS[veil]
                + 5f * (float) Math.sin(azimuth * 2f + drift + phase)
                + 2.5f * (float) Math.sin(azimuth * 5f - drift * 1.7f + phase);
    }

    /** Brightness along a veil: gathered into two or three soft patches that wander round the sky. */
    private static float patch(float azimuth, float drift, float phase)
    {
        final float wave = (float) Math.sin(azimuth * 3f + drift * 1.3f + phase);

        return 0.15f + 0.85f * Math.max(0f, wave) * Math.max(0f, wave);
    }

    private static void drawStars(PoseStack poseStack, Matrix4f projection, float seconds)
    {
        if (starBuffer == null || starBuffer.isInvalid())
        {
            starBuffer = buildStars();
        }

        final float stars = ClientAmbience.stars();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(seconds * STAR_DEGREES_PER_SECOND));
        poseStack.mulPose(Axis.XP.rotationDegrees(-20f));

        RenderSystem.setShaderColor(stars, stars, stars, stars);
        starBuffer.bind();
        starBuffer.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionShader());
        VertexBuffer.unbind();

        poseStack.popPose();
    }

    /**
     * The star field, built once: small quads scattered over the upper sky, the same way vanilla
     * builds its own, from a fixed seed so every soul's stars are the same stars - rank decides how
     * many of them show, not where they are.
     */
    private static VertexBuffer buildStars()
    {
        final RandomSource random = RandomSource.create(0x50_4C_4B_21L);
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int star = 0; star < 1_400; star++)
        {
            double x = random.nextFloat() * 2f - 1f;
            double y = random.nextFloat() * 2f - 1f;
            double z = random.nextFloat() * 2f - 1f;
            final double size = 0.12f + random.nextFloat() * 0.12f;
            final double length = x * x + y * y + z * z;

            // uniform over the sphere, and only the half a player can see above the horizon
            if (length <= 0.01d || length >= 1d || y < -0.1d)
            {
                continue;
            }

            final double scale = 1d / Math.sqrt(length);

            x *= scale;
            y *= scale;
            z *= scale;

            final double px = x * RADIUS;
            final double py = y * RADIUS;
            final double pz = z * RADIUS;
            final double yaw = Math.atan2(x, z);
            final double sinYaw = Math.sin(yaw);
            final double cosYaw = Math.cos(yaw);
            final double pitch = Math.atan2(Math.sqrt(x * x + z * z), y);
            final double sinPitch = Math.sin(pitch);
            final double cosPitch = Math.cos(pitch);
            final double roll = random.nextDouble() * Math.PI * 2d;
            final double sinRoll = Math.sin(roll);
            final double cosRoll = Math.cos(roll);

            for (int corner = 0; corner < 4; corner++)
            {
                final double u = ((corner & 2) - 1) * size;
                final double v = ((corner + 1 & 2) - 1) * size;
                final double across = u * cosRoll - v * sinRoll;
                final double along = v * cosRoll + u * sinRoll;
                final double lift = across * sinPitch;
                final double out = -across * cosPitch;

                builder.vertex(px + out * sinYaw - along * cosYaw, py + lift, pz + along * sinYaw + out * cosYaw).endVertex();
            }
        }

        final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();

        return buffer;
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, float elevation, float azimuth, float[] colour, float alpha)
    {
        final double rise = Math.toRadians(elevation);
        final float flat = (float) Math.cos(rise) * RADIUS;

        builder.vertex(pose, flat * (float) Math.sin(azimuth), (float) Math.sin(rise) * RADIUS, flat * (float) Math.cos(azimuth))
                .color(colour[0], colour[1], colour[2], alpha)
                .endVertex();
    }
}
