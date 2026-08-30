/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leaf.soulhome.SoulHome;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads the shipped {@code soulhome:} block tags at data generation time, so the book's glossary
 * (issue #49) can be written from them the same way {@link ArchetypeDocs} writes the room pages -
 * the tag's contents come from the file a datapack would actually change, so the glossary cannot
 * say something the classifier does not do.
 *
 * <p>Only the {@code soulhome:} tags are read. A room page also names vanilla and Forge tags
 * ({@code minecraft:crops}, {@code minecraft:rails}...), but this mod does not ship - and must
 * not shadow - their contents, so there is nothing local to read them from. Those names stay
 * unlinked in {@code PatchouliMultiblocks#readable}; the tags this mod actually defines are the
 * ones a player has no other way to learn.
 */
public final class TagDocs
{
    /** Where the tag files live, relative to the project root. */
    private static final Path RELATIVE_DIRECTORY =
            Path.of("src", "main", "resources", "data", SoulHome.MODID, "tags", "blocks");

    private TagDocs()
    {
    }

    /**
     * One {@code soulhome:} block tag: its id, the entries every game has, and the entries that
     * only exist when some other mod is installed.
     *
     * <p>The split matters to a reader. A tag entry written as
     * {@code {"id": "create:cogwheel", "required": false}} is how a block from another mod is
     * added without dropping the whole tag on a server that does not have it - and a glossary
     * page that listed a cogwheel among the plain entries would be promising something the game
     * in front of the player cannot give them.
     */
    public record Tag(String id, List<String> values, List<String> optionalValues)
    {
        public Tag
        {
            values = List.copyOf(values);
            optionalValues = List.copyOf(optionalValues);
        }

        /** {@code soulhome:lighting} to {@code lighting}. */
        public String path()
        {
            final int separator = id.indexOf(':');
            return separator < 0 ? id : id.substring(separator + 1);
        }

        /** Everything in the tag, whether or not it needs another mod. */
        public List<String> allValues()
        {
            List<String> all = new ArrayList<>(values.size() + optionalValues.size());
            all.addAll(values);
            all.addAll(optionalValues);

            return List.copyOf(all);
        }
    }

    /** Every {@code soulhome:} block tag this mod ships, in a stable order. */
    public static List<Tag> shipped()
    {
        final Path directory = ProjectResources.locate(RELATIVE_DIRECTORY);

        List<Tag> tags = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory))
        {
            for (Path file : files.sorted().toList())
            {
                if (file.getFileName().toString().endsWith(".json"))
                {
                    tags.add(read(file));
                }
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not read " + directory + " to document the tags", e);
        }

        if (tags.isEmpty())
        {
            throw new IllegalStateException(
                    "No tags could be read from " + directory + ", so the book's glossary would be empty");
        }

        return tags;
    }

    private static Tag read(Path file)
    {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            final String id = SoulHome.MODID + ":" + ArchetypeDocs.fileName(file);

            final JsonArray values = json.has("values") ? json.getAsJsonArray("values") : new JsonArray();

            List<String> required = new ArrayList<>(values.size());
            List<String> optional = new ArrayList<>();

            for (JsonElement value : values)
            {
                //vanilla's two forms: a bare id, or {"id": ..., "required": false} for an entry
                //that may not exist in this install
                if (value.isJsonObject())
                {
                    final JsonObject entry = value.getAsJsonObject();
                    final String entryId = entry.get("id").getAsString();

                    if (entry.has("required") && !entry.get("required").getAsBoolean())
                    {
                        optional.add(entryId);
                    }
                    else
                    {
                        required.add(entryId);
                    }
                }
                else
                {
                    required.add(value.getAsString());
                }
            }

            return new Tag(id, required, optional);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not open " + file + " to document its tag", e);
        }
    }
}
