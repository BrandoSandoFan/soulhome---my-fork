/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:platform}: is the element a contiguous floor, or scattered blocks that happen
 * to share a room - a 5x5 slab reads as one platform, the same 25 blocks spread as singles do not.
 * Graded by the largest contiguous XZ footprint, tolerating a small step in height between
 * footprint-adjacent cells so a terraced or gently sloped surface still counts as one surface. See
 * #32.
 */
public final class PlatformClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:platform";
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
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("min_area", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.required("ideal_area", ClauseParamSpec.Type.INT),
                ClauseParamSpec.optional("height_tolerance", ClauseParamSpec.Type.INT, 1));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new PlatformClause(
                params.getElement("of"), params.getInt("min_area"), params.getInt("ideal_area"),
                params.getInt("height_tolerance"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        PlatformClause platform = (PlatformClause) clause;
        return Map.of(
                "of", platform.of(), "min_area", platform.minArea(), "ideal_area", platform.idealArea(),
                "height_tolerance", platform.heightTolerance());
    }
}

record PlatformClause(String of, int minArea, int idealArea, int heightTolerance) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:platform";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher matcher = elements.get(this.of);

        if (matcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        List<RegionGeometry.Cell> cells = geometry.cellsMatching(matcher);

        if (cells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        // one XZ footprint cell per column - a platform is one block thick per column, so the first
        // cell seen at a column stands in for it
        Map<XZ, Integer> footprintY = new LinkedHashMap<>();

        for (RegionGeometry.Cell cell : cells)
        {
            footprintY.putIfAbsent(new XZ(cell.x(), cell.z()), cell.y());
        }

        int largest = largestContiguousFootprint(footprintY);
        double confidence = largest < this.minArea ? 0d : ClauseMath.clamp01((double) largest / this.idealArea);

        return FormResult.of(confidence, diagnostic(largest));
    }

    private int largestContiguousFootprint(Map<XZ, Integer> footprintY)
    {
        Set<XZ> visited = new HashSet<>();
        int largest = 0;

        for (XZ start : footprintY.keySet())
        {
            if (!visited.add(start))
            {
                continue;
            }

            int size = 0;
            Deque<XZ> stack = new ArrayDeque<>();
            stack.push(start);

            while (!stack.isEmpty())
            {
                XZ point = stack.pop();
                size++;

                // 4-connected, not 8: a diagonal touch is not a floor. Two blocks that only meet
                // corner-to-corner is exactly the "scattered singles" case this clause exists to
                // reject - a checkerboard is fully diagonally connected, so counting diagonals let
                // it read as one contiguous field. See #98.
                for (int[] offset : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                {
                    XZ neighbour = new XZ(point.x() + offset[0], point.z() + offset[1]);
                    Integer neighbourY = footprintY.get(neighbour);

                    if (neighbourY != null
                            && Math.abs(neighbourY - footprintY.get(point)) <= this.heightTolerance
                            && visited.add(neighbour))
                    {
                        stack.push(neighbour);
                    }
                }
            }

            largest = Math.max(largest, size);
        }

        return largest;
    }

    private String diagnostic(int largest)
    {
        if (largest >= this.idealArea)
        {
            return "";
        }

        if (largest < this.minArea)
        {
            return "'" + this.of + "' is scattered - the largest contiguous patch is only " + largest;
        }

        return "the largest contiguous patch of '" + this.of + "' is " + largest + ", short of the "
                + this.idealArea + " wanted";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' forms a contiguous platform";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.idealArea < 1)
        {
            errors.add("'ideal_area' must be at least 1, got " + this.idealArea);
        }

        return errors;
    }

    private record XZ(int x, int z)
    {
    }
}
