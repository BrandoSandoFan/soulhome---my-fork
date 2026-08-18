/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * How a block behaves during room detection.
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
     * A block that stops the fill: walls, floors, furniture, and - by deliberate choice - doors,
     * trapdoors and fence gates regardless of whether they are currently open. See
     * {@link RegionScanner} for why.
     */
    BLOCKING
}
