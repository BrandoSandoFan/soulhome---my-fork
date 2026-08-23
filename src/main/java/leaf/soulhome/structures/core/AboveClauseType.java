/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code above}: does {@code of} sit directly over {@code to} - the rails run over the ice, not
 * beside it. The mirror of {@link BeneathClauseType}; the two are graded independently, so
 * {@code above{of: rails, to: ice}} and {@code beneath{of: ice, to: rails}} are the same physical
 * question asked from opposite ends and are not guaranteed to agree on the same build (a rail with
 * ice above and below it satisfies both; a rail with ice only below it satisfies neither the way
 * the other one expects "above" read). See the relations table in #29.
 */
public final class AboveClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "above";
    }

    @Override
    public Kind kind()
    {
        return Kind.RELATION;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return VerticalRelationClause.params();
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return VerticalRelationClause.create(params, true);
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        return VerticalRelationClause.encode(clause);
    }
}
