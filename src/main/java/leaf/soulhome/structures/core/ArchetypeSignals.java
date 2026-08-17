/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bridges the loaded archetypes to {@link RegionScanner}, which needs to know which blocks are
 * worth clustering an open-air region around.
 *
 * <p>"Worth clustering" means "some archetype cares about it". Deriving that from the definitions
 * rather than hardcoding a list is what lets a datapack add an archetype and have its blocks
 * become detectable without touching Java.
 */
public final class ArchetypeSignals
{
    private ArchetypeSignals()
    {
    }

    /**
     * A predicate matching any block named as a signal or a requirement by any of these
     * archetypes.
     *
     * <p>Detractors are excluded on purpose: an anvil is evidence a room is <i>not</i> a library,
     * which is no reason to go looking for open-air anvil fields.
     */
    public static Predicate<BlockSignature> filterFor(Collection<ArchetypeDefinition> archetypes)
    {
        List<BlockMatcher> matchers = new ArrayList<>();

        for (ArchetypeDefinition archetype : archetypes)
        {
            for (ArchetypeDefinition.Signal signal : archetype.signals())
            {
                matchers.add(signal.match());
            }

            for (ArchetypeDefinition.Requirement requirement : archetype.requirements())
            {
                matchers.add(requirement.match());
            }
        }

        if (matchers.isEmpty())
        {
            return signature -> false;
        }

        final List<BlockMatcher> frozen = List.copyOf(matchers);

        return signature ->
        {
            for (BlockMatcher matcher : frozen)
            {
                if (matcher.test(signature))
                {
                    return true;
                }
            }

            return false;
        };
    }
}
