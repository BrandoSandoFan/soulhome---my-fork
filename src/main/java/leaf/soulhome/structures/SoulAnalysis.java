/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.structures.core.ClassificationResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Everything the last scan of one soulhome worked out, kept whole.
 *
 * <p>{@link SoulHomeBuffData} persists only the rooms that were awarded something, because that is
 * all recomputing a player's buffs needs and a full breakdown per room is a lot of NBT to carry
 * for a question nobody asks on login. But the feedback UX asks exactly the other question - what
 * did the classifier see, and why was this room *not* a library - so the full result is kept here,
 * in memory, for as long as the soulhome is loaded.
 *
 * <p>Deliberately not saved. A stale explanation is worse than none: after a restart the first
 * scan repopulates this within a second of anyone asking.
 */
public record SoulAnalysis(
        ResourceKey<Level> dimension,
        List<ClassificationResult> results,
        long scannedAtMillis)
{
    public SoulAnalysis
    {
        results = List.copyOf(results);
    }

    public static SoulAnalysis empty(ResourceKey<Level> dimension, long scannedAtMillis)
    {
        return new SoulAnalysis(dimension, List.of(), scannedAtMillis);
    }

    public boolean isEmpty()
    {
        return this.results.isEmpty();
    }

    /** How many regions were assigned an archetype, as opposed to merely being found. */
    public int classifiedCount()
    {
        int classified = 0;

        for (ClassificationResult result : this.results)
        {
            if (result.awarded().isPresent())
            {
                classified++;
            }
        }

        return classified;
    }

    /**
     * The region containing this position, if any. Regions can nest - an enclosed room inside a
     * larger open cluster - so the smallest match wins, which is the one a player standing there
     * would say they are in.
     */
    public Optional<ClassificationResult> regionAt(int x, int y, int z)
    {
        ClassificationResult best = null;

        for (ClassificationResult result : this.results)
        {
            if (!result.region().bounds().contains(x, y, z))
            {
                continue;
            }

            if (best == null || result.region().bounds().volume() < best.region().bounds().volume())
            {
                best = result;
            }
        }

        return Optional.ofNullable(best);
    }
}
