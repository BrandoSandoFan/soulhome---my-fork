/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds a directory under the project root by walking upward from the working directory - shared
 * by {@link ArchetypeDocs} and {@link TagDocs}, both of which read shipped data files at data
 * generation time rather than through a {@code ResourceManager}. See {@link ArchetypeDocs}'s
 * class javadoc for why: data generation runs before any server exists, and a Forge development
 * environment does not let the classloader list a mod resource directory.
 */
final class ProjectResources
{
    /** How far up from the working directory to look for the project root. */
    private static final int MAX_SEARCH_DEPTH = 6;

    private ProjectResources()
    {
    }

    /**
     * @throws IllegalStateException if {@code relative} cannot be found within
     *                                {@value #MAX_SEARCH_DEPTH} parent directories of the working
     *                                directory
     */
    static Path locate(Path relative)
    {
        Path cursor = Path.of("").toAbsolutePath();

        for (int depth = 0; depth <= MAX_SEARCH_DEPTH && cursor != null; depth++)
        {
            final Path candidate = cursor.resolve(relative);

            if (Files.isDirectory(candidate))
            {
                return candidate;
            }

            cursor = cursor.getParent();
        }

        throw new IllegalStateException(
                "Could not find " + relative + " from " + Path.of("").toAbsolutePath());
    }
}
