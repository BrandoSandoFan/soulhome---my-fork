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
        return matcherFilter(collectSignalMatchers(archetypes));
    }

    /**
     * A predicate matching any block named by an element of any of these archetypes' structural
     * forms - what {@link RegionScanner}'s {@code geometryFilter} should index, so a room full of
     * stone indexes nothing and a room with rails and chairs indexes only the rails and the chairs.
     * See "Only index what a form will ask about" in #26.
     *
     * <p>Derived the same way {@link #filterFor} is, and for the same reason: a datapack that adds
     * a form gets its elements indexed without a Java change.
     */
    public static Predicate<BlockSignature> geometryFilterFor(Collection<ArchetypeDefinition> archetypes)
    {
        List<BlockMatcher> matchers = new ArrayList<>();

        for (ArchetypeDefinition archetype : archetypes)
        {
            for (Form form : archetype.structures())
            {
                matchers.addAll(form.elements().values());
            }
        }

        return matcherFilter(matchers);
    }

    private static List<BlockMatcher> collectSignalMatchers(Collection<ArchetypeDefinition> archetypes)
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

        return matchers;
    }

    private static Predicate<BlockSignature> matcherFilter(List<BlockMatcher> matchers)
    {
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
