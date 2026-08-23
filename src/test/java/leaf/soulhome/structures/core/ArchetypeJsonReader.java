/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leaf.soulhome.structures.BuiltinFormClauses;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads archetype JSON with Gson, so tests can be run against the definitions the mod actually
 * ships rather than against Java copies of them that quietly drift.
 *
 * <p>This deliberately mirrors the field names and defaults of the production codec in
 * {@code ArchetypeCodecs} - it is not the same code path, and it cannot be: the production path
 * runs on DataFixerUpper, which only exists inside a Minecraft classpath. What it does buy is that
 * a typo in a shipped weight, a renamed tag, or a threshold nobody can reach shows up as a failing
 * test rather than as a silent gameplay bug.
 */
public final class ArchetypeJsonReader
{
    /** Where the shipped definitions live, relative to the repository root. */
    public static final Path SHIPPED_DIRECTORY =
            Path.of("src", "main", "resources", "data", "soulhome", "soulhome_archetypes");

    static
    {
        // the Minecraft-free test suite never runs SoulHome's bootstrap, so nothing else populates
        // FormClauseRegistry.BUILTIN with the real #29-#32 vocabulary before a test that reads
        // against it by default (see #read(JsonObject)) runs - safe to call more than once, see
        // BuiltinFormClauses#registerAll(FormClauseRegistry)
        BuiltinFormClauses.registerAll(FormClauseRegistry.BUILTIN);
    }

    private ArchetypeJsonReader()
    {
    }

    /** Every archetype the mod ships, keyed on the id the loader would derive from the file path. */
    public static List<ArchetypeDefinition> shipped() throws IOException
    {
        if (!Files.isDirectory(SHIPPED_DIRECTORY))
        {
            throw new IllegalStateException(
                    "Cannot find " + SHIPPED_DIRECTORY.toAbsolutePath()
                            + " - these tests must run with the repository root as the working directory");
        }

        List<ArchetypeDefinition> definitions = new ArrayList<>();

        try (var files = Files.list(SHIPPED_DIRECTORY))
        {
            for (Path file : files.sorted().toList())
            {
                if (!file.getFileName().toString().endsWith(".json"))
                {
                    continue;
                }

                final String name = file.getFileName().toString().replace(".json", "");

                try (Reader reader = Files.newBufferedReader(file))
                {
                    definitions.add(read(JsonParser.parseReader(reader).getAsJsonObject())
                            .withId("soulhome:" + name));
                }
            }
        }

        return definitions;
    }

    public static ArchetypeDefinition byId(List<ArchetypeDefinition> definitions, String id)
    {
        for (ArchetypeDefinition definition : definitions)
        {
            if (definition.id().equals(id))
            {
                return definition;
            }
        }

        throw new IllegalArgumentException("No archetype '" + id + "' among " + definitions.stream()
                .map(ArchetypeDefinition::id).toList());
    }

    public static ArchetypeDefinition read(JsonObject json)
    {
        return read(json, FormClauseRegistry.BUILTIN);
    }

    /**
     * @param registry which {@code shape}/{@code relation} clause types {@code structures} may
     *                 resolve against. Tests exercising the grammar's own plumbing pass their own
     *                 registry with fake types registered; {@link #shipped()} uses
     *                 {@link FormClauseRegistry#BUILTIN}, exactly as the production loader does.
     */
    public static ArchetypeDefinition read(JsonObject json, FormClauseRegistry registry)
    {
        List<RegionType> regionTypes = new ArrayList<>();

        for (String name : stringList(json, "region_types"))
        {
            regionTypes.add(RegionType.byName(name)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown region type '" + name + "'")));
        }

        return new ArchetypeDefinition(
                ArchetypeDefinition.PLACEHOLDER_ID,
                json.get("display_name").getAsString(),
                regionTypes,
                json.has("min_volume") ? json.get("min_volume").getAsInt() : 1,
                readRequirements(json),
                readSignals(json, "signals"),
                readSignals(json, "detractors"),
                readTiers(json),
                readBuffs(json),
                readStructures(json, registry));
    }

    private static List<ArchetypeDefinition.Requirement> readRequirements(JsonObject json)
    {
        List<ArchetypeDefinition.Requirement> requirements = new ArrayList<>();

        for (JsonElement element : array(json, "requirements"))
        {
            JsonObject entry = element.getAsJsonObject();
            requirements.add(new ArchetypeDefinition.Requirement(
                    readMatcher(entry.getAsJsonObject("match")),
                    entry.has("min_count") ? entry.get("min_count").getAsInt() : 1));
        }

        return requirements;
    }

    private static List<ArchetypeDefinition.Signal> readSignals(JsonObject json, String field)
    {
        List<ArchetypeDefinition.Signal> signals = new ArrayList<>();

        for (JsonElement element : array(json, field))
        {
            JsonObject entry = element.getAsJsonObject();
            signals.add(new ArchetypeDefinition.Signal(
                    readMatcher(entry.getAsJsonObject("match")),
                    entry.get("weight").getAsDouble(),
                    entry.has("role") ? entry.get("role").getAsString() : ArchetypeDefinition.Signal.DEFAULT_ROLE,
                    entry.has("cap") ? entry.get("cap").getAsInt() : ArchetypeDefinition.DEFAULT_CAP));
        }

        return signals;
    }

    private static List<ArchetypeDefinition.Tier> readTiers(JsonObject json)
    {
        List<ArchetypeDefinition.Tier> tiers = new ArrayList<>();

        for (JsonElement element : array(json, "tiers"))
        {
            JsonObject entry = element.getAsJsonObject();
            tiers.add(new ArchetypeDefinition.Tier(
                    entry.get("min_score").getAsDouble(),
                    entry.get("tier").getAsInt()));
        }

        return tiers;
    }

    private static List<ArchetypeDefinition.BuffSpec> readBuffs(JsonObject json)
    {
        List<ArchetypeDefinition.BuffSpec> buffs = new ArrayList<>();

        for (JsonElement element : array(json, "buffs"))
        {
            JsonObject entry = element.getAsJsonObject();
            buffs.add(new ArchetypeDefinition.BuffSpec(
                    entry.get("type").getAsString(),
                    entry.get("per_tier").getAsDouble(),
                    entry.has("max") ? entry.get("max").getAsDouble() : Double.MAX_VALUE));
        }

        return buffs;
    }

    private static BlockMatcher readMatcher(JsonObject json)
    {
        return new BlockMatcher(stringList(json, "block"), stringList(json, "tag"));
    }

    // region structural forms - mirrors the shape/relation/all/any dispatch in the production
    // codec (structures.FormCodecs), reading the same ClauseParamSpec each FormClauseType declares

    private static List<Form> readStructures(JsonObject json, FormClauseRegistry registry)
    {
        List<Form> forms = new ArrayList<>();

        for (JsonElement element : array(json, "structures"))
        {
            Form form = readForm(element.getAsJsonObject(), registry);

            if (form != null)
            {
                forms.add(form);
            }
        }

        return forms;
    }

    /** {@code null} means the form's clause tree resolved to nothing and the whole form is dropped. */
    public static Form readForm(JsonObject json, FormClauseRegistry registry)
    {
        String name = json.has("name") ? json.get("name").getAsString() : null;
        double weight = json.has("weight") ? json.get("weight").getAsDouble() : 1.0;
        String role = json.has("role") ? json.get("role").getAsString() : null;

        Map<String, BlockMatcher> elements = new LinkedHashMap<>();

        if (json.has("elements"))
        {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("elements").entrySet())
            {
                elements.put(entry.getKey(), readMatcher(entry.getValue().getAsJsonObject()));
            }
        }

        Set<String> referenced = new LinkedHashSet<>();
        FormClause root = readClauseBody(json, registry, referenced);

        if (root == null || root instanceof UnknownClause)
        {
            return null;
        }

        return new Form(name, weight, role, elements, root, referenced);
    }

    /**
     * Reads whichever of {@code shape}/{@code relation}/{@code all}/{@code any} the object carries.
     * {@code null} means none of those keys were present at all - a structurally empty clause
     * object. A recognised-but-unresolvable clause comes back as {@link UnknownClause} instead, so
     * a caller can tell "nothing here" apart from "something here that did not work out".
     */
    private static FormClause readClauseBody(JsonObject json, FormClauseRegistry registry, Set<String> referencedOut)
    {
        if (json.has("shape"))
        {
            return readLeaf(json, "shape", FormClauseType.Kind.SHAPE, registry, referencedOut);
        }

        if (json.has("relation"))
        {
            return readLeaf(json, "relation", FormClauseType.Kind.RELATION, registry, referencedOut);
        }

        if (json.has("all"))
        {
            return readComposite(json.getAsJsonArray("all"), registry, referencedOut, true);
        }

        if (json.has("any"))
        {
            return readComposite(json.getAsJsonArray("any"), registry, referencedOut, false);
        }

        return null;
    }

    private static FormClause readComposite(JsonArray array, FormClauseRegistry registry, Set<String> referencedOut, boolean all)
    {
        List<WeightedClause> children = new ArrayList<>();

        for (JsonElement entry : array)
        {
            JsonObject entryJson = entry.getAsJsonObject();
            double weight = entryJson.has("weight") ? entryJson.get("weight").getAsDouble() : 1.0;
            FormClause clause = readClauseBody(entryJson, registry, referencedOut);

            if (clause != null && !(clause instanceof UnknownClause))
            {
                children.add(new WeightedClause(weight, clause));
            }
        }

        if (children.isEmpty())
        {
            return null;
        }

        return all ? new AllClause(children) : new AnyClause(children);
    }

    private static FormClause readLeaf(
            JsonObject json, String field, FormClauseType.Kind kind, FormClauseRegistry registry, Set<String> referencedOut)
    {
        String id = json.get(field).getAsString().toLowerCase(Locale.ROOT);
        Optional<FormClauseType> type = registry.get(kind, id);

        if (type.isEmpty())
        {
            return new UnknownClause(id);
        }

        try
        {
            ClauseParams.Builder params = ClauseParams.builder();

            for (ClauseParamSpec spec : type.get().params())
            {
                if (!json.has(spec.name()))
                {
                    if (spec.required())
                    {
                        return new UnknownClause(id);
                    }

                    params.put(spec.name(), spec.defaultValue());
                    continue;
                }

                JsonElement raw = json.get(spec.name());

                Object value = switch (spec.type())
                {
                    case DOUBLE -> raw.getAsDouble();
                    case INT -> raw.getAsInt();
                    case STRING, ELEMENT -> raw.getAsString();
                };

                params.put(spec.name(), value);

                if (spec.type() == ClauseParamSpec.Type.ELEMENT)
                {
                    referencedOut.add((String) value);
                }
            }

            return type.get().create(params.build());
        }
        catch (RuntimeException exception)
        {
            return new UnknownClause(id);
        }
    }

    // endregion

    /** Accepts a bare string or an array of strings, exactly as the production codec does. */
    private static List<String> stringList(JsonObject json, String field)
    {
        if (!json.has(field))
        {
            return List.of();
        }

        JsonElement element = json.get(field);

        if (element.isJsonArray())
        {
            List<String> values = new ArrayList<>();

            for (JsonElement entry : element.getAsJsonArray())
            {
                values.add(entry.getAsString().toLowerCase(Locale.ROOT));
            }

            return values;
        }

        return List.of(element.getAsString().toLowerCase(Locale.ROOT));
    }

    private static JsonArray array(JsonObject json, String field)
    {
        return json.has(field) ? json.getAsJsonArray(field) : new JsonArray();
    }
}
