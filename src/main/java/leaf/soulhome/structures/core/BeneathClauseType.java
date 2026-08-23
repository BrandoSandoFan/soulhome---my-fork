/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;

/**
 * {@code beneath}: does {@code of} sit directly under {@code to}. The mirror of
 * {@link AboveClauseType} - see there for why the two are graded independently rather than one
 * being derived from the other.
 */
public final class BeneathClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "beneath";
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
        return VerticalRelationClause.create(params, false);
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        return VerticalRelationClause.encode(clause);
    }
}
