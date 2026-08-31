/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * {@code soulhome:lane}: does the element form two separate groups with a gap between them - two
 * fence lines with a lane a player runs through, not one fence loop. Unlike {@code at_range},
 * which grades distance between two <i>different</i> named elements, this grades an element
 * against itself: {@link CellGraphs#components} splits it into connected groups first, so two
 * fence lines only count once they are not joined by a run of fence (a corner tying the two lines
 * together merges them into one component, and one component can never be "at range" from
 * itself).
 *
 * <p>{@code min_group_cells} exists so a stray fence post at the end of one line does not count as
 * its own "group" and pair off against the real line at zero distance - a group has to be big
 * enough to read as a line before it counts.
 */
public final class LaneClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:lane";
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
                ClauseParamSpec.optional("min_distance", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 3),
                ClauseParamSpec.optional("min_group_cells", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.optional("metric", ClauseParamSpec.Type.STRING, "chebyshev"));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new LaneClause(
                params.getElement("of"), params.getInt("min_distance"), params.getInt("max_distance"),
                params.getInt("min_group_cells"), params.getString("metric"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        LaneClause lane = (LaneClause) clause;
        return Map.of(
                "of", lane.of(), "min_distance", lane.minDistance(), "max_distance", lane.maxDistance(),
                "min_group_cells", lane.minGroupCells(), "metric", lane.metric());
    }
}

record LaneClause(String of, int minDistance, int maxDistance, int minGroupCells, String metric)
        implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:lane";
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

        List<List<RegionGeometry.Cell>> groups = new ArrayList<>();

        for (List<RegionGeometry.Cell> component : CellGraphs.components(cells, CellGraphs.OFFSETS_26))
        {
            if (component.size() >= this.minGroupCells)
            {
                groups.add(component);
            }
        }

        if (groups.size() < 2)
        {
            return FormResult.of(0d, "only one group of '" + this.of
                    + "' was found - a lane needs two separate groups with a gap between them");
        }

        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < groups.size(); i++)
        {
            for (int j = i + 1; j < groups.size(); j++)
            {
                bestDistance = Math.min(bestDistance, closestDistance(groups.get(i), groups.get(j)));
            }
        }

        double confidence;

        if (bestDistance < this.minDistance)
        {
            confidence = this.minDistance <= 0 ? 0d : ClauseMath.clamp01(bestDistance / this.minDistance);
        }
        else if (bestDistance > this.maxDistance)
        {
            confidence = ClauseMath.clamp01(this.maxDistance / bestDistance);
        }
        else
        {
            confidence = 1d;
        }

        String diagnostic = confidence >= 1d
                ? ""
                : "the two closest '" + this.of + "' groups are " + Math.round(bestDistance) + " apart - want "
                        + this.minDistance + "-" + this.maxDistance;

        return FormResult.of(confidence, diagnostic);
    }

    private double closestDistance(List<RegionGeometry.Cell> a, List<RegionGeometry.Cell> b)
    {
        double best = Double.MAX_VALUE;

        for (RegionGeometry.Cell cellA : a)
        {
            for (RegionGeometry.Cell cellB : b)
            {
                best = Math.min(best, ClauseMath.distance(cellA, cellB, this.metric));
            }
        }

        return best;
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' forms two separate groups, " + this.minDistance + "-" + this.maxDistance
                + " apart";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.minDistance > this.maxDistance)
        {
            errors.add("'min_distance' (" + this.minDistance + ") is greater than 'max_distance' ("
                    + this.maxDistance + "), so this shape can never be satisfied");
        }

        if (this.minGroupCells < 1)
        {
            errors.add("'min_group_cells' must be at least 1, got " + this.minGroupCells);
        }

        return errors;
    }
}
