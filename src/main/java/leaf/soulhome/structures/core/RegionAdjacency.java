/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * How the regions of one scan relate to each other - the layer under the Soul Architecture epic
 * (#140), computed once per scan by {@link RegionScanner} and handed to the classifier alongside
 * the regions rather than stored on any {@link SoulRegion}. Keeping it apart means a region stays
 * a description of one region, and the pairwise data stays computed rather than duplicated into
 * every region that shares it.
 *
 * <p>Regions are addressed by their index into the list the scan returned. Every answer here is
 * symmetric and independent of the order the scanner reached the regions in: the relationship
 * between A and B is the same whichever was found first, which is what keeps the identity-hash
 * fold in {@link SoulRegion} sound and two identical houses scoring identically.
 *
 * <h2>Four questions, two traversals</h2>
 *
 * <ul>
 *   <li><b>Shared shell cells</b> - how many boundary cells two rooms have in common. Read off the
 *       shells the scanner already computed for #137; an open-air region has no shell and so
 *       never shares one.</li>
 *   <li><b>Connectivity</b> - whether a path exists between two interiors through cells a player
 *       could walk, and how many cells long it is. <b>Doors, trapdoors and fence gates are crossed
 *       here</b>, which is the opposite of what region detection does with them. Both are right,
 *       for different questions: the scanner treats a door as a wall because an open door that
 *       leaked would merge every room joined by a corridor into one region and make a player's
 *       buffs blink out as they walked through their own front door; a bond treats a door as a
 *       way through because a door is the clearest possible evidence that two rooms belong to one
 *       building. Do not "fix" either to match the other - see the "Doors are walls" paragraph
 *       on {@link RegionScanner}.</li>
 *   <li><b>Separation</b> - the shortest distance between two regions through crossable space,
 *       geodesic rather than straight-line for the same reason the open-air cluster reach is: a
 *       wall between two rooms should mean something, and straight-line distance means solid
 *       matter is not a boundary.</li>
 *   <li><b>Footprint</b> - which columns each region stands in, holes filled, so a garden inside a
 *       courtyard ring reads as inside it; and each region's vertical extent, judged on the air of
 *       a room rather than its shell, so a cellar under a kitchen is beneath it even though the two
 *       share a slab.</li>
 * </ul>
 *
 * <p>Connectivity and separation cost a bounded flood per region rather than one per pair, and
 * only out to {@link #reach()} cells - the furthest any loaded bond could grade - so a pack that
 * declares no bonds pays nothing for them at all. Beyond the reach a pair reads as
 * {@link #UNREACHABLE}, which every relation grades as zero.
 */
public final class RegionAdjacency
{
    /** No path, or none within {@link #reach()}. Grades as zero in every relation. */
    public static final int UNREACHABLE = Integer.MAX_VALUE;

    private final int reach;
    private final int[] shellCells;
    private final int[][] sharedShellCells;
    private final int[][] pathLength;
    private final int[][] separation;
    private final int[] footprint;
    private final int[][] footprintOverlap;
    private final int[] interiorMinY;
    private final int[] interiorMaxY;

    RegionAdjacency(
            int reach,
            int[] shellCells,
            int[][] sharedShellCells,
            int[][] pathLength,
            int[][] separation,
            int[] footprint,
            int[][] footprintOverlap,
            int[] interiorMinY,
            int[] interiorMaxY)
    {
        this.reach = reach;
        this.shellCells = shellCells;
        this.sharedShellCells = sharedShellCells;
        this.pathLength = pathLength;
        this.separation = separation;
        this.footprint = footprint;
        this.footprintOverlap = footprintOverlap;
        this.interiorMinY = interiorMinY;
        this.interiorMaxY = interiorMaxY;
    }

    /** No relationships at all between this many regions: nothing shares, touches or connects. */
    public static RegionAdjacency none(int regionCount)
    {
        int[][] unreachable = new int[regionCount][regionCount];

        for (int[] row : unreachable)
        {
            java.util.Arrays.fill(row, UNREACHABLE);
        }

        return new RegionAdjacency(
                0,
                new int[regionCount],
                new int[regionCount][regionCount],
                unreachable,
                unreachable,
                new int[regionCount],
                new int[regionCount][regionCount],
                new int[regionCount],
                new int[regionCount]);
    }

    public int regionCount()
    {
        return this.shellCells.length;
    }

    /** How far, in cells, the connectivity and separation floods were run. */
    public int reach()
    {
        return this.reach;
    }

    /** Cells in this region's shell - zero for an open-air region, which has none. */
    public int shellCells(int region)
    {
        return this.shellCells[region];
    }

    /** Boundary cells the two have in common - a shared wall, floor or ceiling. */
    public int sharedShellCells(int a, int b)
    {
        return a == b ? 0 : this.sharedShellCells[a][b];
    }

    /**
     * Cells strictly between the two interiors along the shortest walkable path - a doorway in a
     * shared wall is 1, a corridor its length plus the two walls it passes through - or
     * {@link #UNREACHABLE}.
     */
    public int pathLength(int a, int b)
    {
        return a == b ? 0 : this.pathLength[a][b];
    }

    public boolean connected(int a, int b)
    {
        return pathLength(a, b) != UNREACHABLE;
    }

    /**
     * Crossable cells strictly between the two regions along the shortest route through space
     * that is not a full block - zero when they touch or share a wall - or {@link #UNREACHABLE}.
     */
    public int separation(int a, int b)
    {
        return a == b ? 0 : this.separation[a][b];
    }

    /** Columns this region stands in, with anything it closes around in plan filled in. */
    public int footprint(int region)
    {
        return this.footprint[region];
    }

    /** Columns the two regions' footprints have in common. */
    public int footprintOverlap(int a, int b)
    {
        return a == b ? this.footprint[a] : this.footprintOverlap[a][b];
    }

    /**
     * Whether {@code upper}'s air stands wholly above {@code lower}'s, over some of the same
     * ground. A room's own shell is ignored, so two rooms sharing one slab still stack.
     */
    public boolean isAbove(int upper, int lower)
    {
        return upper != lower
                && this.interiorMinY[upper] > this.interiorMaxY[lower]
                && footprintOverlap(upper, lower) > 0;
    }

    /** Cells between the two vertically - the thickness of what separates them. Meaningful only if {@link #isAbove}. */
    public int verticalGap(int upper, int lower)
    {
        return this.interiorMinY[upper] - this.interiorMaxY[lower] - 1;
    }

    /** Whether the two regions' air overlaps in height at all - on the same storey, roughly. */
    public boolean sharesStorey(int a, int b)
    {
        return this.interiorMinY[a] <= this.interiorMaxY[b] && this.interiorMinY[b] <= this.interiorMaxY[a];
    }

    /** Whether the two have any relationship at all worth folding into an identity hash. */
    public boolean related(int a, int b)
    {
        return a != b
                && (sharedShellCells(a, b) > 0
                || connected(a, b)
                || separation(a, b) != UNREACHABLE
                || footprintOverlap(a, b) > 0);
    }
}
