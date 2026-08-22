/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Any child holding is enough." Grades as its best-satisfied child. Several arrangements can be
 * equally right - the ice on a track may run under, around or inside the rails - and a player who
 * builds one of them should not be marked down for not also building the others.
 */
public record AnyClause(List<WeightedClause> children) implements FormClause
{
    public AnyClause
    {
        children = List.copyOf(children);
    }

    @Override
    public String typeId()
    {
        return "any";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        FormResult best = FormResult.ZERO;

        for (WeightedClause child : this.children)
        {
            FormResult result = child.clause().evaluate(geometry, elements);

            if (result.confidence() > best.confidence())
            {
                best = result;
            }
        }

        return best;
    }

    @Override
    public String describe()
    {
        List<String> parts = new ArrayList<>(this.children.size());

        for (WeightedClause child : this.children)
        {
            parts.add(child.clause().describe());
        }

        return String.join(", or ", parts);
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.children.isEmpty())
        {
            errors.add("'any' names no clauses");
        }

        for (WeightedClause child : this.children)
        {
            errors.addAll(child.clause().validationErrors(elementNames));
        }

        return errors;
    }
}
