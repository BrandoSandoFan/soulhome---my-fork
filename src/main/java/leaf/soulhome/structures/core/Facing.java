/*
 * File created ~ 11 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The direction a block's own orientation points, when it has one - a stair's tread, a chest's
 * open side, a bed's head. See the {@code facing} relation (#36).
 *
 * <p>Minecraft-free, like the rest of {@code core}: mirrors the six axis directions
 * {@code net.minecraft.core.Direction} carries rather than importing it, so the game-facing
 * capture code ({@code SnapshotBlockVolume}) has a one-to-one mapping to translate through and
 * this package stays buildable without Forge on the classpath.
 */
public enum Facing
{
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    public final int dx;
    public final int dy;
    public final int dz;

    Facing(int dx, int dy, int dz)
    {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }
}
