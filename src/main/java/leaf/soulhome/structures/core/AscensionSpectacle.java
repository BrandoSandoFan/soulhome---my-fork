/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * What the ascension ritual looks like (#83/#114), reduced to a list of particles a server can send
 * and a test can count.
 *
 * <p>It used to be one to six end rods a tick in a cloud a third of a block wide, and a few portal
 * specks climbing the pillar - thirty seconds of standing still for the biggest moment in the mod,
 * and #163's first playtest found it barely registered. This is the choreography that replaces it,
 * in four movements that build over the hold and one that pays it off:
 *
 * <ul>
 *   <li><b>The helix</b> - strands of the soul's own colour winding up the pillar from its base to
 *       the cap, tightening as the hold goes on. The pillar is the thing the ritual is about; this is
 *       what makes it look like it is doing something.</li>
 *   <li><b>The gathering</b> - glyphs drawn in toward the cap from a ring around it, the ring widening
 *       as more is drawn in. The soul coming to the player.</li>
 *   <li><b>The beam</b> - a column of light rising from the cap toward the firmament, taller and
 *       denser as the hold goes on. The thing that is about to lift.</li>
 *   <li><b>The steps</b> - at each quarter, as the hum steps up a note, a ring of light thrown
 *       outward from the cap. Seen from anywhere in the soul.</li>
 *   <li><b>The ascent</b> - on completion, a burst in every direction, a ring racing out along the
 *       ground, a shell of the soul's colour, and the beam fired up to the sky in one go.</li>
 * </ul>
 *
 * <p>Everything scales with the rank being climbed to, the same way the ritual's own costs do, so
 * reaching V looks like the bigger event it is. And every count is bounded - {@code
 * AscensionSpectacleTest} holds the busiest tick of the busiest rank under a budget, because a
 * spectacle that stutters the client is a worse moment than the one it replaced.
 *
 * <p>Minecraft-free: positions are offsets from the cap's top centre, and {@link Kind} names what a
 * particle is for rather than which vanilla type draws it. {@code AscensionRitualService} owns that
 * mapping, and the soul's colour.
 */
public final class AscensionSpectacle
{
    /** No ordinary tick of the hold sends more than this. */
    public static final int MAX_PER_TICK = 40;

    /** And a tick a step is thrown on, no more than this. */
    public static final int MAX_STEP_TICK = 140;

    /** And the completion sends no more than this, at any rank. */
    public static final int MAX_COMPLETION = 900;

    /** The fractions of the hold at which a step is thrown - the same quarters the hum steps up on. */
    public static final double[] STEPS = {0.25, 0.5, 0.75};

    /** What a particle is for. The server decides what draws it. */
    public enum Kind
    {
        /** The soul's own colour, a speck that hangs where it is put. */
        SOUL,

        /** A point of white light that drifts along its velocity. */
        LIGHT,

        /** A glyph drawn from its velocity's offset in toward its position. */
        GLYPH,

        /** A bright spark thrown along its velocity. */
        SPARK,

        /** A mote rising up the pillar. */
        RISE
    }

    /**
     * One particle, relative to the cap's top centre.
     *
     * @param kind what it is for
     * @param x    offset east, in blocks
     * @param y    offset up
     * @param z    offset south
     * @param vx   velocity, blocks per tick - or, for a {@link Kind#GLYPH}, the offset it flies in from
     * @param vy   as above
     * @param vz   as above
     */
    public record Mote(Kind kind, double x, double y, double z, double vx, double vy, double vz)
    {
    }

    private AscensionSpectacle()
    {
    }

    /**
     * How much grander a ritual to {@code targetRank} is than one to rank I: 1 at rank I, 1.8 at V.
     */
    public static double scale(int targetRank)
    {
        return 1d + 0.2d * Math.max(0, Math.min(4, targetRank - 1));
    }

    /**
     * One tick of the hold.
     *
     * @param elapsed      ticks since the ritual began
     * @param total        how long it runs
     * @param targetRank   the rank being climbed to
     * @param pillarHeight blocks from the pillar's base to the cap the player stands on
     * @param seed         any per-ritual number; the same seed and tick give the same particles
     */
    public static List<Mote> hold(int elapsed, int total, int targetRank, int pillarHeight, long seed)
    {
        final List<Mote> motes = new ArrayList<>();
        final SplittableRandom random = new SplittableRandom(seed * 31L + elapsed);
        final double progress = total <= 0 ? 1d : Math.max(0d, Math.min(1d, elapsed / (double) total));
        final double scale = scale(targetRank);
        final double height = Math.max(1d, pillarHeight);

        helix(motes, elapsed, progress, scale, height);
        gathering(motes, random, progress, scale);
        beam(motes, random, progress, scale);

        for (int step = 0; step < STEPS.length; step++)
        {
            if (elapsed == (int) (total * STEPS[step]))
            {
                ring(motes, 24 + 16 * step, 0.25d + 0.06d * step, Kind.LIGHT, 0.4d);
                ring(motes, 18 + 8 * step, 0.18d, Kind.SOUL, 0.4d);
            }
        }

        return motes;
    }

    /**
     * The payoff: everything at once, the moment the rank arrives.
     *
     * @param targetRank the rank just reached
     * @param seed       any per-ritual number
     */
    public static List<Mote> completion(int targetRank, long seed)
    {
        final List<Mote> motes = new ArrayList<>();
        final SplittableRandom random = new SplittableRandom(seed ^ 0x5EEDL);
        final double scale = scale(targetRank);

        // a burst in every direction - the one moment in the ritual that is loud
        final int burst = (int) (150 * scale);

        for (int i = 0; i < burst; i++)
        {
            final double[] direction = onSphere(random);
            final double speed = 0.45d + random.nextDouble() * 0.5d;

            motes.add(new Mote(Kind.SPARK, 0d, 0.8d, 0d,
                    direction[0] * speed, Math.abs(direction[1]) * speed * 0.8d + 0.1d, direction[2] * speed));
        }

        // a ring racing out along the ground, twice: white, then the soul's colour behind it
        ring(motes, (int) (72 * scale), 0.55d, Kind.LIGHT, 0.1d);
        ring(motes, (int) (48 * scale), 0.35d, Kind.SOUL, 0.2d);

        // a shell of the soul's own colour, hanging round the player for a moment after
        final int shell = (int) (90 * scale);

        for (int i = 0; i < shell; i++)
        {
            final double[] direction = onSphere(random);
            final double radius = 2.2d + random.nextDouble() * 0.6d;

            motes.add(new Mote(Kind.SOUL,
                    direction[0] * radius, 1d + direction[1] * radius, direction[2] * radius, 0d, 0d, 0d));
        }

        // and the beam, fired all the way up in one go: the firmament lifting
        final int beam = (int) (60 * scale);

        for (int i = 0; i < beam; i++)
        {
            motes.add(new Mote(Kind.LIGHT,
                    (random.nextDouble() - 0.5d) * 0.4d, 1d + i * 0.6d, (random.nextDouble() - 0.5d) * 0.4d,
                    0d, 0.35d + random.nextDouble() * 0.2d, 0d));
        }

        return motes;
    }

    /**
     * Strands winding up from the pillar's base to the cap, tightening as the hold goes on. Placed
     * rather than thrown: a new point each tick along each strand, so the strand is what moves.
     */
    private static void helix(List<Mote> motes, int elapsed, double progress, double scale, double height)
    {
        final int strands = scale >= 1.4d ? 3 : 2;
        final double radius = 1.5d - 0.9d * progress;
        final int points = 1 + (int) Math.round(progress * 2d * scale);

        for (int strand = 0; strand < strands; strand++)
        {
            for (int point = 0; point < points; point++)
            {
                // a point climbs the full height of the pillar in two seconds, and each strand is
                // offset round it so they wind together rather than on top of each other
                final double climb = ((elapsed + point * 40d / points) % 40d) / 40d;
                final double angle = elapsed * 0.25d + strand * 2d * Math.PI / strands + climb * 4d;
                final double y = -height + climb * (height + 0.5d);

                motes.add(new Mote(point % 2 == 0 ? Kind.SOUL : Kind.RISE,
                        Math.cos(angle) * radius, y, Math.sin(angle) * radius, 0d, 0.02d, 0d));
            }
        }
    }

    /** Glyphs drawn in toward the cap from a ring that widens as more of the soul is gathered. */
    private static void gathering(List<Mote> motes, SplittableRandom random, double progress, double scale)
    {
        final int count = (int) Math.round((1d + 4d * progress) * scale);
        final double reach = 3d + 3d * progress;

        for (int i = 0; i < count; i++)
        {
            final double angle = random.nextDouble() * Math.PI * 2d;

            motes.add(new Mote(Kind.GLYPH, 0d, 1.2d, 0d,
                    Math.cos(angle) * reach, random.nextDouble() * 1.5d - 0.3d, Math.sin(angle) * reach));
        }
    }

    /** Light rising off the cap, the column taller and fuller as the hold goes on. */
    private static void beam(List<Mote> motes, SplittableRandom random, double progress, double scale)
    {
        final int count = (int) Math.round((1d + 5d * progress) * scale);
        final double top = 3d + 14d * progress * scale;

        for (int i = 0; i < count; i++)
        {
            motes.add(new Mote(Kind.LIGHT,
                    (random.nextDouble() - 0.5d) * 0.3d, 1d + random.nextDouble() * top, (random.nextDouble() - 0.5d) * 0.3d,
                    0d, 0.05d + 0.1d * progress, 0d));
        }
    }

    /** A flat ring thrown outward from the cap, evenly spaced so it reads as a ring and not a spray. */
    private static void ring(List<Mote> motes, int count, double speed, Kind kind, double y)
    {
        for (int i = 0; i < count; i++)
        {
            final double angle = i * 2d * Math.PI / count;

            motes.add(new Mote(kind, 0d, y, 0d, Math.cos(angle) * speed, 0d, Math.sin(angle) * speed));
        }
    }

    private static double[] onSphere(SplittableRandom random)
    {
        final double z = random.nextDouble() * 2d - 1d;
        final double angle = random.nextDouble() * Math.PI * 2d;
        final double flat = Math.sqrt(1d - z * z);

        return new double[] {flat * Math.cos(angle), z, flat * Math.sin(angle)};
    }
}
