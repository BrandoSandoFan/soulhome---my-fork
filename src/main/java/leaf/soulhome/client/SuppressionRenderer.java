/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leaf.soulhome.SoulHome;
import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.mixin.PostChainAccessor;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.List;

/**
 * Draws suppression (#188): a ring aura around each ascended player in view, and - where both the
 * server and this player allow it - a warp in the air around them.
 *
 * <h2>Anchored on them, never on the screen (rule 4 of #181)</h2>
 *
 * Both channels are centred on the player being observed, in world space, and follow them. The aura
 * is drawn in the world; the warp is a post-processing pass, but it is handed each player's own
 * projected position and radius every frame, so a high-rank player at the edge of your vision warps
 * the edge of your vision. A warp in the middle of the screen sourced from something in the corner
 * of it would read as a bug rather than as pressure.
 *
 * <h2>Amount and form</h2>
 *
 * The server sends a signature per player (see {@code SuppressionSettings}): radius and strength
 * are theirs, legibility is this player's. At no legibility the aura is a handful of trembling,
 * overlapping loops that cannot be counted and the warp is a formless refracting swirl; as
 * legibility rises the loops settle into {@code rings} crisp concentric circles and the warp
 * resolves into ripples of the same count, and weakens, so a field you can read is one you can aim
 * through.
 *
 * <p>The warp takes at most {@link #MAX_DISTORTED} players, nearest first. The shader has a fixed
 * set of uniforms and four is where a crowd stops being readable anyway; the aura is still drawn
 * for everyone in view beyond that.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SuppressionRenderer
{
    public static final int MAX_DISTORTED = 4;

    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(SoulHome.MODID, "suppression");

    private static final int SEGMENTS = 48;

    /** The loops a formless field is smeared into - more than a rank could need, so none can be counted. */
    private static final int SMEAR_LOOPS = 7;

    private static PostChain chain;
    private static boolean broken;

    private SuppressionRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (ClientSuppression.isEmpty())
        {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            drawAuras(event);
        }
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL)
        {
            drawWarp(event);
        }
    }

    private static void drawAuras(RenderLevelStageEvent event)
    {
        final List<ClientSuppression.InView> inView = ClientSuppression.visible();

        if (inView.isEmpty())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Camera camera = event.getCamera();
        final Vec3 eye = camera.getPosition();
        final Vector3f left = camera.getLeftVector();
        final Vector3f up = camera.getUpVector();
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partial;

        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        final PoseStack pose = event.getPoseStack();

        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);

        final Matrix4f matrix = pose.last().pose();
        final PoseStack.Pose normal = pose.last();

        for (ClientSuppression.InView seen : inView)
        {
            final ClientSuppression.Perceived field = seen.perceived();
            final Vec3 centre = centreOf(seen.player(), partial);
            final float legibility = field.legibility();

            // fades in over the last quarter of the range it is perceived at, rather than popping
            final float reach = (float) Math.min(1d, (field.range() - seen.distance()) / (field.range() * 0.25d));
            final float alpha = reach * (0.25f + 0.45f * field.strength());

            // the legible rings, sharpening as legibility rises
            for (int ring = 1; ring <= field.rings(); ring++)
            {
                final float radius = field.radius() * (0.4f + 0.6f * ring / field.rings());
                loop(lines, matrix, normal, centre, left, up, radius, 1f - legibility, time, ring,
                        0.75f, 0.6f, 1.0f, alpha * legibility);
            }

            // the smear, fading as legibility rises: loops at no particular radius, trembling, too
            // many and too alike to count - the same field, before it can be read
            for (int smear = 0; smear < SMEAR_LOOPS; smear++)
            {
                final float wander = 0.35f + 0.65f * (float) (0.5d + 0.5d * Math.sin(time * 0.05d + smear * 1.7d));
                loop(lines, matrix, normal, centre, left, up, field.radius() * wander, 1f, time, 17 + smear,
                        0.6f, 0.5f, 0.85f, alpha * (1f - legibility) * 0.7f);
            }
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * One camera-facing loop. {@code tremble} of 0 is a clean circle; 1 wobbles its radius by up to a
     * third, differently along its length and over time, so it reads as heat haze rather than a line.
     */
    private static void loop(
            VertexConsumer lines, Matrix4f matrix, PoseStack.Pose normal, Vec3 centre, Vector3f left, Vector3f up,
            float radius, float tremble, float time, int seed, float r, float g, float b, float alpha)
    {
        if (alpha <= 0.01f || radius <= 0f)
        {
            return;
        }

        Vec3 previous = null;

        for (int i = 0; i <= SEGMENTS; i++)
        {
            final double angle = (Math.PI * 2d * i) / SEGMENTS;
            final double wobble = 1d + tremble * 0.33d
                    * Math.sin(angle * (3 + seed % 4) + time * 0.21d + seed)
                    * Math.cos(angle * 2d - time * 0.13d + seed * 0.5d);
            final double rr = radius * wobble;
            final Vec3 point = centre.add(
                    (left.x() * Math.cos(angle) + up.x() * Math.sin(angle)) * rr,
                    (left.y() * Math.cos(angle) + up.y() * Math.sin(angle)) * rr,
                    (left.z() * Math.cos(angle) + up.z() * Math.sin(angle)) * rr);

            if (previous != null)
            {
                final Vec3 d = point.subtract(previous).normalize();

                lines.addVertex(matrix, (float) previous.x, (float) previous.y, (float) previous.z)
                        .setColor(r, g, b, alpha)
                        .setNormal(normal, (float) d.x, (float) d.y, (float) d.z);
                lines.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                        .setColor(r, g, b, alpha)
                        .setNormal(normal, (float) d.x, (float) d.y, (float) d.z);
            }

            previous = point;
        }
    }

    private static void drawWarp(RenderLevelStageEvent event)
    {
        if (broken || !SoulHomeClientConfig.suppressionDistortion())
        {
            return;
        }

        final List<ClientSuppression.InView> inView = ClientSuppression.visible();

        if (inView.isEmpty())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Camera camera = event.getCamera();
        final Vec3 eye = camera.getPosition();
        final Vector3f up = camera.getUpVector();
        final Matrix4f view = event.getPoseStack().last().pose();
        final Matrix4f projection = event.getProjectionMatrix();
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final float aspect = (float) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());

        final float[][] targets = new float[MAX_DISTORTED][4];
        final float[][] shapes = new float[MAX_DISTORTED][4];
        int used = 0;

        for (ClientSuppression.InView seen : inView)
        {
            if (used >= MAX_DISTORTED)
            {
                break;
            }

            final ClientSuppression.Perceived field = seen.perceived();

            if (!field.distortion())
            {
                continue;
            }

            final Vec3 centre = centreOf(seen.player(), partial).subtract(eye);
            final float[] screen = project(centre, view, projection);
            final float[] rim = project(centre.add(up.x() * field.radius(), up.y() * field.radius(), up.z() * field.radius()), view, projection);

            if (screen == null || rim == null)
            {
                // behind the camera: nothing on screen to warp
                continue;
            }

            final float dx = (rim[0] - screen[0]) * aspect;
            final float dy = rim[1] - screen[1];

            targets[used] = new float[]{screen[0], screen[1], (float) Math.sqrt(dx * dx + dy * dy), field.displacement()};
            shapes[used] = new float[]{field.legibility(), field.rings(), 0f, 0f};
            used++;
        }

        if (used == 0)
        {
            return;
        }

        try
        {
            if (chain == null)
            {
                chain = new PostChain(
                        minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), SHADER);
            }

            chain.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

            final float time = ((minecraft.level == null ? 0 : minecraft.level.getGameTime() % 24_000L) + partial) / 20f;

            for (PostPass pass : ((PostChainAccessor) chain).getPasses())
            {
                final EffectInstance effect = pass.getEffect();

                setUniform(effect, "Time", time);

                for (int i = 0; i < MAX_DISTORTED; i++)
                {
                    final AbstractUniform target = effect.safeGetUniform("Target" + i);
                    final AbstractUniform shape = effect.safeGetUniform("Shape" + i);

                    target.set(targets[i][0], targets[i][1], targets[i][2], targets[i][3]);
                    shape.set(shapes[i][0], shapes[i][1], shapes[i][2], shapes[i][3]);
                }
            }

            chain.process(partial);
        }
        catch (IOException | RuntimeException e)
        {
            // the same bargain as Clear Sight's: a warp nobody could load costs a player the warp,
            // never their session. The aura and the drone carry everything it would have
            LogHelper.error("Suppression's shader failed and has been switched off: " + e.getMessage());
            broken = true;
            chain = null;
        }
    }

    private static void setUniform(EffectInstance effect, String name, float value)
    {
        effect.safeGetUniform(name).set(value);
    }

    /**
     * A camera-relative point to screen space, {@code (0,0)} bottom left and {@code (1,1)} top right,
     * matching the post pass's own texture coordinates. Null for a point behind the camera.
     */
    private static float[] project(Vec3 relative, Matrix4f view, Matrix4f projection)
    {
        final Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1f);

        clip.mul(view);
        clip.mul(projection);

        if (clip.w() <= 0.05f)
        {
            return null;
        }

        return new float[]{(clip.x() / clip.w() + 1f) * 0.5f, (clip.y() / clip.w() + 1f) * 0.5f};
    }

    /** Chest height, interpolated - the field sits around the body, not at its feet. */
    private static Vec3 centreOf(Player player, float partial)
    {
        return player.getPosition(partial).add(0d, player.getBbHeight() * 0.55d, 0d);
    }
}
