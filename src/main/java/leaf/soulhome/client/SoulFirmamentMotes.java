/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.network.SyncSoulAmbienceMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

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
 * rank this is under one mote a tick across the whole visible band, each one nearly transparent.
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
    /** The soul's colour as one ARGB int, at the low alpha that makes a mote a haze and not a speck. */
    private static int packed()
    {
        return (63 << 24)
                | (channel(ClientAmbience.red()) << 16)
                | (channel(ClientAmbience.green()) << 8)
                | channel(ClientAmbience.blue());
    }

    private static int channel(float value)
    {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

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

        // ENTITY_EFFECT is the one vanilla particle that is both tintable and quiet enough to hang
        // in the air without reading as an effect. Since 1.20.5 the colour rides on the particle
        // option rather than in the velocity arguments, so this is not a straight port of the
        // 1.20.1 call - the alpha is ours to set here, and it is set low for the same reason.
        // The velocity arguments no longer carry the colour, but they are still read: a spell
        // particle handed two zeroes damps its own drift to a tenth, so a token nudge is what keeps
        // these reading as moving air rather than as specks hanging in place.
        level.addParticle(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, packed()),
                x, y, z, 0.01d, 0d, 0.01d);
    }
}
