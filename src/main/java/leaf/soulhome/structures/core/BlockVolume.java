/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * A finite, random-access window onto blocks, which is all region detection needs from a world.
 *
 * <p>The game-side implementation snapshots a {@code ServerLevel} so that scanning can be moved
 * off the main thread; tests implement it over a string grid.
 *
 * <p>Positions outside {@link #bounds()} are treated as {@link Passability#EMPTY} by the scanner
 * and are never queried, so implementations need not range-check.
 */
public interface BlockVolume
{
    /** The inclusive box this volume covers. */
    RegionBounds bounds();

    Passability passabilityAt(int x, int y, int z);

    /**
     * The block at this position, or {@code null} where {@link #passabilityAt} is
     * {@link Passability#EMPTY}.
     */
    BlockSignature signatureAt(int x, int y, int z);

    /**
     * Which way the block at this position faces, or {@code null} when it has no orientation
     * property at all - not to be confused with "not facing anything", which is a question for a
     * {@code facing} relation to answer, not this method. Defaulted to {@code null} everywhere so
     * a {@link BlockVolume} that has no notion of orientation - {@code GridVolume}, in the test
     * suite - never has to implement it.
     */
    default Facing facingAt(int x, int y, int z)
    {
        return null;
    }
}
