/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One dichotomy a soul can sit on - the Ambience epic (#163/#165).
 *
 * <p>An axis is a pair of opposed {@link SoulTrait}s and, just as importantly, a third expression
 * for a soul that has built plenty of <b>both</b>. That third thing is the whole reason axes exist
 * rather than a flat list of traits: a soul of forges and freezers is not a lukewarm soul, and
 * averaging it into the middle of the scale would be the worst answer available - it would read as
 * "you have built nothing in particular" to a player who has built a great deal. It gets steam
 * instead, and a soul of workshops and sanctums gets the air between them rather than the average
 * of the two.
 *
 * <p>Which is also why the mod never says any of this out loud. Naming an axis to a player would
 * sort them into a box, and #165's whole constraint is that a soulhome is never <i>a</i> kind of
 * soul; these names exist so the code can be read, not so anything can be displayed. Nothing in
 * {@code feedback}, the lens or the book names an axis, a trait or a blend.
 */
public enum SoulAxis
{
    /** Forge heat against still cold. Both at once is steam. */
    THERMAL,

    /** What a room is powered by: worked matter against the arcane. Both at once is the air between. */
    ESSENCE,

    /** Growing things against emptied ones. Both at once is overgrowth - decay that is also alive. */
    VITALITY;

    /**
     * The traits of this axis, positive pole first.
     *
     * <p>Derived by walking {@link SoulTrait} rather than held in a field, because a field would
     * make the two enums initialise each other and the cycle resolves to nulls rather than to an
     * error anyone would see.
     */
    public List<SoulTrait> traits()
    {
        List<SoulTrait> found = new ArrayList<>(2);

        for (SoulTrait trait : SoulTrait.values())
        {
            if (trait.axis() == this)
            {
                found.add(trait);
            }
        }

        found.sort((a, b) -> Boolean.compare(b.isPositive(), a.isPositive()));

        return List.copyOf(found);
    }

    /** The pole a positive lean points at. */
    public SoulTrait positive()
    {
        return traits().get(0);
    }

    /** The pole a negative lean points at. */
    public SoulTrait negative()
    {
        return traits().get(1);
    }
}
