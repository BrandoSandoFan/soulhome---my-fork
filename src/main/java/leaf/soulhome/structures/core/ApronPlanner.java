/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link GroundSurvey} into an {@link ApronPlan}: which void columns become ground when a
 * soulhome ascends, and which existing ground column each of them is a continuation of (#158/#159).
 *
 * <p>Read the decision on #159 before changing any of this. In summary:
 *
 * <ol>
 *   <li><b>The apron is a band following the island's outline</b>, not a square and not a fill. A
 *       distance transform runs outward from every existing ground column; a void column joins the
 *       apron when it is inside {@link TerrainGrowthSettings#bandWidth} of one. That is what keeps
 *       the silhouette the player already knows - a soul with a bay still has a bay.</li>
 *   <li><b>A hard square clamps it.</b> {@link TerrainGrowthSettings#groundLimit} lags the verge,
 *       so open void is always left between the apron and the wall. That gap is the point rather
 *       than a shortfall, and the guide book says so in as many words, because a player who sees
 *       void inside their own walls will otherwise assume growth failed.</li>
 *   <li><b>Nothing built is ever generated into, or against.</b> Every {@link GroundSurvey.Kind#BUILT}
 *       column keeps a clearance moat around it, and only a {@link GroundSurvey.Kind#VOID} column is
 *       ever a candidate. Generating too little ground is a mild disappointment; generating over
 *       someone's tower is unrecoverable, and the margin is tuned accordingly (#160).</li>
 *   <li><b>It is deterministic.</b> The transform is two raster passes in index order and the edge
 *       jitter is a pure hash of the soul's own id, so the same soul at the same rank plans the
 *       same ground every time - which is what lets a regenerated chunk be identical to the one it
 *       replaces.</li>
 * </ol>
 *
 * <h2>Why a distance transform rather than a flood fill</h2>
 *
 * The obvious implementation is a breadth-first flood outward from the island, which gives a
 * geodesic band. It also gives a diamond: four-neighbour steps measure Manhattan distance, and a
 * Manhattan offset of a roughly round island has corners a player can see. A two-pass chamfer
 * transform with 3/4 weights approximates Euclidean distance closely enough that the band reads as
 * an offset coastline, costs two linear sweeps rather than a queue, and propagates the source
 * column along with the distance in the same passes.
 *
 * <p>It also crosses obstacles, where a flood would be shadowed by them. That is the better answer
 * here and is the opposite of the call {@code RegionScanner} makes about cluster reach: a wall
 * between two builds is a boundary because the player put it there to divide them, but a bridge a
 * player threw across the verge is not a request that the ground behind it stay void forever. The
 * moat keeps the apron off the bridge; it does not stop the island continuing past it.
 */
public final class ApronPlanner
{
    /** Orthogonal step in the chamfer transform. Distances are therefore in thirds of a block. */
    private static final int STEP_ORTHOGONAL = 3;

    /** Diagonal step. 4/3 is the usual rational stand-in for the square root of two. */
    private static final int STEP_DIAGONAL = 4;

    /** Far enough that no real distance reaches it, and low enough that adding a step cannot overflow. */
    private static final int UNREACHED = Integer.MAX_VALUE / 4;

    private ApronPlanner()
    {
    }

    /**
     * Plan one run of growth.
     *
     * @param survey          what every column in the box currently holds
     * @param settings        the growth knobs
     * @param rank            the rank being grown to
     * @param grownRank       the rank growth last completed - the band is measured from here, so a
     *                        soulhome catching up several ranks at once grows one wider band rather
     *                        than several
     * @param vergeHalfExtent the wall for {@code rank}, which the ground limit is held inside of
     * @param excluded        a region no ground may ever appear in - the legacy grant (#80), whose
     *                        whole point is that it is full of build nobody's box knows about. Null
     *                        for a soulhome created after the box existed
     * @param soulSeed        the soul's own identity, so its coastline is its own and is the same
     *                        every time
     *
     *                        <p>Catching up several ranks in one band reaches a block or two
     *                        further than climbing them one at a time would have, because the edge
     *                        jitter is subtracted once rather than once per rank. Left as it is
     *                        rather than corrected: the difference is inside one rank's band, the
     *                        ground limit bounds it either way, and the alternative is re-surveying
     *                        between every rank of a catch-up to reproduce an arbitrary coastline
     *                        nobody ever saw. {@code TerrainGrowthCorpusTest} pins how far apart
     *                        the two are allowed to drift.
     */
    public static ApronPlan plan(
            GroundSurvey survey, TerrainGrowthSettings settings, int rank, int grownRank, int vergeHalfExtent,
            RegionBounds excluded, long soulSeed)
    {
        final int band = settings.bandWidth(rank, grownRank);
        final int limit = settings.groundLimit(rank, vergeHalfExtent);

        if (!settings.enabled() || band <= 0)
        {
            return ApronPlan.NOTHING;
        }

        final int sizeX = survey.sizeX();
        final int sizeZ = survey.sizeZ();
        final int cells = sizeX * sizeZ;

        // the band, measured from the island's own ground, carrying the source column with it
        final int[] bandDistance = new int[cells];
        final int[] bandSource = new int[cells];

        // the moat, measured from anything built. Weights of one on both steps make this exact
        // Chebyshev distance, which is the shape a "keep three blocks clear" margin should be
        final int[] builtDistance = new int[cells];

        seed(survey, bandDistance, bandSource, builtDistance);
        sweep(bandDistance, bandSource, builtDistance, sizeX, sizeZ);

        final List<ApronPlan.Column> planned = new ArrayList<>();

        for (int dx = 0; dx < sizeX; dx++)
        {
            final int x = survey.minX() + dx;

            for (int dz = 0; dz < sizeZ; dz++)
            {
                final int z = survey.minZ() + dz;
                final int index = dx * sizeZ + dz;

                if (!isCandidate(survey, x, z, limit, excluded))
                {
                    continue;
                }

                if (builtDistance[index] <= settings.clearanceMargin())
                {
                    // inside somebody's moat
                    continue;
                }

                if (bandDistance[index] >= UNREACHED)
                {
                    // nothing to grow from - a soulhome with no ground at all, which a survey that
                    // could not read its chunks also looks like
                    continue;
                }

                final int reach = band - jitterAt(soulSeed, x, z, settings.edgeJitter());

                if (reach <= 0 || bandDistance[index] > reach * STEP_ORTHOGONAL)
                {
                    continue;
                }

                final int source = bandSource[index];
                final int sourceX = survey.minX() + source / sizeZ;
                final int sourceZ = survey.minZ() + source % sizeZ;

                planned.add(new ApronPlan.Column(x, z, survey.surfaceAt(sourceX, sourceZ), sourceX, sourceZ));
            }
        }

        return new ApronPlan(planned, limit, band);
    }

    /**
     * How far a soulhome's ground currently reaches from the origin, as a half-extent - the number
     * {@code /soulhome ascent} reports beside how far the walls do (#162). Chebyshev rather than
     * Euclidean so it is the same kind of measurement the verge already is, and so the two numbers
     * can be read against each other.
     *
     * @return 0 for a soulhome with no ground the survey could see
     */
    public static int groundReach(GroundSurvey survey)
    {
        int reach = 0;

        for (int x = survey.minX(); x <= survey.maxX(); x++)
        {
            for (int z = survey.minZ(); z <= survey.maxZ(); z++)
            {
                if (survey.kindAt(x, z) != GroundSurvey.Kind.GROUND)
                {
                    continue;
                }

                reach = Math.max(reach, Math.max(Math.abs(x), Math.abs(z)));
            }
        }

        return reach;
    }

    private static boolean isCandidate(GroundSurvey survey, int x, int z, int limit, RegionBounds excluded)
    {
        if (survey.kindAt(x, z) != GroundSurvey.Kind.VOID)
        {
            return false;
        }

        if (Math.abs(x) > limit || Math.abs(z) > limit)
        {
            return false;
        }

        // the legacy grant is build the box never knew about, so its whole footprint is off limits
        // rather than merely moated
        return excluded == null
                || x < excluded.minX() || x > excluded.maxX()
                || z < excluded.minZ() || z > excluded.maxZ();
    }

    private static void seed(GroundSurvey survey, int[] bandDistance, int[] bandSource, int[] builtDistance)
    {
        final int sizeZ = survey.sizeZ();

        for (int dx = 0; dx < survey.sizeX(); dx++)
        {
            for (int dz = 0; dz < sizeZ; dz++)
            {
                final int index = dx * sizeZ + dz;
                final GroundSurvey.Kind kind = survey.kindAt(survey.minX() + dx, survey.minZ() + dz);

                bandDistance[index] = kind == GroundSurvey.Kind.GROUND ? 0 : UNREACHED;
                bandSource[index] = index;
                builtDistance[index] = kind == GroundSurvey.Kind.BUILT ? 0 : UNREACHED;
            }
        }
    }

    /**
     * The two raster passes. Forward reads only cells already settled by this pass, backward only
     * cells already settled by that one, which between them is what makes a chamfer transform
     * exact enough in two sweeps rather than needing a queue.
     *
     * <p>A tie is left with whichever source reached it first in forward-scan order - relaxation is
     * on a strict improvement - so two equally close ground columns resolve the same way every run.
     * That is the determinism {@code SoulRegion.identityHash} and a regenerated chunk both depend on.
     */
    private static void sweep(int[] bandDistance, int[] bandSource, int[] builtDistance, int sizeX, int sizeZ)
    {
        for (int dx = 0; dx < sizeX; dx++)
        {
            for (int dz = 0; dz < sizeZ; dz++)
            {
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, -1, -1);
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, -1, 0);
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, -1, 1);
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, 0, -1);
            }
        }

        for (int dx = sizeX - 1; dx >= 0; dx--)
        {
            for (int dz = sizeZ - 1; dz >= 0; dz--)
            {
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, 1, 1);
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, 1, 0);
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, 1, -1);
                relax(bandDistance, bandSource, builtDistance, sizeX, sizeZ, dx, dz, 0, 1);
            }
        }
    }

    private static void relax(
            int[] bandDistance, int[] bandSource, int[] builtDistance, int sizeX, int sizeZ, int dx, int dz,
            int offsetX, int offsetZ)
    {
        final int neighbourX = dx + offsetX;
        final int neighbourZ = dz + offsetZ;

        if (neighbourX < 0 || neighbourX >= sizeX || neighbourZ < 0 || neighbourZ >= sizeZ)
        {
            return;
        }

        final int here = dx * sizeZ + dz;
        final int there = neighbourX * sizeZ + neighbourZ;
        final boolean diagonal = offsetX != 0 && offsetZ != 0;

        final int bandStep = diagonal ? STEP_DIAGONAL : STEP_ORTHOGONAL;
        final int candidate = bandDistance[there] + bandStep;

        if (candidate < bandDistance[here])
        {
            bandDistance[here] = candidate;
            bandSource[here] = bandSource[there];
        }

        // Chebyshev: a diagonal step costs exactly what an orthogonal one does
        final int builtCandidate = builtDistance[there] + 1;

        if (builtCandidate < builtDistance[here])
        {
            builtDistance[here] = builtCandidate;
        }
    }

    /**
     * How many blocks short of the full band this column's reach falls, in {@code [0, amplitude]}.
     *
     * <p>Quantised to four-block squares rather than computed per column, because per-column noise
     * is static and four-block features are a coastline. Keyed on the soul's own id so two players
     * who ascend to the same rank do not grow the same edge.
     */
    private static int jitterAt(long soulSeed, int x, int z, int amplitude)
    {
        if (amplitude <= 0)
        {
            return 0;
        }

        // arithmetic shift rather than division: it floors on negative coordinates, so the squares
        // are the same size either side of the origin instead of an eight-wide one straddling it
        long hash = soulSeed ^ ((long) (x >> 2) * 0x9E3779B97F4A7C15L) ^ ((long) (z >> 2) * 0xC2B2AE3D27D4EB4FL);

        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;

        return (int) Math.floorMod(hash, (long) amplitude + 1L);
    }
}
