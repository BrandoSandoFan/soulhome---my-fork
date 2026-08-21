/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stands in for a clause whose id is not registered, or whose parameters could not be read, so a
 * form's other clauses keep working rather than the whole form - or the whole archetype - failing
 * to load over one clause naming a mod that is not installed. See "Unknown clauses must not poison
 * the archetype" in #27.
 *
 * <p>Always the empty confidence and never a validation error on its own account: a datapack naming
 * an absent mod's clause is not malformed, it is just inert here.
 */
public record UnknownClause(String rawId) implements FormClause
{
    @Override
    public String typeId()
    {
        return "unknown:" + this.rawId;
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        return FormResult.ZERO;
    }

    @Override
    public String describe()
    {
        return "";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }
}
