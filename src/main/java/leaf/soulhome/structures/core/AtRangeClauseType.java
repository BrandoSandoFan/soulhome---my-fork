/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code at_range}: is {@code of} at arm's length from {@code to} - near, but not touching. Unlike
 * {@code within}, a distance below {@code min_distance} does not satisfy the relation: a chair
 * jammed against the lectern is not "at reading distance" of it. See the relations table in #29.
 */
public final class AtRangeClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "at_range";
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
                ClauseParamSpec.optional("min_distance", ClauseParamSpec.Type.INT, 2),
                ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.optional("metric", ClauseParamSpec.Type.STRING, "chebyshev"),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new AtRangeClause(
                params.getElement("of"), params.getElement("to"),
                params.getInt("min_distance"), params.getInt("max_distance"),
                params.getString("metric"), params.getDouble("coverage"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        AtRangeClause atRange = (AtRangeClause) clause;
        return Map.of(
                "of", atRange.of(), "to", atRange.to(),
                "min_distance", atRange.minDistance(), "max_distance", atRange.maxDistance(),
                "metric", atRange.metric(), "coverage", atRange.coverage());
    }
}

record AtRangeClause(
        String of, String to, int minDistance, int maxDistance, String metric, double coverage)
        implements FormClause
{
    @Override
    public String typeId()
    {
        return "at_range";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        return ClauseMath.evaluateCoverage(
                geometry, elements, this.of, this.to, this.coverage,
                "is between " + this.minDistance + " and " + this.maxDistance + " of",
                (of, to) ->
                {
                    double distance = ClauseMath.distance(of, to, this.metric);
                    return distance >= this.minDistance && distance <= this.maxDistance;
                });
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' is " + this.minDistance + "-" + this.maxDistance + " from '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return this.minDistance > this.maxDistance
                ? List.of("'min_distance' (" + this.minDistance + ") is greater than 'max_distance' ("
                        + this.maxDistance + "), so this relation can never be satisfied")
                : List.of();
    }
}
