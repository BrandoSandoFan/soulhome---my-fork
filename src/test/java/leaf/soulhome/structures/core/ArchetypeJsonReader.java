/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                readBuffs(json));
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
