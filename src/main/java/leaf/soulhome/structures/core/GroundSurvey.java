/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * What one soulhome's columns hold, flattened to the XZ plane - the only thing terrain growth
 * (#158) needs to know about a soulhome before it can decide where ground may appear.
 *
 * <p>Minecraft-free on purpose, like everything else in this package: the game side fills one of
 * these in from chunk heightmaps ({@code TerrainGrowthService}), and the tests build one out of
 * ASCII art. That is what lets "never generate over anything a player built" be proved without
 * booting a server, which for #160 - the highest-consequence failure in the epic - is worth more
 * than the usual convenience.
 *
 * <h2>Three classes, and the distinction between two of them is the whole of the safety</h2>
 *
 * {@link Kind#GROUND} and {@link Kind#BUILT} are both "there is something in this column". They are
 * separated because one of them is what the apron grows <i>from</i> and the other is what it must
 * keep away from. A patio laid at floor level is ground and the apron grows out of it; a bridge
 * into the verge is built, keeps a moat, and the apron grows around it. Collapsing the two would
 * either shadow the island behind its own moat or grow an apron at bridge height in open sky.
 *
 * <p>{@link Kind#UNKNOWN} is the fourth thing and exists for the same reason
 * {@code Capture.Outcome.UNREADABLE} does: a column in a chunk that could not be read is not an
 * empty column, and treating it as one is how generated terrain would land on top of a build
 * nobody could see.
 */
public final class GroundSurvey
{
    /** What one column holds, as far as growth is concerned. */
    public enum Kind
    {
        /** Nothing at all in the searched range. The only thing that may become apron. */
        VOID,

        /**
         * Something, and its highest block sits inside the ground band - the island's own surface,
         * or a floor the player laid at that height. Seeds the band.
         */
        GROUND,

        /**
         * Something standing above the ground band. Never generated into, and never generated
         * against: {@code ApronPlanner} keeps a clearance moat around every one of these.
         */
        BUILT,

        /** Not readable. Neither a seed nor a candidate, and it blocks nothing. */
        UNKNOWN
    }

    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final byte[] kinds;
    private final int[] surfaceY;

    public GroundSurvey(int minX, int minZ, int maxX, int maxZ)
    {
        if (maxX < minX || maxZ < minZ)
        {
            throw new IllegalArgumentException(
                    "Survey bounds are inverted: (" + minX + "," + minZ + ") to (" + maxX + "," + maxZ + ")");
        }

        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.kinds = new byte[this.sizeX * this.sizeZ];
        this.surfaceY = new int[this.sizeX * this.sizeZ];

        // UNKNOWN rather than VOID is the safe initial state: a survey that was interrupted before
        // it reached a chunk must not have that chunk read back as empty ground to build over
        java.util.Arrays.fill(this.kinds, (byte) Kind.UNKNOWN.ordinal());
    }

    public int minX()
    {
        return this.minX;
    }

    public int minZ()
    {
        return this.minZ;
    }

    public int maxX()
    {
        return this.minX + this.sizeX - 1;
    }

    public int maxZ()
    {
        return this.minZ + this.sizeZ - 1;
    }

    public int sizeX()
    {
        return this.sizeX;
    }

    public int sizeZ()
    {
        return this.sizeZ;
    }

    public boolean contains(int x, int z)
    {
        return x >= this.minX && x <= maxX() && z >= this.minZ && z <= maxZ();
    }

    /**
     * Record what one column holds. {@code surface} is only read back for a {@link Kind#GROUND}
     * column - it is the height an apron grown from that column inherits.
     */
    public void set(int x, int z, Kind kind, int surface)
    {
        final int index = index(x, z);
        this.kinds[index] = (byte) kind.ordinal();
        this.surfaceY[index] = surface;
    }

    public Kind kindAt(int x, int z)
    {
        return contains(x, z) ? KINDS[this.kinds[index(x, z)]] : Kind.UNKNOWN;
    }

    public int surfaceAt(int x, int z)
    {
        return contains(x, z) ? this.surfaceY[index(x, z)] : 0;
    }

    /** Cached: {@code values()} allocates, and this is read once per column per pass. */
    private static final Kind[] KINDS = Kind.values();

    private int index(int x, int z)
    {
        return (x - this.minX) * this.sizeZ + (z - this.minZ);
    }
}
