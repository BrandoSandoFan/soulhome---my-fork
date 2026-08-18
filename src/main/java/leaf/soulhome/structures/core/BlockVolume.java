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
}
