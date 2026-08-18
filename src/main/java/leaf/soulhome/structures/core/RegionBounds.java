/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * An inclusive integer box. A Minecraft-free stand-in for {@code BoundingBox} so the scanner can
 * be exercised without booting the game.
 */
public record RegionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
{
    public RegionBounds
    {
        if (maxX < minX || maxY < minY || maxZ < minZ)
        {
            throw new IllegalArgumentException(
                    "Region bounds are inverted: (" + minX + "," + minY + "," + minZ + ") to ("
                            + maxX + "," + maxY + "," + maxZ + ")");
        }
    }

    public static RegionBounds of(int x, int y, int z)
    {
        return new RegionBounds(x, y, z, x, y, z);
    }

    public int sizeX()
    {
        return this.maxX - this.minX + 1;
    }

    public int sizeY()
    {
        return this.maxY - this.minY + 1;
    }

    public int sizeZ()
    {
        return this.maxZ - this.minZ + 1;
    }

    /**
     * Cell count, as a long - a carelessly derived scan box can overflow an int.
     */
    public long volume()
    {
        return (long) sizeX() * (long) sizeY() * (long) sizeZ();
    }

    public boolean contains(int x, int y, int z)
    {
        return x >= this.minX && x <= this.maxX
                && y >= this.minY && y <= this.maxY
                && z >= this.minZ && z <= this.maxZ;
    }

    /**
     * Whether the position lies on this box's outer shell. Used both to detect a flood fill
     * reaching open sky and to tell walls from furniture.
     */
    public boolean isOnSurface(int x, int y, int z)
    {
        return x == this.minX || x == this.maxX
                || y == this.minY || y == this.maxY
                || z == this.minZ || z == this.maxZ;
    }

    public RegionBounds expand(int by)
    {
        return new RegionBounds(
                this.minX - by, this.minY - by, this.minZ - by,
                this.maxX + by, this.maxY + by, this.maxZ + by);
    }

    public RegionBounds encompass(int x, int y, int z)
    {
        return new RegionBounds(
                Math.min(this.minX, x), Math.min(this.minY, y), Math.min(this.minZ, z),
                Math.max(this.maxX, x), Math.max(this.maxY, y), Math.max(this.maxZ, z));
    }

    @Override
    public String toString()
    {
        return "[" + this.minX + "," + this.minY + "," + this.minZ
                + " -> " + this.maxX + "," + this.maxY + "," + this.maxZ + "]";
    }
}
