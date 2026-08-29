/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * How a block behaves during region detection.
 *
 * <p>Two different questions are asked of a block, and they do not have the same answer:
 *
 * <ul>
 *   <li><b>Does it seal a space?</b> - {@link #stopsFill()}. A fence around a courtyard has no roof
 *       and seals nothing, but a fence in a wall is as much a wall as the blocks either side of it.
 *       Anything the air cannot flow through counts.</li>
 *   <li><b>Is it a barrier between two places?</b> - {@link #isFullBlock()}. Only a block filling
 *       its whole cell. A fence, a wall, a pane, a slab, a stair or a chest is something a player
 *       puts <i>inside</i> one build, and treating each of them as the edge of the world would cut
 *       a fenced track off from its own trackside.</li>
 * </ul>
 */
public enum Passability
{
    /** Air or void. The fill spreads through it and there is nothing to record. */
    EMPTY,

    /**
     * A block the fill spreads through but which is still part of the build - torches, crops,
     * carpets, flowers, water. Recorded as room contents.
     */
    PASSABLE,

    /**
     * A block that stops the fill without filling its cell: fences, walls, panes, slabs, stairs,
     * chests, and - by deliberate choice - doors, trapdoors and fence gates regardless of whether
     * they are currently open. See {@link RegionScanner} for why doors are here rather than with
     * the passable blocks.
     *
     * <p>Seals a room exactly like {@link #BLOCKING} does. What it does not do is separate two
     * open-air builds from each other: you can see over a fence and step across a slab.
     */
    PARTIAL,

    /**
     * A block filling its whole cell: stone, planks, glass, a bookshelf, a hay bale. Seals a room,
     * and is the one thing that says "what is on the other side of me is somewhere else".
     */
    BLOCKING;

    /** Whether the outside-in flood fill stops here, which is what makes a space enclosed. */
    public boolean stopsFill()
    {
        return this == PARTIAL || this == BLOCKING;
    }

    /** Whether this fills its cell, and so stands between one open-air build and the next. */
    public boolean isFullBlock()
    {
        return this == BLOCKING;
    }
}
