/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@code shape}/{@code relation}/{@code all}/{@code any} clause grammar through
 * {@link ArchetypeJsonReader}, against a throwaway {@link FormClauseRegistry} carrying fake clause
 * types - #27 ships the grammar itself, not any real {@code shape}/{@code relation} clause (those
 * are #29 through #32), so these fakes stand in for "some clause type is registered" the way #29's
 * real {@code loop} eventually will.
 *
 * <p>The production codec ({@code structures.FormCodecs}) is meant to resolve a {@code shape}/
 * {@code relation}/{@code all}/{@code any} object exactly the way {@link ArchetypeJsonReader} does
 * here - same {@link ClauseParamSpec} list per type, same leniency - even though the two are
 * necessarily separate implementations (one DataFixerUpper, one Gson; see the class doc on
 * {@link ArchetypeJsonReader}).
 */
class FormParsingTest
{
    private static final BlockMatcher RAILS = BlockMatcher.ofTags("minecraft:rails");
    private static final BlockMatcher ICE = BlockMatcher.ofBlocks("minecraft:ice");

    @Test
    @DisplayName("a form with no 'structures' key loads exactly as it does today")
    void noStructuresKeyLoadsAsToday()
    {
        JsonObject json = parse("""
                {
                  "display_name": "archetype.soulhome.test",
                  "signals": [ { "match": { "tag": "minecraft:rails" }, "weight": 1.0 } ],
                  "tiers": [ { "min_score": 1.0, "tier": 1 } ]
                }
                """);

        ArchetypeDefinition definition = ArchetypeJsonReader.read(json);

        assertTrue(definition.structures().isEmpty());
        assertEquals(List.of(), definition.validationErrors());
    }

    @Test
    @DisplayName("a bare single-element shape leaf parses and evaluates")
    void bareShapeLeafParses()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "weight": 8.0,
                  "elements": { "rails": { "tag": "minecraft:rails" } },
                  "shape": "loop",
                  "of": "rails"
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertEquals("circuit", form.name());
        assertEquals(8.0, form.weight());
        assertEquals(java.util.Set.of("rails"), form.referencedElements());
        assertEquals(List.of(), form.validationErrors());
        assertEquals(1.0, form.evaluate(RegionGeometry.EMPTY).confidence(), 1e-9);
    }

    @Test
    @DisplayName("the canonical all-of-a-loop-and-any-of-relations example parses and is valid")
    void canonicalExampleParses()
    {
        FormClauseRegistry registry = registryWithLoop();
        registry.register(fakeRelation("above"));
        registry.register(fakeRelation("surrounds"));
        registry.register(fakeRelation("inside"));

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "weight": 8.0,
                  "role": "circuit",
                  "elements": {
                    "rails":   { "tag": "minecraft:rails" },
                    "surface": { "block": ["minecraft:ice", "minecraft:packed_ice"] }
                  },
                  "all": [
                    { "shape": "loop", "of": "rails", "weight": 2.0 },
                    { "any": [
                      { "relation": "above",     "of": "rails",   "to": "surface" },
                      { "relation": "surrounds", "of": "surface", "to": "rails" },
                      { "relation": "inside",    "of": "surface", "to": "rails" }
                    ] }
                  ]
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertTrue(form.validationErrors().isEmpty(), () -> String.join("; ", form.validationErrors()));
        assertEquals("circuit", form.role());
        assertTrue(form.root() instanceof AllClause);
    }

    @Test
    @DisplayName("an unknown clause id inside an 'any' leaves the other alternatives working")
    void unknownClauseInsideAnyIsSkipped()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "elements": { "rails": { "tag": "minecraft:rails" } },
                  "any": [
                    { "shape": "spiral", "of": "rails" },
                    { "shape": "loop", "of": "rails" }
                  ]
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertTrue(form.root() instanceof AnyClause);
        assertEquals(1, ((AnyClause) form.root()).children().size());
        assertEquals(1.0, form.evaluate(RegionGeometry.EMPTY).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an unknown clause id that empties a form drops the form, not the archetype")
    void unknownClauseEmptyingFormDropsOnlyTheForm()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "display_name": "archetype.soulhome.test",
                  "signals": [ { "match": { "tag": "minecraft:rails" }, "weight": 1.0 } ],
                  "tiers": [ { "min_score": 1.0, "tier": 1 } ],
                  "structures": [
                    { "name": "bogus", "elements": { "rails": { "tag": "minecraft:rails" } },
                      "shape": "spiral", "of": "rails" }
                  ]
                }
                """);

        ArchetypeDefinition definition = ArchetypeJsonReader.read(json, registry);

        assertTrue(definition.structures().isEmpty());
        assertEquals(List.of(), definition.validationErrors());
    }

    @Test
    @DisplayName("a clause naming an undeclared element is a validation error")
    void clauseNamingUndeclaredElementIsAnError()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "elements": { "rails": { "tag": "minecraft:rails" } },
                  "shape": "loop",
                  "of": "surface"
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertTrue(form.validationErrors().contains("a clause refers to undeclared element 'surface'"));
    }

    @Test
    @DisplayName("a form with five elements is a validation error")
    void fiveElementsIsAnError()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "elements": {
                    "a": { "block": "minecraft:stone" }, "b": { "block": "minecraft:stone" },
                    "c": { "block": "minecraft:stone" }, "d": { "block": "minecraft:stone" },
                    "e": { "block": "minecraft:stone" }
                  },
                  "shape": "loop",
                  "of": "a"
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertTrue(form.validationErrors().stream().anyMatch(error -> error.contains("more than the 4")));
    }

    @Test
    @DisplayName("a form with one element loads with a warning, not an error")
    void oneElementIsWarningNotError()
    {
        Form form = ArchetypeJsonReader.readForm(parse("""
                {
                  "name": "circuit",
                  "elements": { "rails": { "tag": "minecraft:rails" } },
                  "shape": "loop",
                  "of": "rails"
                }
                """), registryWithLoop());

        assertEquals(List.of(), form.validationErrors());
        assertEquals(1, form.validationWarnings().size());
    }

    @Test
    @DisplayName("three levels of clause nesting is a validation error")
    void threeLevelsOfNestingIsAnError()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "elements": { "rails": { "tag": "minecraft:rails" } },
                  "all": [
                    { "any": [
                      { "all": [ { "shape": "loop", "of": "rails" } ] }
                    ] }
                  ]
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertTrue(form.validationErrors().stream().anyMatch(error -> error.contains("nesting")));
    }

    @Test
    @DisplayName("a form in 'requirements' is impossible to write - there is nowhere to put it")
    void requirementsCannotNameAForm()
    {
        // Requirement only ever carries a BlockMatcher and a minCount (see
        // ArchetypeDefinition.Requirement) - neither of which is a Form - so this test exists to
        // make a future field added to Requirement reckon with this rule rather than silently
        // reopening the gate the epic (#25) closes on purpose.
        for (var component : ArchetypeDefinition.Requirement.class.getRecordComponents())
        {
            assertTrue(component.getType() != Form.class, "Requirement must never be able to name a Form");
        }
    }

    @Test
    @DisplayName("round-tripping through readForm preserves every element, clause and parameter")
    void roundTripPreservesShape()
    {
        FormClauseRegistry registry = registryWithLoop();

        JsonObject json = parse("""
                {
                  "name": "circuit",
                  "weight": 3.5,
                  "role": "circuit",
                  "elements": {
                    "rails":   { "tag": "minecraft:rails" },
                    "surface": { "block": "minecraft:ice" }
                  },
                  "shape": "loop",
                  "of": "rails",
                  "min_cells": 12
                }
                """);

        Form form = ArchetypeJsonReader.readForm(json, registry);

        assertEquals("circuit", form.name());
        assertEquals(3.5, form.weight());
        assertEquals("circuit", form.role());
        assertEquals(java.util.Set.of("rails", "surface"), form.elements().keySet());
        assertEquals(RAILS, form.elements().get("rails"));
        assertEquals(ICE, form.elements().get("surface"));
        assertTrue(form.root() instanceof LoopClause loop && loop.of().equals("rails") && loop.minCells() == 12);
    }

    @Test
    @DisplayName("a clause type's encode is the inverse of its create - what the production codec's encode side leans on")
    void encodeIsTheInverseOfCreate()
    {
        LoopClauseType type = new LoopClauseType();

        FormClause loop = type.create(ClauseParams.builder()
                .put("of", "rails")
                .put("min_cells", 12)
                .build());

        assertEquals(Map.of("of", "rails", "min_cells", 12), type.encode(loop));
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** A minimal fake "shape" clause type, standing in for the real #29 {@code loop}. */
    private static FormClauseRegistry registryWithLoop()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        registry.register(new LoopClauseType());
        return registry;
    }

    /** A minimal fake "relation" clause type between two elements, standing in for #29's real ones. */
    private static FormClauseType fakeRelation(String id)
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
                return Kind.RELATION;
            }

            @Override
            public List<ClauseParamSpec> params()
            {
                return List.of(
                        ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                        ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT));
            }

            @Override
            public FormClause create(ClauseParams params)
            {
                return FakeClause.of(1.0, id);
            }

            @Override
            public Map<String, Object> encode(FormClause clause)
            {
                return Map.of();
            }
        };
    }

    /** Always confidence 1.0 - real grading is #29's job, not this issue's. */
    private record LoopClause(String of, int minCells) implements FormClause
    {
        @Override
        public String typeId()
        {
            return "loop";
        }

        @Override
        public FormResult evaluate(RegionGeometry geometry, java.util.Map<String, BlockMatcher> elements)
        {
            return FormResult.of(1.0, "the " + this.of + " form a loop");
        }

        @Override
        public String describe()
        {
            return "the " + this.of + " form a loop";
        }

        @Override
        public List<String> validationErrors(java.util.Set<String> elementNames)
        {
            return List.of();
        }
    }

    private static final class LoopClauseType implements FormClauseType
    {
        @Override
        public String id()
        {
            return "loop";
        }

        @Override
        public Kind kind()
        {
            return Kind.SHAPE;
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(
                    ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                    ClauseParamSpec.optional("min_cells", ClauseParamSpec.Type.INT, 1));
        }

        @Override
        public FormClause create(ClauseParams params)
        {
            return new LoopClause(params.getElement("of"), params.getInt("min_cells"));
        }

        @Override
        public Map<String, Object> encode(FormClause clause)
        {
            LoopClause loop = (LoopClause) clause;
            return Map.of("of", loop.of(), "min_cells", loop.minCells());
        }
    }
}
