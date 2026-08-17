/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Matches a block by id, by tag, or by a list of either.
 *
 * <pre>{@code
 * { "tag": "minecraft:bookshelves" }
 * { "block": "minecraft:lectern" }
 * { "block": ["minecraft:chest", "minecraft:barrel"] }
 * { "block": "minecraft:cauldron", "tag": "minecraft:candles" }
 * }</pre>
 *
 * <p>A matcher succeeds if <i>any</i> of its entries match. Prefer tags in shipped archetypes:
 * {@code minecraft:bookshelves} rather than {@code minecraft:bookshelf} means chiselled and
 * modded variants count for free, which is most of the difference between a system that feels
 * creative and one that feels like a checklist.
 *
 * <p>Deserialisation lives in {@code ArchetypeCodecs}, so that this class - and the scoring that
 * depends on it - stays free of both Minecraft and DataFixerUpper and can be unit tested directly.
 */
public record BlockMatcher(List<String> blocks, List<String> tags) implements Predicate<BlockSignature>
{
    public BlockMatcher
    {
        blocks = normalise(blocks);
        tags = normalise(tags);
    }

    public static BlockMatcher ofBlocks(String... blocks)
    {
        return new BlockMatcher(List.of(blocks), List.of());
    }

    public static BlockMatcher ofTags(String... tags)
    {
        return new BlockMatcher(List.of(), List.of(tags));
    }

    @Override
    public boolean test(BlockSignature signature)
    {
        if (signature == null)
        {
            return false;
        }

        for (String block : this.blocks)
        {
            if (block.equals(signature.id()))
            {
                return true;
            }
        }

        for (String tag : this.tags)
        {
            if (signature.hasTag(tag))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isEmpty()
    {
        return this.blocks.isEmpty() && this.tags.isEmpty();
    }

    /**
     * Human-readable form, for {@code /soul analyse} and the Soul Lens. Tags keep the {@code #}
     * prefix players recognise from recipes.
     */
    public String describe()
    {
        List<String> parts = new ArrayList<>(this.blocks.size() + this.tags.size());
        parts.addAll(this.blocks);

        for (String tag : this.tags)
        {
            parts.add("#" + tag);
        }

        return String.join(" or ", parts);
    }

    /**
     * Structural problems with this matcher, for the loader to log. Empty means valid.
     */
    public List<String> validationErrors()
    {
        List<String> errors = new ArrayList<>();

        if (isEmpty())
        {
            errors.add("matcher has neither a 'block' nor a 'tag' entry, so it can never match");
        }

        for (String block : this.blocks)
        {
            if (!isValidId(block))
            {
                errors.add("'" + block + "' is not a valid block id");
            }
        }

        for (String tag : this.tags)
        {
            if (!isValidId(tag))
            {
                errors.add("'" + tag + "' is not a valid block tag id");
            }
        }

        return errors;
    }

    /**
     * Lowercases, trims, drops blanks, and tolerates the leading {@code #} players habitually put
     * on tags. Keeping ids canonical here means the hot path is a plain string comparison.
     */
    private static List<String> normalise(List<String> raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return List.of();
        }

        List<String> normalised = new ArrayList<>(raw.size());

        for (String entry : raw)
        {
            if (entry == null)
            {
                continue;
            }

            String trimmed = entry.trim().toLowerCase(Locale.ROOT);

            if (trimmed.startsWith("#"))
            {
                trimmed = trimmed.substring(1);
            }

            if (trimmed.isEmpty())
            {
                continue;
            }

            // vanilla convention: an unqualified id means the minecraft namespace
            if (trimmed.indexOf(':') < 0)
            {
                trimmed = "minecraft:" + trimmed;
            }

            if (!normalised.contains(trimmed))
            {
                normalised.add(trimmed);
            }
        }

        return List.copyOf(normalised);
    }

    /**
     * A loose namespaced-id check. Deliberately not a registry lookup: archetypes may legitimately
     * name blocks from mods that are not installed, and those should simply never match rather
     * than invalidate the whole file.
     */
    private static boolean isValidId(String id)
    {
        int separator = id.indexOf(':');

        // normalise() has already supplied a namespace, so a bare path cannot reach here
        return separator > 0
                && separator < id.length() - 1
                && id.indexOf(':', separator + 1) < 0;
    }
}
