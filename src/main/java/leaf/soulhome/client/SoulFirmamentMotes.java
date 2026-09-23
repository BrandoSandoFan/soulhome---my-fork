/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.network.SyncSoulAmbienceMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * The firmament and the verge, seen from a distance (#164).
 *
 * <p>{@code SoulBoundsRenderer} draws the box's edges as a shimmer that fades in as you approach a
 * face, which answers "may I build here" and answers it well. What it cannot do is give a player
 * standing in the middle of their island any sense of how much room is around them, because from
 * there it is invisible by design.
 *
 * <p>So the boundary also drifts. A thin, slow scatter of motes hangs under the ceiling and along
 * the walls, tinted the colour the soul currently is, thickening as the box grows with rank. It
 * reads as air rather than as a wall - which is the point: the issue thread asked for something a
 * player feels they could reach out and touch rather than abstract magic, and moving air is the
 * most physical thing available that does not also block the view.
 *
 * <p>Sparse on purpose, and never dense enough to obscure a build: at full intensity and the last
 * rank this is a couple of motes a tick across the whole visible band.
 *
 * <p>They used to be {@code ENTITY_EFFECT}, the potion swirl, chosen because it was the one tintable
 * vanilla particle quiet enough never to be noticed - and #163's playtest found it was exactly that.
 * Dust takes the soul's colour just as well and is a speck a player can actually see.
 */
public final class SoulFirmamentMotes
{
    /** How far from the player motes are placed. Beyond this they are too small to read anyway. */
    private static final double REACH = 40d;

    /** Depth of the band under the ceiling, and inside each wall, that motes drift in. */
    private static final double BAND = 5d;

    /** Bounded so a tick that owes many motes cannot spike - a stutter is worse than a thin drift. */
    private static final int MAX_PER_TICK = 3;

    private SoulFirmamentMotes()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        final float rate = ClientAmbience.moteRate();

        if (rate <= 0f || minecraft.level == null || minecraft.player == null || minecraft.isPaused())
        {
            return;
        }

        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience
                .forDimension(minecraft.level.dimension().location().toString());

        if (!SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul))
        {
            return;
        }

        final RandomSource random = minecraft.level.random;
        final int owed = Math.min(MAX_PER_TICK, (int) rate + (random.nextFloat() < rate % 1f ? 1 : 0));

        for (int i = 0; i < owed; i++)
        {
            spawn(minecraft.level, minecraft.player, soul, random);
        }
    }

    /**
     * One mote, either against the ceiling or against the nearest wall.
     *
     * <p>Split evenly rather than by which is closer: a player on the floor of a rank V soul is
     * nearer the wall than the ceiling almost everywhere, and a firmament that only appears when
     * you fly up to it is the shimmer's job, not this one.
     */
    /** Kept inside the box: air drifting past the outside of your own wall is not the firmament. */
    private static double clamp(double value, double half)
    {
        return Math.max(-half, Math.min(half, value));
    }

    private static void spawn(
            ClientLevel level, Player player, SyncSoulAmbienceMessage soul, RandomSource random)
    {
        final double half = soul.getVergeHalfExtentOrLegacy();
        final double ceiling = soul.getCeilingY();

        double x;
        double y;
        double z;

        if (random.nextBoolean())
        {
            x = clamp(player.getX() + (random.nextDouble() * 2d - 1d) * REACH, half);
            z = clamp(player.getZ() + (random.nextDouble() * 2d - 1d) * REACH, half);
            y = ceiling - random.nextDouble() * BAND;
        }
        else
        {
            final boolean alongX = random.nextBoolean();
            final double wall = (random.nextBoolean() ? 1d : -1d) * half;
            final double along = (random.nextDouble() * 2d - 1d) * REACH;
            final double inward = random.nextDouble() * BAND;

            x = alongX ? clamp(player.getX() + along, half) : wall - Math.signum(wall) * inward;
            z = alongX ? wall - Math.signum(wall) * inward : clamp(player.getZ() + along, half);

            // near where a player is actually looking, and never above the ceiling they are under
            y = Math.min(ceiling, player.getY() + (random.nextDouble() * 2d - 0.5d) * 12d);
        }

        if (player.distanceToSqr(x, y, z) > REACH * REACH)
        {
            // cheaper to throw the occasional draw away than to sample the band exactly
            return;
        }

        // lifted toward white so a mote in a deep-coloured soul is still a point of light against it
        final Vector3f colour = new Vector3f(
                0.35f + 0.65f * ClientAmbience.red(),
                0.35f + 0.65f * ClientAmbience.green(),
                0.35f + 0.65f * ClientAmbience.blue());

        level.addParticle(new DustParticleOptions(colour, 1.1f + random.nextFloat() * 0.6f), x, y, z,
                (random.nextDouble() - 0.5d) * 0.01d, 0d, (random.nextDouble() - 0.5d) * 0.01d);
    }
}
