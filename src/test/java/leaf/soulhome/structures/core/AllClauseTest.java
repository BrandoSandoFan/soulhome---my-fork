/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllClauseTest
{
    @Test
    @DisplayName("evaluates as the weighted mean of its children, never a product")
    void weightedMean()
    {
        AllClause all = new AllClause(List.of(
                new WeightedClause(1.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, FakeClause.of(0.0))));

        // a product would score this 0.0; rule 1 of #25 says structure never gates
        assertEquals(0.5, all.evaluate(RegionGeometry.EMPTY, Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("weight skews the mean toward the heavier child")
    void weightSkewsTheMean()
    {
        AllClause all = new AllClause(List.of(
                new WeightedClause(3.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, FakeClause.of(0.0))));

        assertEquals(0.75, all.evaluate(RegionGeometry.EMPTY, Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an empty 'all' is zero confidence, not an exception")
    void emptyIsZero()
    {
        assertEquals(FormResult.ZERO, new AllClause(List.of()).evaluate(RegionGeometry.EMPTY, Map.of()));
    }

    @Test
    @DisplayName("blank child diagnostics are dropped, non-blank ones are joined")
    void diagnosticsAreJoined()
    {
        AllClause all = new AllClause(List.of(
                new WeightedClause(1.0, FakeClause.of(1.0, "the rails form a loop")),
                new WeightedClause(1.0, FakeClause.of(0.0, "")),
                new WeightedClause(1.0, FakeClause.of(0.5, "the ice covers half the rails"))));

        String diagnostic = all.evaluate(RegionGeometry.EMPTY, Map.of()).diagnostic();

        assertTrue(diagnostic.contains("the rails form a loop"));
        assertTrue(diagnostic.contains("the ice covers half the rails"));
    }

    @Test
    @DisplayName("validationErrors collects every child's own errors, plus one for an empty list")
    void validationErrorsAggregateChildren()
    {
        AllClause populated = new AllClause(List.of(
                new WeightedClause(1.0, FakeClause.invalid("child A is broken")),
                new WeightedClause(1.0, FakeClause.invalid("child B is broken"))));

        List<String> errors = populated.validationErrors(Set.of());
        assertEquals(2, errors.size());
        assertTrue(errors.contains("child A is broken"));
        assertTrue(errors.contains("child B is broken"));

        assertEquals(List.of("'all' names no clauses"), new AllClause(List.of()).validationErrors(Set.of()));
    }
}
