/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a clause tree against one region's geometry, building the same combined confidence
 * {@link AllClause}/{@link AnyClause} compute internally - but bottom-up, one leaf at a time, so
 * the full tree of intermediate results survives for {@link ArchetypeScore.StructureContribution}
 * instead of being collapsed to a single number the moment a parent's mean or max is taken.
 *
 * <p>Leaf results are read through a caller-supplied memo, keyed on the leaf clause together with the
 * element bindings it was evaluated against - a leaf clause is a value record holding element
 * <em>names</em> ("table"), not the {@link BlockMatcher}s those names resolve to, and two archetypes
 * can name the same clause with the same element names while binding those names to different
 * blocks. {@link ArchetypeClassifier} shares one memo across every archetype it scores for a given
 * region, so a shared leaf - same clause, same bindings - is evaluated once per scan rather than once
 * per archetype, without two differently-bound archetypes reading each other's answer.
 */
final class FormClauseEvaluator
{
    /**
     * A leaf clause is only the same computation when the element bindings it reads are also the
     * same; {@code elements} is part of the memo key for exactly that reason. {@code Map} has value
     * equality, so two archetypes binding the same names to the same {@link BlockMatcher}s still
     * share one cache entry.
     */
    record MemoKey(FormClause clause, Map<String, BlockMatcher> elements)
    {
    }

    private FormClauseEvaluator()
    {
    }

    static ArchetypeScore.ClauseEvaluation evaluate(
            FormClause clause,
            RegionGeometry geometry,
            Map<String, BlockMatcher> elements,
            Map<MemoKey, FormResult> leafMemo)
    {
        if (clause instanceof AllClause all)
        {
            return evaluateAll(all, geometry, elements, leafMemo);
        }

        if (clause instanceof AnyClause any)
        {
            return evaluateAny(any, geometry, elements, leafMemo);
        }

        FormResult result = leafMemo.computeIfAbsent(
                new MemoKey(clause, elements), key -> key.clause().evaluate(geometry, elements));

        return new ArchetypeScore.ClauseEvaluation(
                clause.typeId(), clause.describe(), result.confidence(), result.diagnostic(), List.of(), -1);
    }

    private static ArchetypeScore.ClauseEvaluation evaluateAll(
            AllClause all, RegionGeometry geometry, Map<String, BlockMatcher> elements, Map<MemoKey, FormResult> leafMemo)
    {
        List<ArchetypeScore.ClauseEvaluation> children = new ArrayList<>(all.children().size());
        double weightedSum = 0d;
        double weightTotal = 0d;

        for (WeightedClause child : all.children())
        {
            ArchetypeScore.ClauseEvaluation childEval = evaluate(child.clause(), geometry, elements, leafMemo);
            children.add(childEval);
            weightedSum += child.weight() * childEval.confidence();
            weightTotal += child.weight();
        }

        double confidence = weightTotal > 0 ? weightedSum / weightTotal : 0d;

        return new ArchetypeScore.ClauseEvaluation(
                "all", all.describe(), confidence, joinDiagnostics(children), children, -1);
    }

    private static ArchetypeScore.ClauseEvaluation evaluateAny(
            AnyClause any, RegionGeometry geometry, Map<String, BlockMatcher> elements, Map<MemoKey, FormResult> leafMemo)
    {
        List<ArchetypeScore.ClauseEvaluation> children = new ArrayList<>(any.children().size());
        int bestIndex = -1;
        double bestConfidence = -1d;

        for (WeightedClause child : any.children())
        {
            ArchetypeScore.ClauseEvaluation childEval = evaluate(child.clause(), geometry, elements, leafMemo);
            children.add(childEval);

            if (childEval.confidence() > bestConfidence)
            {
                bestConfidence = childEval.confidence();
                bestIndex = children.size() - 1;
            }
        }

        double confidence = Math.max(0d, bestConfidence);
        String diagnostic = bestIndex >= 0 ? children.get(bestIndex).diagnostic() : "";

        return new ArchetypeScore.ClauseEvaluation("any", any.describe(), confidence, diagnostic, children, bestIndex);
    }

    private static String joinDiagnostics(List<ArchetypeScore.ClauseEvaluation> children)
    {
        List<String> parts = new ArrayList<>(children.size());

        for (ArchetypeScore.ClauseEvaluation child : children)
        {
            if (!child.diagnostic().isBlank())
            {
                parts.add(child.diagnostic());
            }
        }

        return String.join("; ", parts);
    }
}
