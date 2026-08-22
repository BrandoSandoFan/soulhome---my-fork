/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * One child of an {@link AllClause} or {@link AnyClause}, with the weight its parent's
 * {@code "all"} mean should give it. Defaults to {@code 1.0} when a datapack does not set
 * {@code "weight"} on the clause object. Meaningless on a form's root clause, which is not itself
 * anyone's child - see {@link Form#weight()} for the form-level equivalent.
 */
public record WeightedClause(double weight, FormClause clause)
{
}
