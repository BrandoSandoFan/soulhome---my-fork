/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code within}: is {@code of} near {@code to} at all - the loosest of the distance relations,
 * for "the chairs are somewhere near the table" rather than any particular arrangement. See the
 * relations table in #29.
 */
public final class WithinClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "within";
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
                ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("metric", ClauseParamSpec.Type.STRING, "chebyshev"),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new WithinClause(
                params.getElement("of"), params.getElement("to"),
                params.getInt("max_distance"), params.getString("metric"), params.getDouble("coverage"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        WithinClause within = (WithinClause) clause;
        return Map.of(
                "of", within.of(), "to", within.to(),
                "max_distance", within.maxDistance(), "metric", within.metric(), "coverage", within.coverage());
    }
}

record WithinClause(String of, String to, int maxDistance, String metric, double coverage) implements FormClause
{
    @Override
    public String typeId()
    {
        return "within";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        return ClauseMath.evaluateCoverage(
                geometry, elements, this.of, this.to, this.coverage,
                "is within " + this.maxDistance + " of",
                (of, to) -> ClauseMath.distance(of, to, this.metric) <= this.maxDistance);
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' is within " + this.maxDistance + " of '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }
}
