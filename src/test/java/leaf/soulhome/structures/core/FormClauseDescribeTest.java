/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import leaf.soulhome.structures.BuiltinFormClauses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code shape}/{@code relation} clause type the mod ships must produce a non-empty
 * {@link FormClause#describe()} - #35 of the structural considerations epic (#25) builds the
 * book's "how you arrange it" page from exactly this method, so a clause type that cannot
 * describe itself would silently ship a book with a gap in it.
 *
 * <p>Runs against a throwaway {@link FormClauseRegistry} rather than {@link FormClauseRegistry#BUILTIN}
 * so this test can never collide with another that registers its own fakes onto the shared one,
 * matching the convention {@code FormCodecsTest} and {@code FormClauseRegistryTest} already use.
 */
class FormClauseDescribeTest
{
    @Test
    @DisplayName("every registered clause type describes itself with something non-empty")
    void everyClauseTypeDescribesItself()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        BuiltinFormClauses.registerAll(registry);

        assertFalse(registry.all().isEmpty(), "the registry should not be empty in the first place");

        List<Executable> checks = new ArrayList<>();

        for (FormClauseType type : registry.all())
        {
            checks.add(() ->
            {
                FormClause clause = type.create(defaultParamsFor(type));
                String description = clause.describe();

                assertFalse(description == null || description.isBlank(),
                        "clause type '" + type.id() + "' produced no description");
            });
        }

        assertAll(checks);
    }

    @Test
    @DisplayName("an any node's description reads as alternatives, not as a list of requirements")
    void anyDescribesAsAlternatives()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        BuiltinFormClauses.registerAll(registry);

        FormClauseType above = registry.get(FormClauseType.Kind.RELATION, "above").orElseThrow();
        FormClauseType inside = registry.get(FormClauseType.Kind.RELATION, "inside").orElseThrow();

        FormClause anyClause = new AnyClause(List.of(
                new WeightedClause(1.0, above.create(paramsOfTo(above, "rails", "surface"))),
                new WeightedClause(1.0, inside.create(paramsOfTo(inside, "surface", "rails")))));

        String description = anyClause.describe();

        assertTrue(description.contains(" or "), "expected alternatives joined by 'or': " + description);
        assertFalse(description.contains(" and "), "an 'any' is not a conjunction of requirements: " + description);
    }

    /**
     * Fills in every parameter a clause type declares: an alternating dummy name for each
     * {@code ELEMENT} parameter (so a two-element relation reads "of" and "to" as distinct
     * elements), the spec's own default for anything optional, and a small positive placeholder
     * for the handful of required numeric parameters ({@code ideal_cells}, {@code ideal_area},
     * {@code ideal_run}) that have no default to fall back on.
     */
    private static ClauseParams defaultParamsFor(FormClauseType type)
    {
        ClauseParams.Builder builder = ClauseParams.builder();
        int elementIndex = 0;
        String[] elementNames = {"a", "b"};

        for (ClauseParamSpec spec : type.params())
        {
            if (spec.type() == ClauseParamSpec.Type.ELEMENT)
            {
                builder.put(spec.name(), elementNames[elementIndex++ % elementNames.length]);
            }
            else if (!spec.required())
            {
                builder.put(spec.name(), spec.defaultValue());
            }
            else if (spec.type() == ClauseParamSpec.Type.INT)
            {
                builder.put(spec.name(), 10);
            }
            else if (spec.type() == ClauseParamSpec.Type.DOUBLE)
            {
                builder.put(spec.name(), 1.0);
            }
            else
            {
                builder.put(spec.name(), "");
            }
        }

        return builder.build();
    }

    private static ClauseParams paramsOfTo(FormClauseType type, String of, String to)
    {
        ClauseParams.Builder builder = ClauseParams.builder();

        for (ClauseParamSpec spec : type.params())
        {
            if (spec.name().equals("of"))
            {
                builder.put("of", of);
            }
            else if (spec.name().equals("to"))
            {
                builder.put("to", to);
            }
            else
            {
                builder.put(spec.name(), spec.defaultValue());
            }
        }

        return builder.build();
    }
}
