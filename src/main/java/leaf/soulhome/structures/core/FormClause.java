/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One node in a {@link Form}'s clause tree: a {@code shape} leaf, a {@code relation} leaf, or an
 * {@code all}/{@code any} node composing other clauses. See the structural form grammar epic (#27)
 * for the full grammar this is the interface for.
 *
 * <p>Implementations are value records, so a clause shared across archetypes - {@code soulhome:seating}
 * asked for by more than one form - is cheap to hold and cheap to evaluate repeatedly.
 */
public interface FormClause
{
    /** {@code "shape"}/{@code "relation"} clause id, or {@code "all"}/{@code "any"} for a node. */
    String typeId();

    /**
     * Grades this clause against one region's geometry.
     *
     * @param elements every element the owning {@link Form} declared, by name - a leaf reads the
     *                  ones it names via its own {@code of}/{@code to}-style parameters
     */
    FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements);

    /** Human-readable form, for the book and the structural feedback report (#33). */
    String describe();

    /**
     * Problems that make this clause meaningless even though it parsed: a numeric range that can
     * never be satisfied, and so on. Does <b>not</b> cover an element name the clause refers to not
     * being declared - that check is generic across every clause and lives in
     * {@link Form#validationErrors()}, driven by {@link ClauseParamSpec.Type#ELEMENT}.
     *
     * @param elementNames every element name the owning form declared, for a clause whose own
     *                      validation needs that context
     */
    List<String> validationErrors(Set<String> elementNames);

    /**
     * Whether evaluating this clause needs {@link RegionGeometry#isBlocked} data to be meaningful -
     * in practice, only ever {@code true} for an {@code across} relation (#29) with
     * {@code require_clear} set. {@code false} by default so nothing outside that one clause has to
     * know this exists; {@link AllClause} and {@link AnyClause} override it to walk their children,
     * so {@link Form#needsClearance()} - and so {@code ArchetypeSignals#needsClearance} - can ask
     * the question of a whole clause tree without new tree-walking machinery.
     */
    default boolean needsClearance()
    {
        return false;
    }

    /**
     * Names of the elements whose <i>footing</i> - the cell directly beneath each of them - this
     * clause has to see even when that cell is outside the room (#307). A note block on the floor
     * plays whatever it stands on, and what it stands on is the floor's underside, which is never
     * part of the room's shell and so never reaches {@link RegionGeometry} on its own. Empty by
     * default, so nothing that does not ask pays for it; {@link AllClause} and {@link AnyClause}
     * union their children, the way {@link #needsClearance()} is walked, so
     * {@code ArchetypeSignals#footingFilterFor} can ask a whole tree.
     */
    default Set<String> footingElements()
    {
        return Set.of();
    }

    /**
     * The other side of {@link #footingElements()}: names of the elements this clause looks for
     * <i>under</i> them. Empty by default; walked by {@link AllClause} and {@link AnyClause}.
     */
    default Set<String> footingTargets()
    {
        return Set.of();
    }

    /**
     * Whether every leaf under this node reads footing (#307). A form made only of such clauses
     * needs its {@link #footingTargets()} indexed only under the blocks that stand on them, never
     * across the whole room - see {@link FootingFilter}. {@code false} by default, so a form mixing
     * a footing clause with any other kind indexes all its elements the ordinary way, which is
     * always correct, merely less frugal.
     */
    default boolean readsOnlyFootings()
    {
        return false;
    }
}
