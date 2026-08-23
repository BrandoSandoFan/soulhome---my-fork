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
}
