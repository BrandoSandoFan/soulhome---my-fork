/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shared grading behind {@code above} and {@code beneath} (#29): one record, configured by
 * which direction it grades, since the two relations differ only in which side of {@code of}/
 * {@code to} has to be higher - everything else, including which id a clause was actually
 * registered under, is identical. {@link AboveClauseType} and {@link BeneathClauseType} are the
 * two distinct registered ids the datapack format needs; this is their common implementation.
 *
 * <h2>{@code reach}</h2>
 *
 * {@code "region"} (the default) reads only what the room took in. {@code "footing"} also reads the
 * block directly under the upper element when that block is outside the room (#307): a note block
 * set on the floor plays the floor, and the floor's underside is not part of any room's shell. It
 * is opt-in rather than how every vertical clause reads because widening what a mine's or an
 * ossuary's clause can see would move rooms that nobody touched; the scanner indexes footing only
 * for elements some loaded clause names this way, see {@link #footingElements()}.
 */
record VerticalRelationClause(
        String typeId, String of, String to, int maxDrop, String alignment, double coverage, String reach,
        boolean ofAboveTo)
        implements FormClause
{
    static final String REACH_REGION = "region";
    static final String REACH_FOOTING = "footing";

    static List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("max_drop", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("alignment", ClauseParamSpec.Type.STRING, "column"),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0),
                ClauseParamSpec.optional("reach", ClauseParamSpec.Type.STRING, REACH_REGION));
    }

    static FormClause create(ClauseParams params, boolean ofAboveTo)
    {
        return new VerticalRelationClause(
                ofAboveTo ? "above" : "beneath",
                params.getElement("of"), params.getElement("to"),
                params.getInt("max_drop"), params.getString("alignment"), params.getDouble("coverage"),
                params.getString("reach"), ofAboveTo);
    }

    static Map<String, Object> encode(FormClause clause)
    {
        VerticalRelationClause vertical = (VerticalRelationClause) clause;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("of", vertical.of());
        values.put("to", vertical.to());
        values.put("max_drop", vertical.maxDrop());
        values.put("alignment", vertical.alignment());
        values.put("coverage", vertical.coverage());
        values.put("reach", vertical.reach());
        return values;
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
        if (!REACH_REGION.equals(this.reach) && !REACH_FOOTING.equals(this.reach))
        {
            return List.of("'reach' must be \"" + REACH_REGION + "\" or \"" + REACH_FOOTING + "\", not \""
                    + this.reach + "\"");
        }

        return List.of();
    }

    /** The upper element, whichever side of the clause names it - what stands on the footing. */
    @Override
    public Set<String> footingElements()
    {
        return REACH_FOOTING.equals(this.reach) ? Set.of(this.ofAboveTo ? this.of : this.to) : Set.of();
    }

    /** The lower element, whichever side of the clause names it - what is stood on. */
    @Override
    public Set<String> footingTargets()
    {
        return REACH_FOOTING.equals(this.reach) ? Set.of(this.ofAboveTo ? this.to : this.of) : Set.of();
    }

    @Override
    public boolean readsOnlyFootings()
    {
        return REACH_FOOTING.equals(this.reach);
    }
}
