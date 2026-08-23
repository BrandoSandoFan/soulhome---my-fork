/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shared grading behind {@code above} and {@code beneath} (#29): one record, configured by
 * which direction it grades, since the two relations differ only in which side of {@code of}/
 * {@code to} has to be higher - everything else, including which id a clause was actually
 * registered under, is identical. {@link AboveClauseType} and {@link BeneathClauseType} are the
 * two distinct registered ids the datapack format needs; this is their common implementation.
 */
record VerticalRelationClause(
        String typeId, String of, String to, int maxDrop, String alignment, double coverage, boolean ofAboveTo)
        implements FormClause
{
    static List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("max_drop", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("alignment", ClauseParamSpec.Type.STRING, "column"),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0));
    }

    static FormClause create(ClauseParams params, boolean ofAboveTo)
    {
        return new VerticalRelationClause(
                ofAboveTo ? "above" : "beneath",
                params.getElement("of"), params.getElement("to"),
                params.getInt("max_drop"), params.getString("alignment"), params.getDouble("coverage"),
                ofAboveTo);
    }

    static Map<String, Object> encode(FormClause clause)
    {
        VerticalRelationClause vertical = (VerticalRelationClause) clause;
        return Map.of(
                "of", vertical.of(), "to", vertical.to(),
                "max_drop", vertical.maxDrop(), "alignment", vertical.alignment(), "coverage", vertical.coverage());
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        String verb = this.ofAboveTo ? "sits over" : "sits under";

        return ClauseMath.evaluateCoverage(
                geometry, elements, this.of, this.to, this.coverage, verb,
                (of, to) -> ClauseMath.verticallyAligned(of, to, this.ofAboveTo, this.maxDrop, this.alignment));
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' " + (this.ofAboveTo ? "sits over" : "sits under") + " '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }
}
