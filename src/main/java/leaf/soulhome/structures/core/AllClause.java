/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Every child should hold." Grades as the weighted mean of its children's confidence, never a
 * product - a product would let one unmet clause zero the whole form, which is gating a structural
 * finding through the back door. Structure is evidence, never a gate: rule 1 of the structural
 * considerations epic (#25).
 */
public record AllClause(List<WeightedClause> children) implements FormClause
{
    public AllClause
    {
        children = List.copyOf(children);
    }

    @Override
    public String typeId()
    {
        return "all";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        if (this.children.isEmpty())
        {
            return FormResult.ZERO;
        }

        double weightedSum = 0d;
        double weightTotal = 0d;
        List<String> diagnostics = new ArrayList<>();

        for (WeightedClause child : this.children)
        {
            FormResult result = child.clause().evaluate(geometry, elements);
            weightedSum += child.weight() * result.confidence();
            weightTotal += child.weight();

            if (!result.diagnostic().isBlank())
            {
                diagnostics.add(result.diagnostic());
            }
        }

        double confidence = weightTotal > 0 ? weightedSum / weightTotal : 0d;
        return new FormResult(confidence, String.join("; ", diagnostics));
    }

    @Override
    public String describe()
    {
        List<String> parts = new ArrayList<>(this.children.size());

        for (WeightedClause child : this.children)
        {
            parts.add(child.clause().describe());
        }

        return String.join(", and ", parts);
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.children.isEmpty())
        {
            errors.add("'all' names no clauses");
        }

        for (WeightedClause child : this.children)
        {
            errors.addAll(child.clause().validationErrors(elementNames));
        }

        return errors;
    }

    @Override
    public boolean needsClearance()
    {
        for (WeightedClause child : this.children)
        {
            if (child.clause().needsClearance())
            {
                return true;
            }
        }

        return false;
    }
}
