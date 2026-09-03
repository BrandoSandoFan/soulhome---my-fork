/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.items;

import leaf.soulhome.properties.PropTypes;

/**
 * Sublime Essence, ranks I through V (#82). Spent on the ascension ritual (#83) and nothing else -
 * no NBT, no other use, stacks to 64. Five items, not five hundred: this is the whole reason the
 * "treated blocks per rank" proposal in the Ascent epic (#78) was rejected in favour of one small
 * item family.
 */
public class SublimeEssenceItem extends BaseItem
{
    /** 1 (Essence I) through 5 (Essence V) - see {@link leaf.soulhome.structures.core.SoulBounds#MAX_RANK}. */
    private final int rank;

    public SublimeEssenceItem(int rank)
    {
        super(PropTypes.Items.SIXTY_FOUR.get());

        if (rank < 1 || rank > 5)
        {
            throw new IllegalArgumentException("Sublime Essence rank must be 1-5, got " + rank);
        }

        this.rank = rank;
    }

    public int rank()
    {
        return this.rank;
    }
}
