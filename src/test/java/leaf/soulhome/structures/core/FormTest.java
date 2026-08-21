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

class FormTest
{
    private static final BlockMatcher RAILS = BlockMatcher.ofTags("minecraft:rails");
    private static final BlockMatcher ICE = BlockMatcher.ofBlocks("minecraft:ice");

    @Test
    @DisplayName("a well-formed form has no validation errors")
    void wellFormedFormIsValid()
    {
        Form form = new Form(
                "circuit", 8.0, "circuit",
                Map.of("rails", RAILS, "surface", ICE),
                FakeClause.of(1.0),
                Set.of("rails", "surface"));

        assertEquals(List.of(), form.validationErrors());
    }

    @Test
    @DisplayName("evaluate delegates to the root clause with the form's own elements")
    void evaluateDelegatesToRoot()
    {
        Form form = new Form(
                "circuit", 1.0, null, Map.of("rails", RAILS),
                FakeClause.of(0.75), Set.of("rails"));

        assertEquals(0.75, form.evaluate(RegionGeometry.EMPTY).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a blank role falls back to the default")
    void blankRoleFallsBackToDefault()
    {
        Form form = new Form("circuit", 1.0, "  ", Map.of("rails", RAILS), FakeClause.of(1.0), Set.of("rails"));
        assertEquals(Form.DEFAULT_ROLE, form.role());
    }

    @Test
    @DisplayName("a blank or missing name is a validation error")
    void blankNameIsAnError()
    {
        Form form = new Form(" ", 1.0, null, Map.of("rails", RAILS), FakeClause.of(1.0), Set.of("rails"));
        assertTrue(form.validationErrors().contains("'name' is missing or blank"));
    }

    @Test
    @DisplayName("a non-positive weight is a validation error")
    void nonPositiveWeightIsAnError()
    {
        Form form = new Form("circuit", 0.0, null, Map.of("rails", RAILS), FakeClause.of(1.0), Set.of("rails"));
        assertTrue(form.validationErrors().contains("'weight' must be positive, got 0.0"));
    }

    @Test
    @DisplayName("no elements declared is a validation error")
    void noElementsIsAnError()
    {
        Form form = new Form("circuit", 1.0, null, Map.of(), FakeClause.of(1.0), Set.of());
        assertTrue(form.validationErrors().contains("no 'elements' declared"));
    }

    @Test
    @DisplayName("more than four elements is a validation error")
    void tooManyElementsIsAnError()
    {
        Form form = new Form(
                "circuit", 1.0, null,
                Map.of("a", RAILS, "b", RAILS, "c", RAILS, "d", RAILS, "e", RAILS),
                FakeClause.of(1.0), Set.of("a", "b", "c", "d", "e"));

        assertTrue(form.validationErrors().stream().anyMatch(error -> error.contains("more than the 4")));
    }

    @Test
    @DisplayName("a single element is a warning, not an error")
    void singleElementIsWarningNotError()
    {
        Form form = new Form("circuit", 1.0, null, Map.of("rails", RAILS), FakeClause.of(1.0), Set.of("rails"));

        assertEquals(List.of(), form.validationErrors());
        assertEquals(1, form.validationWarnings().size());
    }

    @Test
    @DisplayName("an invalid element matcher's errors are namespaced by element")
    void elementMatcherErrorsAreNamespaced()
    {
        Form form = new Form(
                "circuit", 1.0, null,
                Map.of("rails", new BlockMatcher(List.of(), List.of())),
                FakeClause.of(1.0), Set.of("rails"));

        assertTrue(form.validationErrors().stream().anyMatch(error -> error.startsWith("elements.rails: ")));
    }

    @Test
    @DisplayName("a clause referring to an undeclared element is a validation error")
    void undeclaredReferenceIsAnError()
    {
        Form form = new Form(
                "circuit", 1.0, null, Map.of("rails", RAILS),
                FakeClause.of(1.0), Set.of("rails", "surface"));

        assertTrue(form.validationErrors().contains("a clause refers to undeclared element 'surface'"));
    }

    @Test
    @DisplayName("an element declared but never used by a clause is a validation error")
    void unusedElementIsAnError()
    {
        Form form = new Form(
                "circuit", 1.0, null,
                Map.of("rails", RAILS, "surface", ICE),
                FakeClause.of(1.0), Set.of("rails"));

        assertTrue(form.validationErrors().contains("element 'surface' is declared but never used by a clause"));
    }

    @Test
    @DisplayName("a root clause's own validation errors are surfaced")
    void rootClauseErrorsAreSurfaced()
    {
        Form form = new Form(
                "circuit", 1.0, null, Map.of("rails", RAILS),
                FakeClause.invalid("min_cells must be positive"), Set.of("rails"));

        assertTrue(form.validationErrors().contains("min_cells must be positive"));
    }

    @Test
    @DisplayName("all-of-leaves is within the two-level nesting cap")
    void allOfLeavesIsWithinCap()
    {
        AllClause root = new AllClause(List.of(
                new WeightedClause(1.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, FakeClause.of(1.0))));

        Form form = new Form("circuit", 1.0, null, Map.of("rails", RAILS), root, Set.of("rails"));
        assertEquals(List.of(), form.validationErrors());
    }

    @Test
    @DisplayName("all-of-leaves-and-one-any is exactly the two-level cap, and is valid")
    void allOfAnyIsWithinCap()
    {
        AnyClause any = new AnyClause(List.of(
                new WeightedClause(1.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, FakeClause.of(1.0))));

        AllClause root = new AllClause(List.of(
                new WeightedClause(2.0, FakeClause.of(1.0)),
                new WeightedClause(1.0, any)));

        Form form = new Form("circuit", 1.0, null, Map.of("rails", RAILS), root, Set.of("rails"));
        assertEquals(List.of(), form.validationErrors());
    }

    @Test
    @DisplayName("three levels of composite nesting is a validation error")
    void threeLevelsOfNestingIsAnError()
    {
        AllClause innermost = new AllClause(List.of(new WeightedClause(1.0, FakeClause.of(1.0))));
        AnyClause middle = new AnyClause(List.of(new WeightedClause(1.0, innermost)));
        AllClause root = new AllClause(List.of(new WeightedClause(1.0, middle)));

        Form form = new Form("circuit", 1.0, null, Map.of("rails", RAILS), root, Set.of("rails"));

        assertTrue(form.validationErrors().stream().anyMatch(error -> error.contains("nesting")));
    }

    @Test
    @DisplayName("a null root clause is a validation error, not an exception")
    void nullRootIsAnError()
    {
        Form form = new Form("circuit", 1.0, null, Map.of("rails", RAILS), null, Set.of());
        assertTrue(form.validationErrors().contains("has no evaluable clause"));
    }
}
