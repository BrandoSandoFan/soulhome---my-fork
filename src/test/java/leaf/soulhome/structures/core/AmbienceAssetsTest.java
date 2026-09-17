/*
 * File created ~ 17 - 9 - 2026
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every voice has a sound event, and every event names a file that is actually in the tree (#209).
 *
 * <p>This is the guard the block tags already had and the sounds did not. The failure it exists to
 * catch is silent in the worst way: a {@code SoundEvent} whose {@code sounds.json} entry is missing
 * or misspelt registers perfectly happily and then plays nothing at all, so the symptom is a soul
 * that has simply gone quiet, months after the commit that did it. An unshipped asset should fail
 * the build rather than the player's ears.
 *
 * <p>Reads the shipped resources directly rather than a fixture, the way {@link LightingTagTest}
 * reads the shipped tag files, so what is checked is what actually ships. It runs offline - no
 * Minecraft, no registry - which is why it lives in {@code structures.core} with the rest of the
 * suite despite being about assets.
 */
class AmbienceAssetsTest
{
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "soulhome");

    private static final Path SOUNDS_JSON = ASSETS.resolve("sounds.json");

    /** Mirrors {@code SoundsRegistry.voiceKey} - which cannot be called here, being Forge-side. */
    private static String voiceKey(SoulVoice voice)
    {
        return "ambience.voice." + voice.name().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("every SoulVoice has an entry in sounds.json")
    void everyVoiceIsRegistered()
    {
        final JsonObject sounds = readSounds();

        for (SoulVoice voice : SoulVoice.values())
        {
            assertTrue(sounds.has(voiceKey(voice)),
                    "no sound event for " + voice + " - it would register and then play silence");
        }
    }

    @Test
    @DisplayName("both rank layers of the bed have entries, and they stream")
    void theBedIsRegistered()
    {
        final JsonObject sounds = readSounds();

        for (String key : List.of("ambience.bed.close", "ambience.bed.open"))
        {
            assertTrue(sounds.has(key), "no sound event for the bed's " + key);

            final JsonArray entries = sounds.getAsJsonObject(key).getAsJsonArray("sounds");

            assertEquals(1, entries.size(), key + " is one long loop, not a set of variants");
            assertTrue(entries.get(0).getAsJsonObject().get("stream").getAsBoolean(),
                    key + " is over a minute long and has to be streamed rather than held in memory");
        }
    }

    @Test
    @DisplayName("every sound named in sounds.json is a file in the tree")
    void everyNamedFileExists()
    {
        final JsonObject sounds = readSounds();
        final List<String> missing = new ArrayList<>();

        for (String key : sounds.keySet())
        {
            for (String name : namesIn(sounds, key))
            {
                // "soulhome:ambience/warm_1" is assets/soulhome/sounds/ambience/warm_1.ogg
                final String path = name.substring(name.indexOf(':') + 1);

                if (!Files.isRegularFile(ASSETS.resolve("sounds").resolve(path + ".ogg")))
                {
                    missing.add(key + " -> " + name);
                }
            }
        }

        assertTrue(missing.isEmpty(), "sounds.json names files that are not in the tree: " + missing);
    }

    @Test
    @DisplayName("every voice has more than one variant, so no evening is a loop of one sound")
    void everyVoiceHasVariants()
    {
        final JsonObject sounds = readSounds();

        for (SoulVoice voice : SoulVoice.values())
        {
            assertTrue(namesIn(sounds, voiceKey(voice)).size() > 1,
                    voice + " has a single variant - the variety used to come from a coin flip in "
                            + "Java and now has to come from here");
        }
    }

    @Test
    @DisplayName("no voice rolls off faster than the band the placement maths throws it into")
    void attenuationClearsTheOneShotBand()
    {
        final JsonObject sounds = readSounds();
        final int floor = (int) SoulAmbience.OneShotPlacement.AUDIBLE_RADIUS;

        for (SoulVoice voice : SoulVoice.values())
        {
            for (JsonElement entry : sounds.getAsJsonObject(voiceKey(voice)).getAsJsonArray("sounds"))
            {
                final JsonObject sound = entry.getAsJsonObject();

                assertTrue(sound.has("attenuation_distance"),
                        voice + " leaves its range to the default - which is how far the source"
                                + " carries, and is a decision worth writing down");

                // a voice that rolls off inside the band oneShotPlacement throws it into is #208
                // again, one voice at a time: audible in the maths and silent at the ear
                assertTrue(sound.get("attenuation_distance").getAsInt() >= floor,
                        voice + " rolls off before the furthest a one-shot of it is thrown");
            }
        }
    }

    @Test
    @DisplayName("nothing in the palette is a vanilla event any more")
    void nothingIsBorrowed()
    {
        final JsonObject sounds = readSounds();

        for (String key : sounds.keySet())
        {
            for (String name : namesIn(sounds, key))
            {
                assertTrue(name.startsWith("soulhome:"),
                        key + " plays " + name + ", which belongs to somebody else and means "
                                + "something of its own - see #209");
            }
        }
    }

    @Test
    @DisplayName("the assets record where they came from")
    void provenanceIsRecorded()
    {
        final Path sources = ASSETS.resolve("sounds").resolve("ambience").resolve("SOURCES.md");

        assertTrue(Files.isRegularFile(sources),
                "\"CC0\" and \"CC-BY\" are different promises and the repo should be able to prove "
                        + "which one it made (#209)");

        final String text = read(sources);

        assertTrue(text.contains("tools/ambience/generate.py"),
                "the provenance record should name what produced these files");
        assertFalse(text.isBlank());
    }

    private static List<String> namesIn(JsonObject sounds, String key)
    {
        final List<String> names = new ArrayList<>();

        for (JsonElement entry : sounds.getAsJsonObject(key).getAsJsonArray("sounds"))
        {
            names.add(entry.isJsonObject()
                    ? entry.getAsJsonObject().get("name").getAsString()
                    : entry.getAsString());
        }

        return names;
    }

    private static JsonObject readSounds()
    {
        try (Reader reader = Files.newBufferedReader(SOUNDS_JSON))
        {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException("could not read " + SOUNDS_JSON, exception);
        }
    }

    private static String read(Path path)
    {
        try
        {
            return Files.readString(path);
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException("could not read " + path, exception);
        }
    }
}
