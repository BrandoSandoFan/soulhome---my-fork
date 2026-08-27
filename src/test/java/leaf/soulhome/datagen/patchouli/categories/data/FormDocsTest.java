/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import leaf.soulhome.structures.core.AnyClause;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.ClauseParams;
import leaf.soulhome.structures.core.Form;
import leaf.soulhome.structures.core.FormClause;
import leaf.soulhome.structures.core.FormClauseRegistry;
import leaf.soulhome.structures.core.FormResult;
import leaf.soulhome.structures.core.LoopClauseType;
import leaf.soulhome.structures.core.RegionGeometry;
import leaf.soulhome.structures.core.WeightedClause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormDocs} is the only thing standing between a form's clause tree and the book page
 * #35 (of the structural considerations epic, #25) adds - so it earns direct coverage rather than
 * only being exercised indirectly through {@code PatchouliMultiblocksTest}.
 */
class FormDocsTest
{
    @Test
    @DisplayName("a form's description reads as a capitalised sentence, with no raw clause-tree quoting")
    void describeProducesASentence()
    {
        LoopClauseType loopType = new LoopClauseType();

        Form form = new Form(
                "circuit", 6.0, "arrangement",
                Map.of("rails", BlockMatcher.ofTags("minecraft:rails")),
                loopType.create(ClauseParams.builder()
                        .put("of", "rails")
                        .put("min_cells", 12)
                        .put("ideal_cells", 32)
                        .put("connectivity", "planar")
                        .build()),
                Set.of("rails"));

        String description = FormDocs.describe(form);

        assertFalse(description.contains("'"), "the book should not show the raw quoted element names: " + description);
        assertTrue(Character.isUpperCase(description.charAt(0)), "should start with a capital: " + description);
        assertTrue(description.endsWith("."), "should end as a sentence: " + description);
    }

    @Test
    @DisplayName("an any node's alternatives survive into the sentence as 'or', not as separate requirements")
    void anyBecomesOr()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        leaf.soulhome.structures.BuiltinFormClauses.registerAll(registry);

        var above = registry.get(leaf.soulhome.structures.core.FormClauseType.Kind.RELATION, "above").orElseThrow();
        var inside = registry.get(leaf.soulhome.structures.core.FormClauseType.Kind.RELATION, "inside").orElseThrow();

        FormClause root = new AnyClause(List.of(
                new WeightedClause(1.0, above.create(paramsOfTo(above, "rails", "surface"))),
                new WeightedClause(1.0, inside.create(paramsOfTo(inside, "surface", "rails")))));

        Form form = new Form(
                "circuit", 6.0, "arrangement",
                Map.of(
                        "rails", BlockMatcher.ofTags("minecraft:rails"),
                        "surface", BlockMatcher.ofBlocks("minecraft:ice")),
                root, Set.of("rails", "surface"));

        String description = FormDocs.describe(form);

        assertTrue(description.contains(" or "), "expected alternatives joined by 'or': " + description);
    }

    @Test
    @DisplayName("a clause that cannot describe itself fails the datagen run rather than shipping a silent gap")
    void undescribableClauseThrows()
    {
        Form form = new Form(
                "broken", 1.0, "arrangement",
                Map.of("rails", BlockMatcher.ofTags("minecraft:rails")),
                new SilentClause(), Set.of("rails"));

        assertThrows(IllegalStateException.class, () -> FormDocs.describe(form));
    }

    private static ClauseParams paramsOfTo(leaf.soulhome.structures.core.FormClauseType type, String of, String to)
    {
        ClauseParams.Builder builder = ClauseParams.builder();

        for (var spec : type.params())
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

    private record SilentClause() implements FormClause
    {
        @Override
        public String typeId()
        {
            return "test:silent";
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
}
