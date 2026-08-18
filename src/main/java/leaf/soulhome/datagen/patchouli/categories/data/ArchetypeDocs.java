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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads the shipped archetype definitions at data generation time, so the book can be written from
 * them.
 *
 * <p>The alternative is a hand-written page per archetype, which is the same information typed
 * twice: change a weight or a tier threshold and the book quietly starts lying. Since the whole
 * point of this feature is that a player can trust what they are told about how their room was
 * judged, documentation that can drift from the data is worse than none.
 *
 * <p>Reads the files straight off the classpath rather than through a
 * {@link net.minecraft.server.packs.resources.ResourceManager}: data generation runs before any
 * server exists, and these are the mod's own resources, sitting in a directory on disk.
 */
public final class ArchetypeDocs
{
    private static final String DIRECTORY =
            "/data/" + SoulHome.MODID + "/" + ArchetypeManager.DIRECTORY;

    private ArchetypeDocs()
    {
    }

    /**
     * Every archetype this mod ships, in a stable order.
     *
     * <p>Returns an empty list rather than throwing if the directory cannot be read: a book with
     * no archetype entries is a visible, fixable problem, and a data generation run that dies
     * halfway leaves the whole generated tree in an unclear state.
     */
    public static List<ArchetypeDefinition> shipped()
    {
        final URL directory = ArchetypeDocs.class.getResource(DIRECTORY);

        if (directory == null || !"file".equals(directory.getProtocol()))
        {
            LogHelper.error("Could not list " + DIRECTORY
                    + " to generate archetype documentation; the book will be missing its room entries.");
            return List.of();
        }

        final File[] files;

        try
        {
            files = new File(directory.toURI()).listFiles((dir, name) -> name.endsWith(".json"));
        }
        catch (URISyntaxException e)
        {
            LogHelper.error("Could not read " + DIRECTORY + " for archetype documentation: " + e);
            return List.of();
        }

        if (files == null)
        {
            return List.of();
        }

        Arrays.sort(files, Comparator.comparing(File::getName));

        List<ArchetypeDefinition> archetypes = new ArrayList<>();

        for (File file : files)
        {
            read(file).ifPresent(archetypes::add);
        }

        return archetypes;
    }

    private static Optional<ArchetypeDefinition> read(File file)
    {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
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
            LogHelper.error("Could not open " + file + " for archetype documentation: " + e);
            return Optional.empty();
        }
    }

    /** The file's name without its extension - the same thing the datapack loader uses as an id. */
    public static String fileName(File file)
    {
        final String name = file.getName();
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
