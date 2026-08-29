/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.ArchetypeCodecs;
import leaf.soulhome.structures.ArchetypeManager;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.utils.LogHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the shipped archetype definitions at data generation time, so the book can be written from
 * them.
 *
 * <p>The alternative is a hand-written page per archetype, which is the same information typed
 * twice: change a weight or a tier threshold and the book quietly starts lying. Since the whole
 * point of this feature is that a player can trust what they are told about how their room was
 * judged, documentation that can drift from the data is worse than none.
 *
 * <p>Reads the source tree rather than the classpath. Data generation runs before any server
 * exists, so a {@code ResourceManager} is not an option, and asking the classloader to list a
 * resource <i>directory</i> does not work in a Forge development environment - mod resources are
 * served through a union filesystem, and the directory URL comes back either null or under a
 * scheme {@code File} cannot open. Reading the files where they actually live is dull, but data
 * generation is a development-time task that already knows it is inside the project. The tests do
 * the same thing for the same reason.
 *
 * <p>Failure throws rather than returning nothing. An empty room category is exactly the sort of
 * quiet, plausible-looking breakage that ships; a failed data generation run is not.
 */
public final class ArchetypeDocs
{
    /** Where the definitions live, relative to the project root. */
    private static final Path RELATIVE_DIRECTORY =
            Path.of("src", "main", "resources", "data", SoulHome.MODID, ArchetypeManager.DIRECTORY);

    private ArchetypeDocs()
    {
    }

    /** Every archetype this mod ships, in a stable order. */
    public static List<ArchetypeDefinition> shipped()
    {
        final Path directory = ProjectResources.locate(RELATIVE_DIRECTORY);

        List<ArchetypeDefinition> archetypes = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory))
        {
            for (Path file : files.sorted().toList())
            {
                if (file.getFileName().toString().endsWith(".json"))
                {
                    read(file).ifPresent(archetypes::add);
                }
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not read " + directory + " to document the archetypes", e);
        }

        if (archetypes.isEmpty())
        {
            throw new IllegalStateException(
                    "No archetypes could be read from " + directory
                            + ", so the book would ship with an empty room category");
        }

        return archetypes;
    }

    private static Optional<ArchetypeDefinition> read(Path file)
    {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            final JsonElement json = JsonParser.parseReader(reader);
            final String id = SoulHome.MODID + ":" + fileName(file);

            return ArchetypeCodecs.ARCHETYPE
                    .parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error ->
                            LogHelper.error("Could not read archetype " + id + " for the book: " + error))
                    .map(definition -> definition.withId(id));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not open " + file + " to document its archetype", e);
        }
    }

    /** The file's name without its extension - the same thing the datapack loader uses as an id. */
    public static String fileName(Path file)
    {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');

        return dot < 0 ? name : name.substring(0, dot);
    }

    /** {@code soulhome:library} to {@code library}. */
    public static String pathOf(ArchetypeDefinition archetype)
    {
        final int separator = archetype.id().indexOf(':');
        return separator < 0 ? archetype.id() : archetype.id().substring(separator + 1);
    }
}
