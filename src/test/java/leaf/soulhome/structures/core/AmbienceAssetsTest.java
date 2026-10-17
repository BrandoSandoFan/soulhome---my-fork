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
import leaf.soulhome.structures.core.music.JorbisDecoder;
import leaf.soulhome.structures.core.music.SoulSynth;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    /** Mirrors {@code SoundsRegistry.characterBedKey}, for the same reason. */
    private static String characterBedKey(SoulVoice voice)
    {
        return "ambience.bed.character." + voice.name().toLowerCase(Locale.ROOT);
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
    @DisplayName("every non-base voice has a character layer of the bed, and it streams (#214)")
    void theCharacterBedIsRegistered()
    {
        final JsonObject sounds = readSounds();

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            final String key = characterBedKey(voice);

            assertTrue(sounds.has(key), "no sound event for " + voice + "'s character layer");

            final JsonArray entries = sounds.getAsJsonObject(key).getAsJsonArray("sounds");

            assertEquals(1, entries.size(), key + " is one long loop, not a set of variants");
            assertTrue(entries.get(0).getAsJsonObject().get("stream").getAsBoolean(),
                    key + " is over a minute long and has to be streamed rather than held in memory");
        }

        assertFalse(sounds.has(characterBedKey(SoulVoice.BASE)),
                "the base voice is the place rather than a room in it - it has no character layer");
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
    @DisplayName("suppression has a drone and a count of its own, and the drone streams (#188)")
    void suppressionIsRegistered()
    {
        final JsonObject sounds = readSounds();

        assertTrue(sounds.has("suppression.drone"), "no sound event for suppression's drone");
        assertTrue(sounds.has("suppression.throb"), "no sound event for suppression's count");

        final JsonArray drone = sounds.getAsJsonObject("suppression.drone").getAsJsonArray("sounds");

        assertEquals(1, drone.size(), "the drone is one loop, not a set of variants");
        assertTrue(drone.get(0).getAsJsonObject().get("stream").getAsBoolean(),
                "the drone loops for as long as someone suppressed is in view, and is streamed");

        // a count whose beats differ is harder to count - vanilla's random pick between variants
        // is exactly the variety the ambience wants and this does not
        assertEquals(1, namesIn(sounds, "suppression.throb").size(),
                "every beat of the count should be the same beat");

        for (String key : List.of("suppression.drone", "suppression.throb"))
        {
            // the audio channel is the accessible one, and a player who cannot hear it still gets
            // told something is there, in the subtitles, which is where they will be looking
            assertTrue(sounds.getAsJsonObject(key).has("subtitle"), key + " has no subtitle");
        }
    }

    @Test
    @DisplayName("the assets record where they came from")
    void provenanceIsRecorded()
    {
        for (String directory : List.of("ambience", "suppression"))
        {
            final Path sources = ASSETS.resolve("sounds").resolve(directory).resolve("SOURCES.md");

            assertTrue(Files.isRegularFile(sources),
                    "\"CC0\" and \"CC-BY\" are different promises and the repo should be able to prove "
                            + "which one it made (#209) - no record for " + directory);

            final String text = read(sources);

            assertFalse(text.isBlank());
        }

        // the ambience is mixed from recordings, so its record names the mixer and both libraries it
        // took from, under the licence that makes it CC0 - and each library with the commit it was taken at
        final String ambience = read(ASSETS.resolve("sounds").resolve("ambience").resolve("SOURCES.md"));

        assertTrue(ambience.contains("tools/ambience/mix.py"),
                "the ambience's provenance should name the script that mixed it, mix.py");
        assertTrue(ambience.contains("CC0"), "the ambience's recordings are CC0, and the record should say so");
        assertTrue(ambience.contains("VSCO-2"), "the ambience's record should name VSCO-2 Community Edition");
        assertTrue(ambience.contains("VCSL"), "the ambience's record should name the Versilian Community Sample Library");
        assertEquals(2, commitsIn(ambience), "each of the two libraries should be pinned to a commit");

        // the suppression set is still rendered by its own script, and says so
        final String suppression = read(ASSETS.resolve("sounds").resolve("suppression").resolve("SOURCES.md"));

        assertTrue(suppression.contains("tools/ambience/generate.py"),
                "the suppression set's provenance should name what produced it, generate.py");
    }

    @Test
    @DisplayName("every recording in the ambience and the soul's music decodes, and is mono at the synth's rate (#163)")
    void everyRecordingIsMonoAtTheSynthRate() throws IOException
    {
        final List<Path> files = recordings();

        assertFalse(files.isEmpty(), "found no recordings to check - the paths have moved");

        final List<String> problems = new ArrayList<>();

        for (Path file : files)
        {
            final String where = file.toString();

            try
            {
                final JorbisDecoder.Decoded decoded = JorbisDecoder.decode(file);

                if (decoded.channels() != 1)
                {
                    problems.add(where + " has " + decoded.channels() + " channels");
                }

                if (decoded.sampleRate() != SoulSynth.SAMPLE_RATE)
                {
                    problems.add(where + " is at " + decoded.sampleRate() + " Hz");
                }

                if (decoded.samples().length == 0)
                {
                    problems.add(where + " decodes to nothing");
                }
            }
            catch (IOException exception)
            {
                problems.add(where + " will not decode: " + exception.getMessage());
            }
        }

        // vanilla only spatialises mono, and a stereo one-shot would play in the head rather than at its place
        assertTrue(problems.isEmpty(), "recordings that are not mono at " + SoulSynth.SAMPLE_RATE + " Hz: " + problems);
    }

    @Test
    @DisplayName("the beds and the character layers are between a minute and seventy seconds long, so they loop unheard")
    void theBedsAreLongEnoughToLoop() throws IOException
    {
        final List<String> outside = new ArrayList<>();
        int checked = 0;

        for (Path file : recordings())
        {
            final String name = file.getFileName().toString();

            if (!name.startsWith("bed_") && !name.startsWith("character_"))
            {
                continue;
            }

            final JorbisDecoder.Decoded decoded = JorbisDecoder.decode(file);
            final double seconds = decoded.samples().length / (double) decoded.sampleRate();

            checked++;

            if (seconds < 60d || seconds > 70d)
            {
                outside.add(name + " is " + seconds + " s");
            }
        }

        // two rank beds, and a character layer for every voice but the base one
        assertEquals(2 + SoulVoice.values().length - 1, checked,
                "expected two rank beds and a character layer for every non-base voice");
        assertTrue(outside.isEmpty(), "beds and character layers outside 60-70 s: " + outside);
    }

    /** Every Ogg file the ambience and the soul's music ship, in path order. */
    private static List<Path> recordings() throws IOException
    {
        final List<Path> files = new ArrayList<>();

        for (Path root : List.of(ASSETS.resolve("sounds").resolve("ambience"), ASSETS.resolve("soul_music")))
        {
            try (Stream<Path> walk = Files.walk(root))
            {
                walk.filter(path -> path.getFileName().toString().endsWith(".ogg")).sorted().forEach(files::add);
            }
        }

        return files;
    }

    /** How many full 40-digit git commit hashes a record names. */
    private static int commitsIn(String text)
    {
        return (int) Pattern.compile("\\b[0-9a-f]{40}\\b").matcher(text).results().count();
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
