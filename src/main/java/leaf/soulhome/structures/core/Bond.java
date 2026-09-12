/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One archetype's declared relationship with another - the third kind of evidence beside
 * {@code signals} and {@code structures}, from the Soul Architecture epic (#140).
 *
 * <pre>{@code
 * { "with": "soulhome:enchanting_room", "relation": "connects", "weight": 4.0, "role": "study",
 *   "max_distance": 12 }
 * }</pre>
 *
 * <p>Declared once, on either archetype, and symmetric in effect: both rooms are credited. A
 * negative weight is a <i>discord</i> - evidence against, for the cases where being next to
 * something is the wrong thing (a powder magazine against a hearth).
 *
 * <p>Deliberately a relation between exactly two archetypes with one relation and no
 * composition: a datapack composes, it does not compute, and here more tightly than for forms.
 * Never a gate - there is nowhere on {@link ArchetypeDefinition.Requirement} to put one.
 *
 * @param with     the other archetype's id. Naming one that is not loaded is fine and simply never
 *                 matches, exactly as {@link BlockMatcher} treats a block from an absent mod - which
 *                 is what lets a datapack bond against a room it hopes is installed
 * @param relation how the two should relate - an id in {@link BondRelationRegistry}, closed the
 *                 way the form clause vocabulary is closed
 * @param weight   positive for a bond, negative for a discord
 * @param role     multiplies into the diversity term the way a signal role does, so a room bonded
 *                 to several kinds of neighbour beats one bonded hard to a single kind
 * @param params   the relation's own parameters, read according to
 *                 {@link BondRelation#params()} with defaults filled in
 */
public record Bond(String with, String relation, double weight, String role, ClauseParams params)
{
    public static final String DEFAULT_ROLE = "bond";

    public Bond
    {
        role = role == null || role.isBlank() ? DEFAULT_ROLE : role;
    }

    public boolean isDiscord()
    {
        return this.weight < 0d;
    }

    /** Problems worth rejecting the archetype over - see {@link ArchetypeDefinition#validationErrors}. */
    public List<String> validationErrors()
    {
        List<String> errors = new ArrayList<>();

        if (this.with == null || this.with.isBlank())
        {
            errors.add("'with' is missing or blank");
        }

        if (this.relation == null || this.relation.isBlank())
        {
            errors.add("'relation' is missing or blank");
        }

        if (this.weight == 0d || Double.isNaN(this.weight))
        {
            errors.add("'weight' must be non-zero - positive for a bond, negative for a discord");
        }

        return errors;
    }
}
