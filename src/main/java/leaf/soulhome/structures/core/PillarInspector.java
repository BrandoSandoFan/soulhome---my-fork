/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Whether a continuous pillar stands from a soulhome's floor to its firmament (#83) - the
 * structure the ascension ritual is judged against, in the same spirit as {@link RegionScanner}:
 * a build's own shape decides the answer, not a fixed blueprint.
 *
 * <p>Deliberately its own small flood fill rather than a {@code RegionGeometry} run through
 * {@code soulhome:line}: that clause grades how straight a scattered set of matching blocks is,
 * which is the wrong question here. A pillar is not "the longest run of stone" - it is "is there
 * an unbroken path of full blocks from a 3x3 footprint at the floor to the ceiling", which is a
 * connectivity question, not a straightness one. Sharing {@link BlockVolume} and
 * {@link Passability} with the rest of region detection is what actually matters: the pillar is
 * judged the same "does this fill its cell" way as everything else in this mod, not by a second
 * definition of solid.
 *
 * <h2>Base first, then grow</h2>
 *
 * The base has to be a genuine 3x3 of full blocks - one corner post is not a pillar - so it is
 * searched for on its own before anything is flooded. Once found, the fill can taper or widen on
 * its way up (a beautiful buttressed tower and a plain square post are both pillars; see #83's
 * "shape families, not shapes"), so from the base it is a plain 6-connected flood through full
 * blocks only, exactly like {@code markOutside}'s flood in {@code RegionScanner} except it grows
 * inward through solid matter instead of outward through air.
 *
 * <p>The flood is bounded to {@code searchRadius + LATERAL_MARGIN} blocks from the anchor on X and
 * Z, independent of the soulhome's own verge - a rank V soulhome's verge is over a hundred blocks
 * wide, and letting a slab floor attached to the pillar's base flood sideways across all of it
 * would make one ritual tick cost as much as a small scan. A real pillar has no reason to spread
 * that wide near its own base, so the bound costs nothing a legitimate build would ever hit.
 */
public final class PillarInspector
{
    /** "At least 3 x 3 at its base" - #83's own words, not a config knob. */
    public static final int MIN_BASE_SIZE = 3;

    /** How much further than the base search radius the flood is allowed to spread on X/Z. */
    private static final int LATERAL_MARGIN = 6;

    private static final int[][] NEIGHBOURS_6 = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private PillarInspector()
    {
    }

    /** One (x, z) position at the pillar's top layer a player can stand above. */
    public record CapCell(int x, int z)
    {
    }

    /**
     * @param hasBase        whether a solid {@link #MIN_BASE_SIZE} square was found near the anchor
     * @param reachesCeiling whether the flood from that base reached the soulhome's top buildable layer
     * @param topY           the highest Y the flood actually reached - equal to the top buildable
     *                       layer when {@code reachesCeiling} is true, and meaningful even when it
     *                       is not, so a failure message can say by how much the pillar falls short
     * @param capCells       where a player may stand on top of the pillar, empty unless valid
     */
    public record Result(boolean hasBase, boolean reachesCeiling, int topY, List<CapCell> capCells)
    {
        public static final Result NO_BASE = new Result(false, false, Integer.MIN_VALUE, List.of());

        public boolean valid()
        {
            return this.hasBase && this.reachesCeiling;
        }
    }

    /**
     * @param anchorX,anchorZ where the Soul Anchor sits - the pillar's base must be within
     *                        {@code searchRadius} of it ("a few blocks", #83)
     * @param floorY          the soulhome's floor, inclusive - where the base is searched for
     * @param ceilingExclusive the soulhome's firmament; the top buildable layer is one below this,
     *                         matching {@link SoulBounds}'s own floor/ceiling convention
     */
    public static Result inspect(
            BlockVolume volume, int anchorX, int anchorZ, int floorY, int ceilingExclusive, int searchRadius)
    {
        final int topY = ceilingExclusive - 1;

        if (topY < floorY)
        {
            return Result.NO_BASE;
        }

        final int[] base = findBase(volume, anchorX, anchorZ, floorY, searchRadius);

        if (base == null)
        {
            return Result.NO_BASE;
        }

        final int lateralMin = -searchRadius - LATERAL_MARGIN;
        final int lateralMax = searchRadius + LATERAL_MARGIN;

        Set<Long> visited = new HashSet<>();
        Deque<int[]> pending = new ArrayDeque<>();

        for (int dx = 0; dx < MIN_BASE_SIZE; dx++)
        {
            for (int dz = 0; dz < MIN_BASE_SIZE; dz++)
            {
                final int x = base[0] + dx;
                final int z = base[1] + dz;
                offer(visited, pending, x, floorY, z);
            }
        }

        int highestY = floorY;

        while (!pending.isEmpty())
        {
            final int[] cell = pending.poll();
            highestY = Math.max(highestY, cell[1]);

            for (int[] step : NEIGHBOURS_6)
            {
                final int nx = cell[0] + step[0];
                final int ny = cell[1] + step[1];
                final int nz = cell[2] + step[2];

                if (ny < floorY || ny > topY)
                {
                    continue;
                }

                if (nx - anchorX < lateralMin || nx - anchorX > lateralMax
                        || nz - anchorZ < lateralMin || nz - anchorZ > lateralMax)
                {
                    continue;
                }

                if (!volume.passabilityAt(nx, ny, nz).isFullBlock())
                {
                    continue;
                }

                offer(visited, pending, nx, ny, nz);
            }
        }

        if (highestY < topY)
        {
            return new Result(true, false, highestY, List.of());
        }

        List<CapCell> capCells = new ArrayList<>();

        for (long packed : visited)
        {
            if (unpackY(packed) == topY)
            {
                capCells.add(new CapCell(unpackX(packed), unpackZ(packed)));
            }
        }

        return new Result(true, true, topY, List.copyOf(capCells));
    }

    /**
     * The top-left corner of the first {@link #MIN_BASE_SIZE} square of full blocks found at
     * {@code floorY} within {@code searchRadius} of the anchor, swept in ascending X then Z for a
     * deterministic answer when more than one qualifies.
     */
    private static int[] findBase(BlockVolume volume, int anchorX, int anchorZ, int floorY, int searchRadius)
    {
        final int span = MIN_BASE_SIZE - 1;

        for (int x0 = anchorX - searchRadius; x0 <= anchorX + searchRadius - span; x0++)
        {
            for (int z0 = anchorZ - searchRadius; z0 <= anchorZ + searchRadius - span; z0++)
            {
                if (isSolidSquare(volume, x0, floorY, z0))
                {
                    return new int[] {x0, z0};
                }
            }
        }

        return null;
    }

    private static boolean isSolidSquare(BlockVolume volume, int x0, int y, int z0)
    {
        for (int dx = 0; dx < MIN_BASE_SIZE; dx++)
        {
            for (int dz = 0; dz < MIN_BASE_SIZE; dz++)
            {
                if (!volume.passabilityAt(x0 + dx, y, z0 + dz).isFullBlock())
                {
                    return false;
                }
            }
        }

        return true;
    }

    private static void offer(Set<Long> visited, Deque<int[]> pending, int x, int y, int z)
    {
        if (visited.add(pack(x, y, z)))
        {
            pending.add(new int[] {x, y, z});
        }
    }

    // packed with a generous offset and 21 bits per axis - comfortably wider than any soulhome's
    // verge, which is what keeps this an encoding rather than a hash that could collide
    private static final int OFFSET = 1 << 20;

    private static long pack(int x, int y, int z)
    {
        return ((long) (x + OFFSET) << 42) | ((long) (y + OFFSET) << 21) | (long) (z + OFFSET);
    }

    private static int unpackX(long packed)
    {
        return (int) (packed >> 42) - OFFSET;
    }

    private static int unpackZ(long packed)
    {
        return (int) (packed & 0x1FFFFF) - OFFSET;
    }

    private static int unpackY(long packed)
    {
        return (int) ((packed >> 21) & 0x1FFFFF) - OFFSET;
    }
}
