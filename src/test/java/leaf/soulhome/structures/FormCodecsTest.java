/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import leaf.soulhome.structures.core.AllClause;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.ClauseParamSpec;
import leaf.soulhome.structures.core.ClauseParams;
import leaf.soulhome.structures.core.Form;
import leaf.soulhome.structures.core.FormClause;
import leaf.soulhome.structures.core.FormClauseRegistry;
import leaf.soulhome.structures.core.FormClauseType;
import leaf.soulhome.structures.core.FormResult;
import leaf.soulhome.structures.core.RegionGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link FormCodecs} - and, through it, the {@code structures} field on
 * {@link ArchetypeCodecs#ARCHETYPE_WITH_ID} - against {@link JsonOps}. Sits outside
 * {@code leaf.soulhome.structures.core} on purpose: DataFixerUpper is only on the game's classpath,
 * and this file needs it.
 *
 * <p>Builds its own {@link FormClauseRegistry} rather than touching
 * {@link FormClauseRegistry#BUILTIN}, so these fake clause types can never leak into another test -
 * see {@link FormCodecs#forRegistry}.
 */
class FormCodecsTest
{
    @Test
    @DisplayName("an archetype with no 'structures' key round-trips exactly as it does today")
    void noStructuresRoundTrips()
    {
        ArchetypeDefinition original = new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(), 1,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        ArchetypeDefinition roundTripped = ArchetypeCodecs.ARCHETYPE_WITH_ID
                .parse(JsonOps.INSTANCE, ArchetypeCodecs.ARCHETYPE_WITH_ID
                        .encodeStart(JsonOps.INSTANCE, original).result().orElseThrow())
                .result()
                .orElseThrow();

        assertTrue(roundTripped.structures().isEmpty());
    }

    @Test
    @DisplayName("round-tripping through FORM preserves every element, clause and parameter")
    void roundTripPreservesShape()
    {
        FormClauseRegistry registry = new FormClauseRegistry();
        LoopClauseType loopType = new LoopClauseType();
        registry.register(loopType);

        Codec<Form> codec = FormCodecs.forRegistry(registry);

        Form original = new Form(
                "circuit", 3.5, "circuit",
                Map.of("rails", BlockMatcher.ofTags("minecraft:rails"), "surface", BlockMatcher.ofBlocks("minecraft:ice")),
                loopType.create(ClauseParams.builder().put("of", "rails").put("min_cells", 12).build()),
                Set.of("rails"));

        var encoded = codec.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
        Form decoded = codec.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(original.name(), decoded.name());
        assertEquals(original.weight(), decoded.weight());
        assertEquals(original.role(), decoded.role());
        assertEquals(original.elements(), decoded.elements());
        assertEquals(original.root().typeId(), decoded.root().typeId());
        assertEquals(List.of(), decoded.validationErrors());
    }

    @Test
    @DisplayName("the canonical all-of-a-loop-and-any-of-relations example round-trips through JsonOps")
    void canonicalExampleRoundTrips()
    {
        JsonObject json = JsonParser.parseString("""
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
                """).getAsJsonObject();

        FormClauseRegistry registry = new FormClauseRegistry();
        registry.register(new LoopClauseType());
        registry.register(new RelationClauseType("above"));
        registry.register(new RelationClauseType("surrounds"));
        registry.register(new RelationClauseType("inside"));

        Form form = FormCodecs.forRegistry(registry).parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertTrue(form.validationErrors().isEmpty(), () -> String.join("; ", form.validationErrors()));
        assertTrue(form.root() instanceof AllClause);
        assertEquals(2, ((AllClause) form.root()).children().size());
    }

    @Test
    @DisplayName("an unknown clause id that empties a form drops the form, not the archetype")
    void unknownClauseEmptyingFormDropsOnlyTheForm()
    {
        JsonObject json = JsonParser.parseString("""
                {
                  "display_name": "archetype.soulhome.test",
                  "signals": [ { "match": { "tag": "minecraft:rails" }, "weight": 1.0 } ],
                  "tiers": [ { "min_score": 1.0, "tier": 1 } ],
                  "structures": [
                    { "name": "bogus", "elements": { "rails": { "tag": "minecraft:rails" } },
                      "shape": "spiral", "of": "rails" }
                  ]
                }
                """).getAsJsonObject();

        ArchetypeDefinition definition = ArchetypeCodecs.ARCHETYPE.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertTrue(definition.structures().isEmpty());
        assertEquals(List.of(), definition.validationErrors());
    }

    private record LoopClause(String of, int minCells) implements FormClause
    {
        @Override
        public String typeId()
        {
            return "loop";
        }

        @Override
        public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
        {
            return FormResult.of(1.0, "the " + this.of + " form a loop");
        }

        @Override
        public String describe()
        {
            return "the " + this.of + " form a loop";
        }

        @Override
        public List<String> validationErrors(Set<String> elementNames)
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

    private record RelationClause(String relationId, String of, String to) implements FormClause
    {
        @Override
        public String typeId()
        {
            return this.relationId;
        }

        @Override
        public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
        {
            return FormResult.of(1.0, "");
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

    private static final class RelationClauseType implements FormClauseType
    {
        private final String id;

        private RelationClauseType(String id)
        {
            this.id = id;
        }

        @Override
        public String id()
        {
            return this.id;
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
            return new RelationClause(this.id, params.getElement("of"), params.getElement("to"));
        }

        @Override
        public Map<String, Object> encode(FormClause clause)
        {
            RelationClause relation = (RelationClause) clause;
            return Map.of("of", relation.of(), "to", relation.to());
        }
    }
}
