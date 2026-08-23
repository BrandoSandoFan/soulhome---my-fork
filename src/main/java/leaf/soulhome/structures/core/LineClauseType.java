/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code soulhome:line}: does the element run, rather than sit - a fence in a row reads as a
 * boundary, the same fence posts bunched into a blob do not. Graded as the longest run along
 * whichever of X, Y or Z turns out to be the element's own axis of travel, tolerating a small
 * perpendicular wobble at each step so a single-cell jog does not split an otherwise straight run
 * in two. See #32.
 *
 * <h2>Longest run, not widest box</h2>
 *
 * The run is found by a small dynamic program per candidate axis rather than by measuring a
 * bounding box: cells are grouped into layers by their coordinate on that axis, and for each cell
 * the longest chain ending there is 1 plus the best compatible chain in the immediately preceding
 * layer - "compatible" meaning within {@code max_deviation} on the other two axes. A layer that is
 * not consecutive with the one before it (a gap along the axis) cannot extend a chain, so this
 * naturally tells a genuine run apart from a blob that merely happens to be no wider than the
 * tolerance in one direction: a blob's layers are short and do not chain into anything long, while
 * a real run's cross-section is one or two cells per layer and chains all the way through.
 */
public final class LineClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:line";
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
                ClauseParamSpec.optional("min_run", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.required("ideal_run", ClauseParamSpec.Type.INT),
                ClauseParamSpec.optional("max_deviation", ClauseParamSpec.Type.INT, 1));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new LineClause(
                params.getElement("of"), params.getInt("min_run"), params.getInt("ideal_run"),
                params.getInt("max_deviation"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        LineClause line = (LineClause) clause;
        return Map.of(
                "of", line.of(), "min_run", line.minRun(), "ideal_run", line.idealRun(),
                "max_deviation", line.maxDeviation());
    }
}

record LineClause(String of, int minRun, int idealRun, int maxDeviation) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:line";
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

        int longestRun = Math.max(longestRunAlong(cells, Axis.X), Math.max(longestRunAlong(cells, Axis.Y), longestRunAlong(cells, Axis.Z)));
        double confidence = longestRun < this.minRun ? 0d : ClauseMath.clamp01((double) longestRun / this.idealRun);

        return FormResult.of(confidence, diagnostic(longestRun));
    }

    private int longestRunAlong(List<RegionGeometry.Cell> cells, Axis axis)
    {
        // layers in ascending order of the primary coordinate - a TreeMap gives that for free
        Map<Integer, List<RegionGeometry.Cell>> layers = new TreeMap<>();

        for (RegionGeometry.Cell cell : cells)
        {
            layers.computeIfAbsent(axis.primary(cell), key -> new ArrayList<>()).add(cell);
        }

        Map<RegionGeometry.Cell, Integer> chainEndingAt = new HashMap<>();
        Integer previousCoordinate = null;
        List<RegionGeometry.Cell> previousLayer = List.of();
        int longest = 0;

        for (Map.Entry<Integer, List<RegionGeometry.Cell>> entry : layers.entrySet())
        {
            final int coordinate = entry.getKey();
            final boolean contiguous = previousCoordinate != null && coordinate == previousCoordinate + 1;

            for (RegionGeometry.Cell cell : entry.getValue())
            {
                int chain = 1;

                if (contiguous)
                {
                    for (RegionGeometry.Cell previous : previousLayer)
                    {
                        if (axis.perpendicularDeviation(cell, previous) <= this.maxDeviation)
                        {
                            chain = Math.max(chain, chainEndingAt.get(previous) + 1);
                        }
                    }
                }

                chainEndingAt.put(cell, chain);
                longest = Math.max(longest, chain);
            }

            previousCoordinate = coordinate;
            previousLayer = entry.getValue();
        }

        return longest;
    }

    private String diagnostic(int longestRun)
    {
        if (longestRun >= this.idealRun)
        {
            return "";
        }

        if (longestRun < this.minRun)
        {
            return "'" + this.of + "' is scattered rather than run in a line - the longest run is only " + longestRun;
        }

        return "the longest run of '" + this.of + "' is " + longestRun + ", short of the " + this.idealRun + " wanted";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' runs in a line";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.idealRun < 1)
        {
            errors.add("'ideal_run' must be at least 1, got " + this.idealRun);
        }

        return errors;
    }

    /** Which coordinate is "along the run" and which two are "across it", per candidate axis. */
    private enum Axis
    {
        X
                {
                    @Override
                    int primary(RegionGeometry.Cell cell)
                    {
                        return cell.x();
                    }

                    @Override
                    int perpendicularDeviation(RegionGeometry.Cell a, RegionGeometry.Cell b)
                    {
                        return Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z()));
                    }
                },
        Y
                {
                    @Override
                    int primary(RegionGeometry.Cell cell)
                    {
                        return cell.y();
                    }

                    @Override
                    int perpendicularDeviation(RegionGeometry.Cell a, RegionGeometry.Cell b)
                    {
                        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
                    }
                },
        Z
                {
                    @Override
                    int primary(RegionGeometry.Cell cell)
                    {
                        return cell.z();
                    }

                    @Override
                    int perpendicularDeviation(RegionGeometry.Cell a, RegionGeometry.Cell b)
                    {
                        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
                    }
                };

        abstract int primary(RegionGeometry.Cell cell);

        abstract int perpendicularDeviation(RegionGeometry.Cell a, RegionGeometry.Cell b);
    }
}
