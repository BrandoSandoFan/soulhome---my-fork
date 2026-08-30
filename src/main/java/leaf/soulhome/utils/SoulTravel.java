/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.utils;

import leaf.soulhome.SoulHome;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Who is allowed to move something into or out of a soul dimension.
 *
 * <p>A soulhome is meant to be reachable with a SoulKey and by nothing else. Every teleport mod
 * on the market disagrees: Waystones will happily bind a warp stone to a plate inside someone's
 * soul and let the whole server walk in, and a return scroll used in there is a free trip home
 * that skips the key, the exit position the mod saved, and the rescan on the way out that decides
 * what the player's rooms are currently worth. So the rule is inverted - travel across a soul
 * dimension's boundary is refused unless this mod is the one doing it.
 *
 * <p>The marker is a thread-local rather than a flag on the entity because it is scoped to a call,
 * not to a player: teleports happen on the server thread, inside
 * {@code TeleportHelper#teleportEntity}, and a flag left set on an entity by a teleport that threw
 * halfway would open the door permanently for that player.
 */
public final class SoulTravel
{
    /**
     * Depth rather than a boolean: {@code DimensionHelper#FlipDimension} teleports a whole group,
     * and a nested call restoring {@code false} on the way out would strand the rest of the group
     * outside the exemption.
     */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private SoulTravel()
    {
    }

    /** Runs {@code teleport} as a soulhome-initiated move, which the travel guard lets through. */
    public static void asSoulTravel(Runnable teleport)
    {
        DEPTH.set(DEPTH.get() + 1);

        try
        {
            teleport.run();
        }
        finally
        {
            final int remaining = DEPTH.get() - 1;

            if (remaining <= 0)
            {
                //remove rather than set(0), so a thread that never teleports again does not hold
                //an entry in the thread-local map for the rest of the server's life
                DEPTH.remove();
            }
            else
            {
                DEPTH.set(remaining);
            }
        }
    }

    /** Whether the teleport currently being processed on this thread is one of ours. */
    public static boolean isSoulTravel()
    {
        return DEPTH.get() > 0;
    }

    /**
     * Whether this dimension key names a soul dimension.
     *
     * <p>Read off the key rather than by resolving the level and comparing dimension types, which
     * is what {@code DimensionHelper#isDimensionOfType} does: the destination of a travel event may
     * be a soulhome that has never been visited and so does not exist yet, and "the level is not
     * loaded" must not read as "not a soulhome". Soul dimensions are named
     * {@code soulhome:<owner uuid>} (see {@code DimensionRegistry#createSoulDimension}), so the
     * name is enough.
     */
    public static boolean isSoulDimensionKey(ResourceKey<Level> dimension)
    {
        if (dimension == null)
        {
            return false;
        }

        final ResourceLocation location = dimension.location();

        if (!SoulHome.MODID.equals(location.getNamespace()))
        {
            return false;
        }

        try
        {
            UUID.fromString(location.getPath());
            return true;
        }
        catch (IllegalArgumentException e)
        {
            //some other dimension in this mod's namespace, if one is ever added, is not a soulhome
            return false;
        }
    }
}
