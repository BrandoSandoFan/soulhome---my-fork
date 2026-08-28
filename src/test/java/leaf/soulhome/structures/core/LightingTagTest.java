/*
 * File created ~ 28 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #45: {@code soulhome:lighting} was a twelve-entry hand-written list missing most of what a
 * player actually builds with, redstone lamps most of all. Reads the shipped tag files directly -
 * the same "read what the game actually ships" approach {@link ArchetypeJsonReader} uses for
 * archetypes - so a typo'd or reverted addition shows up as a failing test rather than as a room
 * that silently stops scoring its lighting.
 */
class LightingTagTest
{
    private static final Path TAGS_DIRECTORY =
            Path.of("src", "main", "resources", "data", "soulhome", "tags", "blocks");

    @Test
    @DisplayName("soulhome:lighting includes the vanilla light sources #45 reported as missing")
    void includesTheReportedGaps()
    {
        Set<String> values = readValues("lighting.json");

        List<String> expected = List.of(
                "minecraft:redstone_lamp",
                "minecraft:jack_o_lantern",
                "minecraft:ochre_froglight",
                "minecraft:verdant_froglight",
                "minecraft:pearlescent_froglight",
                "minecraft:glow_lichen",
                "minecraft:redstone_torch",
                "minecraft:redstone_wall_torch",
                "minecraft:amethyst_cluster",
                "minecraft:small_amethyst_bud",
                "minecraft:medium_amethyst_bud",
                "minecraft:large_amethyst_bud",
                "minecraft:sea_pickle",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:respawn_anchor",
                "minecraft:cave_vines",
                "minecraft:cave_vines_plant",
                "minecraft:light");

        for (String block : expected)
        {
            assertTrue(values.contains(block), block + " should be tagged " + "soulhome:lighting");
        }
    }

    @Test
    @DisplayName("soulhome:lighting does not claim blocks another signal already owns")
    void excludesTheDocumentedOverlaps()
    {
        Set<String> values = readValues("lighting.json");

        // magma_block is hearth's own heat signal, crying_obsidian is soulhome:arcane, and
        // lava_cauldron is soulhome:alchemy_vessels - which soulhome:alchemy_lab scores alongside
        // soulhome:lighting, so claiming it here would let one block placed once count twice
        // toward the same archetype's score
        assertFalse(values.contains("minecraft:magma_block"));
        assertFalse(values.contains("minecraft:crying_obsidian"));
        assertFalse(values.contains("minecraft:lava_cauldron"));
    }

    @Test
    @DisplayName("none of soulhome:lighting's new entries are claimed by another soulhome: signal tag")
    void newLightingEntriesDoNotOverlapAnotherTag() throws IOException
    {
        if (!Files.isDirectory(TAGS_DIRECTORY))
        {
            return;
        }

        // only this test's own additions are checked - a pre-existing overlap elsewhere in the
        // shipped tags is a different bug, not one #45 is responsible for introducing or fixing
        Set<String> added = Set.of(
                "minecraft:redstone_lamp", "minecraft:jack_o_lantern", "minecraft:ochre_froglight",
                "minecraft:verdant_froglight", "minecraft:pearlescent_froglight", "minecraft:glow_lichen",
                "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:amethyst_cluster",
                "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud", "minecraft:large_amethyst_bud",
                "minecraft:sea_pickle", "minecraft:beacon", "minecraft:conduit", "minecraft:respawn_anchor",
                "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:light");

        try (var files = Files.list(TAGS_DIRECTORY))
        {
            for (Path file : files.sorted().toList())
            {
                final String tagName = file.getFileName().toString().replace(".json", "");

                if (!file.getFileName().toString().endsWith(".json") || tagName.equals("lighting"))
                {
                    continue;
                }

                for (String value : readValues(file))
                {
                    assertFalse(added.contains(value),
                            value + " is now in both soulhome:lighting and soulhome:" + tagName
                                    + " - it double-counts for any archetype that scores both");
                }
            }
        }
    }

    private static Set<String> readValues(String fileName)
    {
        return readValues(TAGS_DIRECTORY.resolve(fileName));
    }

    private static Set<String> readValues(Path file)
    {
        try (Reader reader = Files.newBufferedReader(file))
        {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = json.getAsJsonArray("values");

            Set<String> values = new LinkedHashSet<>();

            for (JsonElement element : array)
            {
                values.add(element.getAsString());
            }

            return values;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
