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
 * <p>"Worth clustering" means "some archetype that can be scored on open ground cares about it".
 * Deriving that from the definitions rather than hardcoding a list is what lets a datapack add an
 * archetype and have its blocks become detectable without touching Java.
 */
public final class ArchetypeSignals
{
    private ArchetypeSignals()
    {
    }

    /**
     * A predicate matching any block named as a signal or a requirement by any of these
     * archetypes - everything the classifier could ever count.
     *
     * <p>Detractors are excluded on purpose: an anvil is evidence a room is <i>not</i> a library,
     * which is no reason to go looking for open-air anvil fields.
     *
     * <p>This is <b>not</b> what the scanner should cluster open-air regions around - see
     * {@link #openClusterFilterFor} for why the two are different questions with different
     * answers. It was, for a while, and that is the fault #134 is about.
     */
    public static Predicate<BlockSignature> filterFor(Collection<ArchetypeDefinition> archetypes)
    {
        return matcherFilter(collectSignalMatchers(archetypes));
    }

    /**
     * A predicate matching any block named as a signal or a requirement by an archetype that
     * accepts {@link RegionType#OPEN} regions - what {@link RegionScanner}'s {@code signalFilter}
     * should seed and grow open-air clusters from.
     *
     * <p>Two jobs used to share one filter: deciding which blocks are worth <i>counting</i>, which
     * is every archetype's palette, and deciding which blocks <i>seed and extend a cluster</i>,
     * which should not be. Most shipped archetypes are {@code enclosed}-only, and their palettes
     * still seeded open-air clusters they could never be scored by. Snow is a cold storage signal
     * and leaves are a greenhouse signal, and both are what the starter islands are made of - so
     * on a snowy island the ground itself was one island-spanning cluster, every open-air build a
     * player laid on it joined that one region, and building a wall between two builds did nothing
     * because there was no gap for a wall to close (#134).
     *
     * <p>Nothing stops being counted. A cluster still takes in whatever it closes around and
     * whatever sits a cell beyond its own blocks, so a farm planted on snow still has that snow in
     * its contents; what it no longer does is chain across the snow to the next build. A datapack
     * that adds an {@code open} archetype naming snow gets snow back as a cluster seed with no Java
     * change - derived from the definitions, exactly as {@link #filterFor} is.
     */
    public static Predicate<BlockSignature> openClusterFilterFor(Collection<ArchetypeDefinition> archetypes)
    {
        List<ArchetypeDefinition> openCapable = new ArrayList<>();

        for (ArchetypeDefinition archetype : archetypes)
        {
            if (archetype.accepts(RegionType.OPEN))
            {
                openCapable.add(archetype);
            }
        }

        return matcherFilter(collectSignalMatchers(openCapable));
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

    /**
     * Whether any of these archetypes' structural forms actually needs clearance data - see
     * {@link RegionGeometry#isBlocked} and {@link RegionScanner}'s {@code indexClearance} flag.
     * Today that means an {@code across} relation with {@code require_clear} set; derived the same
     * way {@link #geometryFilterFor} is, so a datapack adding one gets clearance tracked for its
     * scan without a Java change.
     */
    public static boolean needsClearance(Collection<ArchetypeDefinition> archetypes)
    {
        for (ArchetypeDefinition archetype : archetypes)
        {
            for (Form form : archetype.structures())
            {
                if (form.needsClearance())
                {
                    return true;
                }
            }
        }

        return false;
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
