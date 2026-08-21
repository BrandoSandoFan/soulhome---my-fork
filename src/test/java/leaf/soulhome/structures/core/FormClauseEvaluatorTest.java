/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormClauseEvaluatorTest
{
    @Test
    @DisplayName("a leaf's evaluation carries its own confidence and diagnostic, no children")
    void leafEvaluatesDirectly()
    {
        ArchetypeScore.ClauseEvaluation eval = evaluate(FakeClause.of(0.6, "half there"));

        assertEquals(0.6, eval.confidence(), 1e-9);
        assertEquals("half there", eval.diagnostic());
        assertEquals(List.of(), eval.children());
        assertEquals(-1, eval.selectedChild());
    }

    @Test
    @DisplayName("'any' returns its best child, not its first or its last, and records which one won")
    void anyRecordsTheWinningChild()
    {
        AnyClause any = new AnyClause(List.of(
                new WeightedClause(1.0, FakeClause.of(0.2, "under")),
                new WeightedClause(1.0, FakeClause.of(0.9, "around")),
                new WeightedClause(1.0, FakeClause.of(0.4, "inside"))));

        ArchetypeScore.ClauseEvaluation eval = evaluate(any);

        assertEquals(0.9, eval.confidence(), 1e-9);
        assertEquals("around", eval.diagnostic());
        assertEquals(1, eval.selectedChild());
        assertEquals(3, eval.children().size());
    }

    @Test
    @DisplayName("'all' returns a weighted mean, and one zero child does not zero the form")
    void allIsAWeightedMeanNotAProduct()
    {
        AllClause all = new AllClause(List.of(
                new WeightedClause(1.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, FakeClause.of(0.0))));

        // a product would score this 0.0 - the regression test for gating through the back door
        assertEquals(0.5, evaluate(all).confidence(), 1e-9);
    }

    @Test
    @DisplayName("per-child weight shifts the mean as expected")
    void weightShiftsTheMean()
    {
        AllClause all = new AllClause(List.of(
                new WeightedClause(3.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, FakeClause.of(0.0))));

        assertEquals(0.75, evaluate(all).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the tree carries a confidence and diagnostic for every node, not just the root")
    void treeIsFullyPopulated()
    {
        AllClause all = new AllClause(List.of(
                new WeightedClause(2.0, FakeClause.of(1.0, "loop closed")),
                new WeightedClause(1.0, new AnyClause(List.of(
                        new WeightedClause(1.0, FakeClause.of(0.3, "above")),
                        new WeightedClause(1.0, FakeClause.of(0.7, "surrounds")))))));

        ArchetypeScore.ClauseEvaluation root = evaluate(all);

        assertEquals("all", root.typeId());
        assertEquals(2, root.children().size());
        assertEquals("loop closed", root.children().get(0).diagnostic());

        ArchetypeScore.ClauseEvaluation anyNode = root.children().get(1);
        assertEquals("any", anyNode.typeId());
        assertEquals(0.7, anyNode.confidence(), 1e-9);
        assertEquals(1, anyNode.selectedChild());
        assertEquals("surrounds", anyNode.children().get(1).diagnostic());

        // (2.0 * 1.0 + 1.0 * 0.7) / 3.0
        assertEquals((2.0 * 1.0 + 1.0 * 0.7) / 3.0, root.confidence(), 1e-9);
    }

    @Test
    @DisplayName("shared clauses are evaluated once per region")
    void sharedClausesAreMemoizedByValue()
    {
        // two distinct objects, equal by value - the same shape a datapack author would produce by
        // writing the same clause into two different archetypes' forms
        CountingClause first = new CountingClause("shared", 0.7d);
        CountingClause second = new CountingClause("shared", 0.7d);

        Map<FormClause, FormResult> memo = new HashMap<>();
        FormClauseEvaluator.evaluate(first, RegionGeometry.EMPTY, Map.of(), memo);
        FormClauseEvaluator.evaluate(second, RegionGeometry.EMPTY, Map.of(), memo);

        assertEquals(1, first.callCount() + second.callCount(),
                "the second, value-equal clause should reuse the memo rather than being evaluated again");
    }

    @Test
    @DisplayName("two clauses that merely look alike but are not equal are each evaluated")
    void distinctClausesAreNotConflated()
    {
        CountingClause first = new CountingClause("a", 0.7d);
        CountingClause second = new CountingClause("b", 0.7d);

        Map<FormClause, FormResult> memo = new HashMap<>();
        FormClauseEvaluator.evaluate(first, RegionGeometry.EMPTY, Map.of(), memo);
        FormClauseEvaluator.evaluate(second, RegionGeometry.EMPTY, Map.of(), memo);

        assertTrue(first.callCount() == 1 && second.callCount() == 1);
    }

    private static ArchetypeScore.ClauseEvaluation evaluate(FormClause clause)
    {
        return FormClauseEvaluator.evaluate(clause, RegionGeometry.EMPTY, Map.of(), new HashMap<>());
    }

    /** Like {@link FakeClause}, but mutable and counts its own {@code evaluate} calls. */
    private static final class CountingClause implements FormClause
    {
        private final String id;
        private final double confidence;
        private int callCount;

        CountingClause(String id, double confidence)
        {
            this.id = id;
            this.confidence = confidence;
        }

        int callCount()
        {
            return this.callCount;
        }

        @Override
        public String typeId()
        {
            return this.id;
        }

        @Override
        public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
        {
            this.callCount++;
            return new FormResult(this.confidence, "");
        }

        @Override
        public String describe()
        {
            return this.id;
        }

        @Override
        public List<String> validationErrors(Set<String> elementNames)
        {
            return List.of();
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof CountingClause other && this.id.equals(other.id);
        }

        @Override
        public int hashCode()
        {
            return this.id.hashCode();
        }
    }
}
