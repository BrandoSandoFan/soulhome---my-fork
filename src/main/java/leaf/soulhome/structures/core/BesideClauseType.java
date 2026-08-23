/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code beside}: is {@code of} horizontally alongside {@code to}, at nearly the same level - a
 * chair pulled up next to a table, not stacked on it or across the room from it. See the relations
 * table in #29.
 */
public final class BesideClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "beside";
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
                ClauseParamSpec.optional("max_drop", ClauseParamSpec.Type.INT, 0),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new BesideClause(
                params.getElement("of"), params.getElement("to"),
                params.getInt("max_drop"), params.getDouble("coverage"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        BesideClause beside = (BesideClause) clause;
        return Map.of("of", beside.of(), "to", beside.to(), "max_drop", beside.maxDrop(), "coverage", beside.coverage());
    }
}

record BesideClause(String of, String to, int maxDrop, double coverage) implements FormClause
{
    @Override
    public String typeId()
    {
        return "beside";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        return ClauseMath.evaluateCoverage(
                geometry, elements, this.of, this.to, this.coverage, "is beside",
                (of, to) -> ClauseMath.isBeside(of, to, this.maxDrop));
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' is beside '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }
}
