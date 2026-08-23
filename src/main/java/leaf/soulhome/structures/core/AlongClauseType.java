/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code along}: does {@code of} run parallel to {@code to} over a distance - a fence running the
 * length of a track, not just brushing past it once. A cell within {@code max_distance} of {@code
 * to} only counts once it belongs to a contiguous run of such cells at least {@code min_run} long,
 * so a single stray hit near {@code to} does not count the way an actual run does. See the
 * relations table in #29.
 */
public final class AlongClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "along";
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
                ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 2),
                ClauseParamSpec.optional("min_run", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new AlongClause(
                params.getElement("of"), params.getElement("to"),
                params.getInt("max_distance"), params.getInt("min_run"), params.getDouble("coverage"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        AlongClause along = (AlongClause) clause;
        return Map.of(
                "of", along.of(), "to", along.to(),
                "max_distance", along.maxDistance(), "min_run", along.minRun(), "coverage", along.coverage());
    }
}

record AlongClause(String of, String to, int maxDistance, int minRun, double coverage) implements FormClause
{
    @Override
    public String typeId()
    {
        return "along";
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

        List<RegionGeometry.Cell> near = new ArrayList<>();

        for (RegionGeometry.Cell of : ofCells)
        {
            for (RegionGeometry.Cell to : toCells)
            {
                if (ClauseMath.distance(of, to, "chebyshev") <= this.maxDistance)
                {
                    near.add(of);
                    break;
                }
            }
        }

        Set<RegionGeometry.Cell> satisfied = new HashSet<>();

        for (List<RegionGeometry.Cell> run : CellGraphs.components(near, CellGraphs.OFFSETS_26))
        {
            if (run.size() >= this.minRun)
            {
                satisfied.addAll(run);
            }
        }

        final int total = ofCells.size();
        double confidence = ClauseMath.coverageConfidence((double) satisfied.size() / total, this.coverage);
        String diagnostic = ClauseMath.coverageDiagnostic(
                satisfied.size(), total, this.of, "runs alongside", this.to);

        return FormResult.of(confidence, diagnostic);
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' runs along '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }
}
