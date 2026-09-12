/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:clearance}: is there room to move? A training yard needs somewhere to train, a
 * track needs a lane to run down, and a hall is a hall because of the floor around the table. See
 * #168.
 *
 * <p><b>This is the counterweight to the density multiplier.</b> Scoring otherwise treats empty
 * space as an absence - a room's score is scaled down by how sparsely filled it is - and for these
 * three rooms the empty space <i>is</i> the point. Nothing in the vocabulary before this could say
 * so, which is why a yard packed wall-to-wall with equipment out-scored one with a floor to fight
 * on.
 *
 * <p>No {@code of} element, like {@link VerticalityClauseType}: this is not "where does this block
 * sit" but "what room does the build leave", so it reads the region's own extent and the clearance
 * index rather than a matcher's cells.
 *
 * <h2>What is measured</h2>
 *
 * A cell is <b>standable</b> when the cell itself is open, the cell below it is solid, and the
 * {@code headroom} cells from it upward are open - a floor to stand on and space to stand in. The
 * clause grades the largest contiguous group of standable cells, counted <b>one cell per standing
 * position</b>: a 5x5 room under a 3-high ceiling measures 25, not 75, because it is the floor a
 * player can occupy that matters and not the air over their head. ({@code min_volume} and
 * {@code ideal_volume} keep #168's parameter names; the unit is a standing position.)
 *
 * <p>Contiguity is 4-connected horizontally, allowing a one-cell step up or down, so a sunken pit
 * or a stepped terrace still reads as one space a player moves through while two rooms either side
 * of a wall do not. Diagonals are excluded for the reason {@link PlatformClauseType} excludes them:
 * a checkerboard of standing spots is fully diagonally connected and is not somewhere to move.
 *
 * <h2>It reads the index #29 already pays for</h2>
 *
 * {@link RegionGeometry#isBlocked} is written by {@link RegionScanner} when {@code indexClearance}
 * is set, which {@code ArchetypeSignals#needsClearance} turns on exactly when some loaded form asks
 * for it - {@link #needsClearance()} on the clause below is what puts this clause in that set, so a
 * pack that never asks still pays nothing. There is no second index.
 *
 * <p><b>The index only covers cells the region took in</b>, and that is why this clause is the one
 * most exposed to a region that is a ring rather than a solid (see {@code fillInteriorHoles} in
 * {@link RegionScanner}). An unfilled infield's ground would be absent from the index, so the floor
 * under it would read as open air, and a rail circuit with a perfectly runnable infield would grade
 * as having nowhere to run. {@code ClearanceClauseTest} pins that case.
 *
 * <p>One consequence worth stating for an {@link RegionType#OPEN} region, whose bounds are a box
 * around a cluster rather than a shell around a room: a cell inside that box which the cluster did
 * not take in reads as open, so it can only ever be counted where it stands directly on the
 * cluster's own material. That is the right answer - standing on top of this build's own floor, or
 * its fence - rather than crediting a build with the sky above it.
 */
public final class ClearanceClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:clearance";
    }

    @Override
    public Kind kind()
    {
        return Kind.SHAPE;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.optional("min_volume", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.required("ideal_volume", ClauseParamSpec.Type.INT),
                ClauseParamSpec.optional("headroom", ClauseParamSpec.Type.INT, 2));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new ClearanceClause(
                params.getInt("min_volume"), params.getInt("ideal_volume"), params.getInt("headroom"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        ClearanceClause clearance = (ClearanceClause) clause;
        return Map.of(
                "min_volume", clearance.minVolume(),
                "ideal_volume", clearance.idealVolume(),
                "headroom", clearance.headroom());
    }
}

record ClearanceClause(int minVolume, int idealVolume, int headroom) implements FormClause
{
    /** Horizontal steps only; the vertical give is {@link #STEP} on top of them. */
    private static final int[][] STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /** How far a standing position may sit above or below its neighbour and still be the same space. */
    private static final int STEP = 1;

    @Override
    public String typeId()
    {
        return "soulhome:clearance";
    }

    /**
     * True, which is the whole of this clause's plumbing: it puts every archetype declaring it into
     * {@code ArchetypeSignals#needsClearance}, so the scanner records the index this reads.
     */
    @Override
    public boolean needsClearance()
    {
        return true;
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        RegionBounds bounds = geometry.bounds().orElse(null);

        if (bounds == null)
        {
            if (geometry.isEmpty())
            {
                return FormResult.of(0d, "nothing was indexed to measure the room's space from");
            }

            bounds = ClauseMath.boundingBox(geometry.cells(), 1);
        }

        // Absent clearance data is not open space. A geometry with nothing solid in it cannot
        // distinguish "a room with a floor" from "a hand-built geometry nobody tracked solidity
        // for", and crediting the second would hand a form full marks for an index that was never
        // written - the opposite of across's require_clear, which treats an untracked index as
        // clear because a check nobody paid for should not start failing builds.
        if (!geometry.hasClearanceData())
        {
            return FormResult.of(0d, "the room's solid blocks were not tracked, so its floor space is unknown");
        }

        Set<Position> standable = standablePositions(bounds, geometry);
        final int largest = largestContiguous(standable);

        final double confidence = largest < this.minVolume
                ? 0d
                : ClauseMath.clamp01((double) largest / this.idealVolume);

        return FormResult.of(confidence, diagnostic(largest));
    }

    /**
     * Every cell in the region's extent a player could stand in: open itself, solid underfoot, and
     * clear for {@link #headroom} cells upward. The floor check is what keeps a cavity inside a
     * thick wall - open, and with nothing below it either - from reading as somewhere to stand.
     */
    private Set<Position> standablePositions(RegionBounds bounds, RegionGeometry geometry)
    {
        Set<Position> standable = new HashSet<>();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
        {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
            {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                {
                    if (!geometry.isBlocked(x, y - 1, z))
                    {
                        continue;
                    }

                    if (hasHeadroom(geometry, x, y, z))
                    {
                        standable.add(new Position(x, y, z));
                    }
                }
            }
        }

        return standable;
    }

    private boolean hasHeadroom(RegionGeometry geometry, int x, int y, int z)
    {
        for (int above = 0; above < this.headroom; above++)
        {
            if (geometry.isBlocked(x, y + above, z))
            {
                return false;
            }
        }

        return true;
    }

    private int largestContiguous(Set<Position> standable)
    {
        Set<Position> visited = new HashSet<>();
        int largest = 0;

        for (Position start : standable)
        {
            if (!visited.add(start))
            {
                continue;
            }

            int size = 0;
            Deque<Position> stack = new ArrayDeque<>();
            stack.push(start);

            while (!stack.isEmpty())
            {
                Position at = stack.pop();
                size++;

                for (int[] step : STEPS)
                {
                    for (int dy = -STEP; dy <= STEP; dy++)
                    {
                        Position neighbour = new Position(at.x() + step[0], at.y() + dy, at.z() + step[1]);

                        if (standable.contains(neighbour) && visited.add(neighbour))
                        {
                            stack.push(neighbour);
                        }
                    }
                }
            }

            largest = Math.max(largest, size);
        }

        return largest;
    }

    private String diagnostic(int largest)
    {
        if (largest >= this.idealVolume)
        {
            return "";
        }

        if (largest < this.minVolume)
        {
            return largest == 0
                    ? "there is nowhere in here to stand, let alone move"
                    : "the largest clear floor is " + largest + " blocks - too cramped to move in";
        }

        return "the largest clear floor is " + largest + " blocks, short of the " + this.idealVolume
                + " wanted";
    }

    @Override
    public String describe()
    {
        return "there is room to move - at least " + this.minVolume + " blocks of clear floor with "
                + this.headroom + " of headroom, best at " + this.idealVolume;
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.idealVolume < 1)
        {
            errors.add("'ideal_volume' must be at least 1, got " + this.idealVolume);
        }

        if (this.minVolume < 0)
        {
            errors.add("'min_volume' must not be negative, got " + this.minVolume);
        }

        if (this.headroom < 1)
        {
            errors.add("'headroom' must be at least 1 - a standing position needs its own cell, got "
                    + this.headroom);
        }

        return errors;
    }

    private record Position(int x, int y, int z)
    {
    }
}
