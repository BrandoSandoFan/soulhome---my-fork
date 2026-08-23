/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import leaf.soulhome.structures.BuiltinFormClauses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormClauseRegistryTest
{
    private static final FormClauseType LOOP = fakeType("loop", FormClauseType.Kind.SHAPE);
    private static final FormClauseType ABOVE = fakeType("above", FormClauseType.Kind.RELATION);

    @Test
    @DisplayName("a registered type is found by its own kind")
    void registeredTypeIsFound()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        registry.register(LOOP);

        assertEquals(LOOP, registry.get(FormClauseType.Kind.SHAPE, "loop").orElseThrow());
    }

    @Test
    @DisplayName("the same id under the wrong kind is not found")
    void wrongKindIsNotFound()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        registry.register(LOOP);

        assertTrue(registry.get(FormClauseType.Kind.RELATION, "loop").isEmpty());
    }

    @Test
    @DisplayName("an id nothing registered is not found")
    void unknownIdIsNotFound()
    {
        assertTrue(new FormClauseRegistry().get(FormClauseType.Kind.SHAPE, "spiral").isEmpty());
    }

    @Test
    @DisplayName("registering the same id twice fails loudly rather than silently overwriting")
    void duplicateRegistrationThrows()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        registry.register(LOOP);

        assertThrows(IllegalStateException.class, () -> registry.register(LOOP));
    }

    @Test
    @DisplayName("Phase 2 (#29-#32) registers the shape/relation vocabulary onto BUILTIN")
    void builtinShipsThePhase2Vocabulary()
    {
        BuiltinFormClauses.registerAll(FormClauseRegistry.BUILTIN);

        assertTrue(FormClauseRegistry.BUILTIN.get(FormClauseType.Kind.SHAPE, "soulhome:loop").isPresent());
        assertTrue(FormClauseRegistry.BUILTIN.get(FormClauseType.Kind.RELATION, "above").isPresent());
        assertTrue(FormClauseRegistry.BUILTIN.get(FormClauseType.Kind.RELATION, "within").isPresent());
        assertTrue(FormClauseRegistry.BUILTIN.get(FormClauseType.Kind.SHAPE, "soulhome:cluster").isPresent());
    }

    @Test
    @DisplayName("registering distinct ids under both kinds does not collide")
    void distinctKindsCoexist()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        registry.register(LOOP);
        registry.register(ABOVE);

        assertEquals(LOOP, registry.get(FormClauseType.Kind.SHAPE, "loop").orElseThrow());
        assertEquals(ABOVE, registry.get(FormClauseType.Kind.RELATION, "above").orElseThrow());
    }

    private static FormClauseType fakeType(String id, FormClauseType.Kind kind)
    {
        return new FormClauseType()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public Kind kind()
            {
                return kind;
            }

            @Override
            public List<ClauseParamSpec> params()
            {
                return List.of();
            }

            @Override
            public FormClause create(ClauseParams params)
            {
                return FakeClause.of(1.0);
            }

            @Override
            public java.util.Map<String, Object> encode(FormClause clause)
            {
                return java.util.Map.of();
            }
        };
    }
}
