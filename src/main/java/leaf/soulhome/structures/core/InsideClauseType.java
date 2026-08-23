/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code inside}: does {@code of} fall within the footprint {@code to} traces out - ice filling
 * the loop a rail circuit encloses, not ice sitting outside it. Graded by footprint containment
 * rather than distance: an {@code of} cell counts if it projects onto {@code to}'s own XZ footprint,
 * sits within {@code max_gap} of it, or is enclosed by it - a flood fill from the cell that fails
 * to escape the region's bounds without crossing {@code to}'s footprint. See #30.
 */
public final class InsideClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "inside";
    }

    @Override
    public Kind kind()
    {
        return Kind.RELATION;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("max_gap", ClauseParamSpec.Type.INT, 1));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new InsideClause(params.getElement("of"), params.getElement("to"), params.getInt("max_gap"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        InsideClause inside = (InsideClause) clause;
        return Map.of("of", inside.of(), "to", inside.to(), "max_gap", inside.maxGap());
    }
}

record InsideClause(String of, String to, int maxGap) implements FormClause
{
    @Override
    public String typeId()
    {
        return "inside";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher ofMatcher = elements.get(this.of);
        BlockMatcher toMatcher = elements.get(this.to);

        if (ofMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        if (toMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.to));
        }

        List<RegionGeometry.Cell> ofCells = geometry.cellsMatching(ofMatcher);
        List<RegionGeometry.Cell> toCells = geometry.cellsMatching(toMatcher);

        if (ofCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        if (toCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.to));
        }

        Set<XZ> footprint = new HashSet<>();

        for (RegionGeometry.Cell cell : toCells)
        {
            footprint.add(new XZ(cell.x(), cell.z()));
        }

        RegionBounds bounds = geometry.bounds().orElseGet(() -> boundingBoxOf(ofCells, toCells));

        Map<XZ, Boolean> memo = new HashMap<>();
        int satisfied = 0;

        for (RegionGeometry.Cell of : ofCells)
        {
            if (isInside(new XZ(of.x(), of.z()), footprint, bounds, memo))
            {
                satisfied++;
            }
        }

        final int total = ofCells.size();
        double confidence = ClauseMath.clamp01((double) satisfied / total);
        String diagnostic = ClauseMath.coverageDiagnostic(satisfied, total, this.of, "falls inside", this.to);

        return FormResult.of(confidence, diagnostic);
    }

    private boolean isInside(XZ point, Set<XZ> footprint, RegionBounds bounds, Map<XZ, Boolean> memo)
    {
        if (footprint.contains(point))
        {
            return true;
        }

        for (int dx = -this.maxGap; dx <= this.maxGap; dx++)
        {
            for (int dz = -this.maxGap; dz <= this.maxGap; dz++)
            {
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= this.maxGap
                        && footprint.contains(new XZ(point.x() + dx, point.z() + dz)))
                {
                    return true;
                }
            }
        }

        return memo.computeIfAbsent(point, p -> isEnclosed(p, footprint, bounds));
    }

    /**
     * Flood fill in the XZ plane from {@code start}, blocked by {@code footprint}: if the fill can
     * reach the edge of {@code bounds} without crossing {@code footprint}, {@code start} can "escape"
     * and is not enclosed; if the fill exhausts every reachable cell first, it is boxed in.
     */
    private static boolean isEnclosed(XZ start, Set<XZ> footprint, RegionBounds bounds)
    {
        Set<XZ> visited = new HashSet<>();
        Deque<XZ> stack = new ArrayDeque<>();
        visited.add(start);
        stack.push(start);

        while (!stack.isEmpty())
        {
            XZ point = stack.pop();

            if (point.x() <= bounds.minX() || point.x() >= bounds.maxX()
                    || point.z() <= bounds.minZ() || point.z() >= bounds.maxZ())
            {
                return false;
            }

            for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            {
                XZ neighbour = new XZ(point.x() + offset[0], point.z() + offset[1]);

                if (!footprint.contains(neighbour) && visited.add(neighbour))
                {
                    stack.push(neighbour);
                }
            }
        }

        return true;
    }

    private static RegionBounds boundingBoxOf(List<RegionGeometry.Cell> ofCells, List<RegionGeometry.Cell> toCells)
    {
        List<RegionGeometry.Cell> all = new ArrayList<>(ofCells);
        all.addAll(toCells);
        return ClauseMath.boundingBox(all, 2);
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' is inside '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }

    private record XZ(int x, int z)
    {
    }
}
