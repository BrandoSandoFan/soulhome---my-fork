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

class AnyClauseTest
{
    @Test
    @DisplayName("evaluates as its best-satisfied child")
    void bestOf()
    {
        AnyClause any = new AnyClause(List.of(
                new WeightedClause(1.0, FakeClause.of(0.2)),
                new WeightedClause(1.0, FakeClause.of(0.9)),
                new WeightedClause(1.0, FakeClause.of(0.4))));

        assertEquals(0.9, any.evaluate(RegionGeometry.EMPTY, Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a child's own weight does not affect which alternative wins")
    void weightIsIgnoredByAny()
    {
        AnyClause any = new AnyClause(List.of(
                new WeightedClause(100.0, FakeClause.of(0.1)),
                new WeightedClause(0.01, FakeClause.of(0.8))));

        assertEquals(0.8, any.evaluate(RegionGeometry.EMPTY, Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an empty 'any' is zero confidence, not an exception")
    void emptyIsZero()
    {
        assertEquals(FormResult.ZERO, new AnyClause(List.of()).evaluate(RegionGeometry.EMPTY, Map.of()));
    }

    @Test
    @DisplayName("validationErrors collects every child's own errors, plus one for an empty list")
    void validationErrorsAggregateChildren()
    {
        AnyClause populated = new AnyClause(List.of(new WeightedClause(1.0, FakeClause.invalid("broken"))));
        assertEquals(List.of("broken"), populated.validationErrors(Set.of()));

        assertEquals(List.of("'any' names no clauses"), new AnyClause(List.of()).validationErrors(Set.of()));
    }
}
