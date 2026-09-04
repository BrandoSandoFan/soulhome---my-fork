/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.utils.StringHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * What a matcher matched, as a player would say it: {@code minecraft:lightning_rod} becomes
 * "Lightning Rod", and {@code #soulhome:conductive} becomes "any conductive".
 *
 * <p>{@code /soulhome analyse} and the Soul Lens are where a player goes when a room did not
 * count, and both of them used to answer in registry ids - "needs 1 of minecraft:lightning_rod,
 * found 0". The id is the mod's business, not the player's; the block has a name and the game
 * already knows it.
 *
 * <p><b>This reads back a string {@link leaf.soulhome.structures.core.BlockMatcher#describe()}
 * built.</b> The alternative is carrying the matcher's ids as a list through
 * {@code ArchetypeScore}, its network message and the lens screen, so that a display concern can
 * be answered at the far end - which widens three records and a packet to say something the
 * description already says. {@code describe()} joins its parts with " or " and marks a tag with a
 * leading "#", and that shape is what this splits on; the two belong together and should move
 * together.
 */
public final class BlockNames
{
    private static final String SEPARATOR = " or ";

    private BlockNames()
    {
    }

    /**
     * One matcher description as a component, so a client translates the block names itself rather
     * than being sent whatever language the server happens to run in.
     */
    public static MutableComponent of(String description)
    {
        MutableComponent readable = Component.empty();
        boolean first = true;

        for (String part : description.split(SEPARATOR))
        {
            if (!first)
            {
                readable.append(SEPARATOR);
            }

            readable.append(name(part));
            first = false;
        }

        return readable;
    }

    /**
     * The same thing flattened, for the places that carry text rather than components - the lens
     * report is built on the server and travels as strings.
     */
    public static String text(String description)
    {
        return of(description).getString();
    }

    private static Component name(String part)
    {
        if (part.startsWith("#"))
        {
            //a tag is a category rather than a thing, and the book says "any lighting" for the
            //same tag - one wording between them, so a page and the game agree
            return Component.literal("any " + pathOf(part.substring(1)).replace('_', ' '));
        }

        try
        {
            final ResourceLocation id = ResourceLocation.tryParse(part);

            if (id != null)
            {
                final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);

                if (block.isPresent())
                {
                    return block.get().getName();
                }
            }
        }
        catch (Throwable registryUnavailable)
        {
            //a report is worth having with a plainer name in it; the tests run this without a
            //bootstrapped registry, and an unbootstrapped one fails in a static initialiser
        }

        //a room written for a mod this install does not have: there is no name to look up, and
        //the id is the honest answer rather than a guess dressed up as one
        return Component.literal(StringHelper.fixCapitalisation(pathOf(part)));
    }

    private static String pathOf(String id)
    {
        final int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }
}
