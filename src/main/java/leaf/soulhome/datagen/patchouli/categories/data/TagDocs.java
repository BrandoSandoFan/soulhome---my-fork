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

    /** One {@code soulhome:} block tag: its id and the entries in its {@code values} array. */
    public record Tag(String id, List<String> values)
    {
        /** {@code soulhome:lighting} to {@code lighting}. */
        public String path()
        {
            final int separator = id.indexOf(':');
            return separator < 0 ? id : id.substring(separator + 1);
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

            List<String> valueList = new ArrayList<>(values.size());

            for (JsonElement value : values)
            {
                valueList.add(value.getAsString());
            }

            return new Tag(id, List.copyOf(valueList));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not open " + file + " to document its tag", e);
        }
    }
}
