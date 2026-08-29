/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Carves a soulhome into candidate structures.
 *
 * <p>The player builds freely in an otherwise empty void, so before anything can be classified we
 * have to decide what "a structure" even is. Two answers, both needed:
 *
 * <ol>
 *   <li><b>Enclosed volumes.</b> Pockets of space sealed off from the sky. Found by marking
 *       everything reachable from outside the build, then treating each remaining pocket of open
 *       space as a room.</li>
 *   <li><b>Open-air clusters.</b> A wheat field under an open sky is not a room and would be
 *       invisible to the pass above - but a farm is the headline example of this whole feature.
 *       So signal-bearing blocks that belong to no room are clustered by proximity instead.</li>
 * </ol>
 *
 * <h2>Distance is what you can walk, not what you can measure</h2>
 *
 * An open-air cluster reaches out to the next signal block by stepping through cells the fill can
 * actually pass through, up to {@link ScanSettings#clusterRadius} steps of clear space at a time.
 * Straight-line distance would have been cheaper, and was what this did first, but it means solid
 * matter is not a boundary: a farm and a racetrack on opposite sides of a wall still read as one
 * region, and a player who reaches for the obvious fix - build a wall between them - watches it
 * make no difference at all. Walls are the tool players already have for saying "these are two
 * different places", so they have to work.
 *
 * <h2>A building owns its own fabric</h2>
 *
 * A room's shell is only the layer of blocks touching its air, which leaves the rest of the
 * building - a roof over the ceiling, the outer half of a thick wall, the corners of a plain box -
 * belonging to nothing. Those loose blocks used to seed open-air clusters of their own, so a barn
 * with a hay roof came back as a barn plus a mysterious second region sitting on top of it. Blocks
 * within {@link ScanSettings#shellDepth} of a shell are claimed for that building instead. They are
 * not scored - the shell alone is still what a room is worth - they just stop being available to
 * anything else.
 *
 * <p>This used to be done by excluding each room's whole bounding box, which was worse in both
 * directions: it still missed anything above the roofline, and for any build that is not a plain
 * box it swallowed the ground around it. A farm planted in the crook of an L-shaped house fell
 * inside the house's bounding box and was never reported at all.
 *
 * <h2>Doors are walls</h2>
 *
 * Doors, trapdoors and fence gates count as boundary regardless of whether they are open. The
 * alternative - treating an open door as a leak - means a player's buffs blink out every time they
 * walk through their own front door, and it means two rooms joined by a corridor collapse into one
 * region. Both are worse than the modelling inaccuracy.
 *
 * <h2>Determinism</h2>
 *
 * The same build always yields the same regions in the same order. Region identity hashes are used
 * to skip rescans, so instability here would defeat the caching in the scheduling work.
 */
public final class RegionScanner
{
    private static final int FLAG_OUTSIDE = 0x1;
    private static final int FLAG_VISITED = 0x2;
    private static final int FLAG_CLAIMED = 0x4;

    /** 6-neighbourhood. Diagonal leaks would let rooms bleed through corners. */
    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    /**
     * 26-neighbourhood, for everything that is a question about nearness rather than about whether
     * a space is sealed: how far a cluster reaches, what counts as a cell of slack around it, and
     * which blocks are packed against a building. A field planted in a checkerboard is one farm,
     * and the corner of a box is part of that box.
     */
    private static final int[][] NEIGHBOURS_26 = neighbours26();

    private static int[][] neighbours26()
    {
        int[][] offsets = new int[26][];
        int next = 0;

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx != 0 || dy != 0 || dz != 0)
                    {
                        offsets[next++] = new int[]{dx, dy, dz};
                    }
                }
            }
        }

        return offsets;
    }

    private final BlockVolume volume;
    private final ScanSettings settings;
    private final Predicate<BlockSignature> signalFilter;
    private final Predicate<BlockSignature> geometryFilter;
    private final boolean indexClearance;

    private final RegionBounds bounds;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] flags;

    /** Every shell cell every room claimed, so {@link #claimBuildingFabric} knows where to start. */
    private final IntStack shellCells = new IntStack();

    private RegionScanner(
            BlockVolume volume,
            Predicate<BlockSignature> signalFilter,
            Predicate<BlockSignature> geometryFilter,
            boolean indexClearance,
            ScanSettings settings)
    {
        this.volume = volume;
        this.settings = settings;
        this.signalFilter = signalFilter;
        this.geometryFilter = geometryFilter == null ? signature -> false : geometryFilter;
        this.indexClearance = indexClearance;

        this.bounds = volume.bounds();
        this.sizeX = this.bounds.sizeX();
        this.sizeY = this.bounds.sizeY();
        this.sizeZ = this.bounds.sizeZ();
        this.flags = new byte[(int) this.bounds.volume()];
    }

    /**
     * Whether this volume is small enough to scan. Callers should check and log rather than
     * discovering the limit as an exception - a soulhome big enough to trip this is a sign the
     * bounds derivation went wrong, which is worth seeing in the log.
     */
    public static boolean isScannable(RegionBounds bounds, ScanSettings settings)
    {
        final long cells = bounds.volume();
        return cells <= settings.maxScannedCells() && cells <= Integer.MAX_VALUE;
    }

    /**
     * The common case: nothing is structurally interesting yet, so nothing is indexed. Safe and
     * cheap while no archetype has a form to ask {@link RegionGeometry} anything - see
     * {@link #scan(BlockVolume, Predicate, Predicate, ScanSettings)} for when one does.
     *
     * @param signalFilter blocks worth clustering an open-air region around - in practice, every
     *                     block named by some loaded archetype. May be {@code null}, which skips
     *                     open-air detection entirely.
     * @throws IllegalArgumentException if the volume fails {@link #isScannable}
     */
    public static List<SoulRegion> scan(
            BlockVolume volume,
            Predicate<BlockSignature> signalFilter,
            ScanSettings settings)
    {
        return scan(volume, signalFilter, null, settings);
    }

    /**
     * @param signalFilter   blocks worth clustering an open-air region around - in practice, every
     *                       block named by some loaded archetype. May be {@code null}, which skips
     *                       open-air detection entirely.
     * @param geometryFilter blocks worth keeping a position for - in practice, every block named by
     *                       some loaded archetype's structural forms. May be {@code null}, which
     *                       indexes nothing and leaves every {@link SoulRegion#geometry()} empty.
     * @throws IllegalArgumentException if the volume fails {@link #isScannable}
     */
    public static List<SoulRegion> scan(
            BlockVolume volume,
            Predicate<BlockSignature> signalFilter,
            Predicate<BlockSignature> geometryFilter,
            ScanSettings settings)
    {
        return scan(volume, signalFilter, geometryFilter, false, settings);
    }

    /**
     * @param indexClearance whether to also record which cells are {@link Passability#BLOCKING}, so
     *                       {@link RegionGeometry#isBlocked} can answer the {@code across} relation's
     *                       {@code require_clear} check (#29). Costs nothing beyond a set insertion
     *                       at cells the scanner is visiting anyway - see
     *                       {@code ArchetypeSignals#needsClearance} for how a caller decides whether
     *                       any loaded form actually needs this before paying for it.
     * @throws IllegalArgumentException if the volume fails {@link #isScannable}
     */
    public static List<SoulRegion> scan(
            BlockVolume volume,
            Predicate<BlockSignature> signalFilter,
            Predicate<BlockSignature> geometryFilter,
            boolean indexClearance,
            ScanSettings settings)
    {
        if (!isScannable(volume.bounds(), settings))
        {
            throw new IllegalArgumentException(
                    "Scan volume " + volume.bounds() + " holds " + volume.bounds().volume()
                            + " cells, above the limit of " + settings.maxScannedCells());
        }

        return new RegionScanner(volume, signalFilter, geometryFilter, indexClearance, settings).run();
    }

    private List<SoulRegion> run()
    {
        List<SoulRegion> regions = new ArrayList<>();

        markOutside();
        findEnclosedRegions(regions);
        claimBuildingFabric();
        findOpenRegions(regions);

        return capRegions(regions);
    }

    // region enclosed volumes

    /**
     * Flood in from every face of the scan box, so that anything the open sky can reach is known
     * before any pocket is considered. A room with a hole in its roof connects to this fill and so
     * is correctly never offered as a room.
     */
    private void markOutside()
    {
        IntStack stack = new IntStack();

        for (int x = this.bounds.minX(); x <= this.bounds.maxX(); x++)
        {
            for (int y = this.bounds.minY(); y <= this.bounds.maxY(); y++)
            {
                for (int z = this.bounds.minZ(); z <= this.bounds.maxZ(); z++)
                {
                    if (this.bounds.isOnSurface(x, y, z))
                    {
                        seedOutside(stack, x, y, z);
                    }
                }
            }
        }

        drainOutside(stack);
    }

    private void seedOutside(IntStack stack, int x, int y, int z)
    {
        if (this.volume.passabilityAt(x, y, z) == Passability.BLOCKING)
        {
            return;
        }

        final int index = index(x, y, z);

        if ((this.flags[index] & FLAG_OUTSIDE) == 0)
        {
            this.flags[index] |= FLAG_OUTSIDE;
            stack.push(index);
        }
    }

    private void drainOutside(IntStack stack)
    {
        while (!stack.isEmpty())
        {
            final int index = stack.pop();
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int[] offset : NEIGHBOURS)
            {
                final int nx = x + offset[0];
                final int ny = y + offset[1];
                final int nz = z + offset[2];

                if (!this.bounds.contains(nx, ny, nz))
                {
                    continue;
                }

                final int neighbour = index(nx, ny, nz);

                if ((this.flags[neighbour] & FLAG_OUTSIDE) != 0)
                {
                    continue;
                }

                if (this.volume.passabilityAt(nx, ny, nz) == Passability.BLOCKING)
                {
                    continue;
                }

                this.flags[neighbour] |= FLAG_OUTSIDE;
                stack.push(neighbour);
            }
        }
    }

    /**
     * Every remaining pocket of space is, by construction, sealed. Each becomes a candidate room
     * unless it is implausibly large - past a point, an enclosed space is architecture, not a room,
     * and scoring it as one lets a player wrap a wall around their whole island.
     */
    private void findEnclosedRegions(List<SoulRegion> regions)
    {
        for (int x = this.bounds.minX(); x <= this.bounds.maxX(); x++)
        {
            for (int y = this.bounds.minY(); y <= this.bounds.maxY(); y++)
            {
                for (int z = this.bounds.minZ(); z <= this.bounds.maxZ(); z++)
                {
                    final int index = index(x, y, z);

                    if (this.flags[index] != 0)
                    {
                        continue;
                    }

                    if (this.volume.passabilityAt(x, y, z) == Passability.BLOCKING)
                    {
                        continue;
                    }

                    SoulRegion region = collectPocket(index);

                    if (region != null)
                    {
                        regions.add(region);
                    }
                }
            }
        }
    }

    /**
     * @return the room grown from this cell, or {@code null} if the pocket is too large or too
     *         small to be one
     */
    private SoulRegion collectPocket(int seed)
    {
        IntStack stack = new IntStack();
        IntStack interior = new IntStack();

        this.flags[seed] |= FLAG_VISITED;
        stack.push(seed);

        boolean oversized = false;

        while (!stack.isEmpty())
        {
            final int index = stack.pop();

            if (!oversized)
            {
                interior.push(index);

                // past this size the pocket is architecture rather than a room. Keep draining so
                // the whole thing stays marked visited, but stop accumulating it.
                oversized = interior.size() > this.settings.maxRoomVolume();
            }

            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int[] offset : NEIGHBOURS)
            {
                final int nx = x + offset[0];
                final int ny = y + offset[1];
                final int nz = z + offset[2];

                if (!this.bounds.contains(nx, ny, nz))
                {
                    continue;
                }

                final int neighbour = index(nx, ny, nz);

                if (this.flags[neighbour] != 0)
                {
                    continue;
                }

                if (this.volume.passabilityAt(nx, ny, nz) == Passability.BLOCKING)
                {
                    continue;
                }

                this.flags[neighbour] |= FLAG_VISITED;
                stack.push(neighbour);
            }
        }

        if (oversized)
        {
            return null;
        }

        if (interior.size() < this.settings.minRoomVolume())
        {
            // a void inside a thick wall, the gap behind a stair, the shaft up a hollow pillar.
            // Every build of any complexity has several, none of them is a room, and offering each
            // one as a region leaves the player's lens full of boxes around nothing.
            //
            // Still marked visited above, so this pocket is not walked again; deliberately not
            // claimed, so the blocks around it stay available to whatever they are actually part of.
            return null;
        }

        return buildEnclosedRegion(interior);
    }

    private SoulRegion buildEnclosedRegion(IntStack interior)
    {
        RegionBounds interiorBounds = boundsOf(interior);

        BlockCounts.Builder boundary = BlockCounts.builder();
        BlockCounts.Builder contents = BlockCounts.builder();
        RegionGeometry.Builder geometry = RegionGeometry.builder(this.settings.maxGeometryCells());
        RegionBounds regionBounds = interiorBounds;

        // blocks standing in the room itself: torches, crops, carpets, water
        for (int i = 0; i < interior.size(); i++)
        {
            final int index = interior.get(i);
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            this.flags[index] |= FLAG_CLAIMED;

            if (this.volume.passabilityAt(x, y, z) == Passability.PASSABLE)
            {
                BlockSignature signature = this.volume.signatureAt(x, y, z);
                contents.add(signature);
                indexIfInteresting(geometry, x, y, z, signature);
            }
        }

        // the shell: every solid block touching the room.
        // Deduped per region rather than globally, so two rooms sharing a wall both get credit for
        // it. Global dedup would make a room's score depend on which of its neighbours happened to
        // be scanned first, and two identical studies should score identically. Repeated rooms of
        // one archetype are handled by diminishing returns in the balance pass, not here.
        Set<Integer> shellSeen = new HashSet<>();
        IntStack shell = new IntStack();

        for (int i = 0; i < interior.size(); i++)
        {
            final int index = interior.get(i);
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int[] offset : NEIGHBOURS)
            {
                final int nx = x + offset[0];
                final int ny = y + offset[1];
                final int nz = z + offset[2];

                if (!this.bounds.contains(nx, ny, nz))
                {
                    continue;
                }

                if (this.volume.passabilityAt(nx, ny, nz) != Passability.BLOCKING)
                {
                    continue;
                }

                final int neighbour = index(nx, ny, nz);

                if (!shellSeen.add(neighbour))
                {
                    continue;
                }

                // still claimed, so open-air clustering does not treat a wall as a loose signal
                this.flags[neighbour] |= FLAG_CLAIMED;
                shell.push(neighbour);
                this.shellCells.push(neighbour);
            }
        }

        for (int i = 0; i < shell.size(); i++)
        {
            final int index = shell.get(i);
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            BlockSignature signature = this.volume.signatureAt(x, y, z);
            regionBounds = regionBounds.encompass(x, y, z);
            indexIfInteresting(geometry, x, y, z, signature);

            // every shell cell is BLOCKING by construction - it was only pushed here because a
            // passabilityAt check just above said so - so this is free: no extra query, just
            // recording what the scanner already knows while it is looking at the cell anyway
            if (this.indexClearance)
            {
                geometry.addBlocked(x, y, z);
            }

            // walls, floor and ceiling sit outside the air's bounding box; anything solid *inside*
            // it is furniture standing in the room - a pillar, an enchanting table, an anvil
            if (interiorBounds.contains(x, y, z))
            {
                contents.add(signature);
            }
            else
            {
                boundary.add(signature);
            }
        }

        geometry.bounds(regionBounds);

        return SoulRegion.create(
                RegionType.ENCLOSED,
                regionBounds,
                boundary.build(),
                contents.build(),
                interior.size(),
                geometry.build());
    }

    private void indexIfInteresting(RegionGeometry.Builder geometry, int x, int y, int z, BlockSignature signature)
    {
        if (signature != null && this.geometryFilter.test(signature))
        {
            geometry.add(x, y, z, signature);
        }
    }

    // endregion

    // region building fabric

    /**
     * Claim the solid blocks packed against each room's shell for that room's building.
     *
     * <p>The shell is only the layer touching a room's air, which leaves a roof over the ceiling,
     * the outer half of a thick wall, and the corners and edges of a plain box owned by nobody -
     * and so free to seed an open-air cluster of their own on top of a building the scan has
     * already understood. Spreads through solid blocks only, so it stops dead at the first cell of
     * air and cannot walk away across the ground.
     *
     * <p>Claimed, not counted: these blocks are excluded from the pass below, but they are not
     * added to any room's boundary. What a room is worth is still what lines it.
     */
    private void claimBuildingFabric()
    {
        final int depth = this.settings.shellDepth();

        if (depth <= 0 || this.shellCells.isEmpty())
        {
            return;
        }

        IntStack layer = this.shellCells;

        for (int step = 0; step < depth && !layer.isEmpty(); step++)
        {
            IntStack next = new IntStack();

            for (int i = 0; i < layer.size(); i++)
            {
                final int index = layer.get(i);
                final int x = xOf(index);
                final int y = yOf(index);
                final int z = zOf(index);

                for (int[] offset : NEIGHBOURS_26)
                {
                    final int nx = x + offset[0];
                    final int ny = y + offset[1];
                    final int nz = z + offset[2];

                    if (!this.bounds.contains(nx, ny, nz))
                    {
                        continue;
                    }

                    final int neighbour = index(nx, ny, nz);

                    if ((this.flags[neighbour] & FLAG_CLAIMED) != 0)
                    {
                        continue;
                    }

                    if (this.volume.passabilityAt(nx, ny, nz) != Passability.BLOCKING)
                    {
                        continue;
                    }

                    this.flags[neighbour] |= FLAG_CLAIMED;
                    next.push(neighbour);
                }
            }

            layer = next;
        }
    }

    // endregion

    // region open-air clusters

    /**
     * Density-based clustering over signal blocks that belong to no room. Seeded on a signal block
     * and grown outwards through space the fill can pass through, so a field of wheat with a gap in
     * it is still one farm, while a farm and a racetrack with a wall between them are two things.
     */
    private void findOpenRegions(List<SoulRegion> regions)
    {
        if (this.signalFilter == null)
        {
            return;
        }

        List<Integer> seeds = new ArrayList<>();
        Set<Integer> signalCells = new HashSet<>();

        for (int x = this.bounds.minX(); x <= this.bounds.maxX(); x++)
        {
            for (int y = this.bounds.minY(); y <= this.bounds.maxY(); y++)
            {
                for (int z = this.bounds.minZ(); z <= this.bounds.maxZ(); z++)
                {
                    final int index = index(x, y, z);

                    if ((this.flags[index] & FLAG_CLAIMED) != 0)
                    {
                        continue;
                    }

                    if (this.volume.passabilityAt(x, y, z) == Passability.EMPTY)
                    {
                        continue;
                    }

                    BlockSignature signature = this.volume.signatureAt(x, y, z);

                    if (signature != null && this.signalFilter.test(signature))
                    {
                        seeds.add(index);
                        signalCells.add(index);
                    }
                }
            }
        }

        if (seeds.isEmpty())
        {
            return;
        }

        // how much clear space a cluster still has left to cross at each cell it has reached.
        // Allocated once and undone after each cluster rather than reallocated per seed: a soulhome
        // full of torches has a great many seeds and only one of them is ever growing at a time.
        byte[] reach = new byte[this.flags.length];

        for (int seed : seeds)
        {
            if ((this.flags[seed] & FLAG_CLAIMED) != 0)
            {
                continue;
            }

            SoulRegion region = growCluster(seed, signalCells, reach);

            if (region != null)
            {
                regions.add(region);
            }
        }
    }

    /**
     * Grow one cluster out from a signal block.
     *
     * <p>Reaching the next signal block costs a step per cell of clear space crossed, and arriving
     * at one refills the allowance, so {@link ScanSettings#clusterRadius} is the widest gap a
     * cluster will bridge rather than the size of the whole thing. Only cells the fill can pass
     * through are crossable, which is the whole point: a wall, a floor or another structure's
     * blocks end the spread, and neither a straight line through solid rock nor a path through
     * somebody else's living room counts as nearness.
     *
     * @param reach scratch space shared between clusters, left as it was found
     */
    private SoulRegion growCluster(int seed, Set<Integer> signalCells, byte[] reach)
    {
        final int radius = this.settings.clusterRadius();

        IntStack frontier = new IntStack();
        IntStack cluster = new IntStack();
        IntStack touched = new IntStack();

        this.flags[seed] |= FLAG_CLAIMED;
        cluster.push(seed);
        reach[seed] = (byte) radius;
        touched.push(seed);
        frontier.push(seed);

        while (!frontier.isEmpty())
        {
            final int index = frontier.pop();
            final int budget = reach[index];

            if (budget <= 0)
            {
                continue;
            }

            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int[] offset : NEIGHBOURS_26)
            {
                final int nx = x + offset[0];
                final int ny = y + offset[1];
                final int nz = z + offset[2];

                if (!this.bounds.contains(nx, ny, nz))
                {
                    continue;
                }

                final int neighbour = index(nx, ny, nz);

                if ((this.flags[neighbour] & FLAG_CLAIMED) != 0)
                {
                    // this cluster's own, a room's, or an earlier cluster's - either way not a way
                    // through, so one structure cannot reach another by crossing a third
                    continue;
                }

                if (signalCells.contains(neighbour))
                {
                    this.flags[neighbour] |= FLAG_CLAIMED;
                    cluster.push(neighbour);
                    reach[neighbour] = (byte) radius;
                    touched.push(neighbour);
                    frontier.push(neighbour);
                    continue;
                }

                if (this.volume.passabilityAt(nx, ny, nz) == Passability.BLOCKING)
                {
                    continue;
                }

                final int remaining = budget - 1;

                if (remaining > reach[neighbour])
                {
                    if (reach[neighbour] == 0)
                    {
                        touched.push(neighbour);
                    }

                    reach[neighbour] = (byte) remaining;
                    frontier.push(neighbour);
                }
            }
        }

        for (int i = 0; i < touched.size(); i++)
        {
            reach[touched.get(i)] = 0;
        }

        if (cluster.size() < this.settings.minClusterSize())
        {
            // too sparse to be a deliberate build - a single planted sapling is not a farm.
            // Left claimed so it is not reconsidered as another cluster's seed.
            return null;
        }

        return buildOpenRegion(cluster);
    }

    /**
     * Take in one cell of slack around the cluster's own blocks. A field of wheat sits at crop
     * height and the farmland holding it up is a layer below, so without the slack the ground a
     * farm is grown on would not count as part of the farm.
     *
     * <p>The slack follows the shape of the cluster rather than its bounding box. A box is an
     * over-estimate of everything but a solid rectangle, and taking one meant a sprawling or
     * L-shaped cluster swallowed whatever happened to be standing in the space it did not occupy.
     */
    private SoulRegion buildOpenRegion(IntStack cluster)
    {
        IntStack absorbed = new IntStack();

        for (int i = 0; i < cluster.size(); i++)
        {
            absorbed.push(cluster.get(i));
        }

        for (int i = 0; i < cluster.size(); i++)
        {
            final int index = cluster.get(i);
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int[] offset : NEIGHBOURS_26)
            {
                final int nx = x + offset[0];
                final int ny = y + offset[1];
                final int nz = z + offset[2];

                if (!this.bounds.contains(nx, ny, nz))
                {
                    continue;
                }

                final int neighbour = index(nx, ny, nz);

                // already spoken for by a room, an earlier cluster, or this one
                if ((this.flags[neighbour] & FLAG_CLAIMED) != 0)
                {
                    continue;
                }

                if (this.volume.passabilityAt(nx, ny, nz) == Passability.EMPTY)
                {
                    continue;
                }

                this.flags[neighbour] |= FLAG_CLAIMED;
                absorbed.push(neighbour);
            }
        }

        final RegionBounds clusterBounds = boundsOf(absorbed);

        // index order is x, y, z order, so this is the sweep the bounding-box version did - which
        // keeps what lands in a truncated geometry index the same from one scan to the next
        BlockCounts.Builder contents = BlockCounts.builder();
        RegionGeometry.Builder geometry = RegionGeometry.builder(this.settings.maxGeometryCells());

        for (int index : absorbed.toSortedArray())
        {
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            BlockSignature signature = this.volume.signatureAt(x, y, z);
            contents.add(signature);
            indexIfInteresting(geometry, x, y, z, signature);

            if (this.indexClearance && this.volume.passabilityAt(x, y, z) == Passability.BLOCKING)
            {
                geometry.addBlocked(x, y, z);
            }
        }

        geometry.bounds(clusterBounds);

        final long boundsVolume = clusterBounds.volume();

        return SoulRegion.create(
                RegionType.OPEN,
                clusterBounds,
                BlockCounts.empty(),
                contents.build(),
                (int) Math.min(boundsVolume, Integer.MAX_VALUE),
                geometry.build());
    }

    // endregion

    /**
     * Keep the richest regions when a build produces more than the cap. Sorting by block count
     * rather than discovery order means the cap trims sheds, not the great hall.
     */
    private List<SoulRegion> capRegions(List<SoulRegion> regions)
    {
        Comparator<SoulRegion> byInterest = Comparator
                .comparingInt((SoulRegion region) -> region.allBlocks().total()).reversed()
                .thenComparingInt(region -> region.bounds().minX())
                .thenComparingInt(region -> region.bounds().minY())
                .thenComparingInt(region -> region.bounds().minZ());

        regions.sort(byInterest);

        return regions.size() <= this.settings.maxRegions()
                ? regions
                : new ArrayList<>(regions.subList(0, this.settings.maxRegions()));
    }

    private RegionBounds boundsOf(IntStack cells)
    {
        if (cells.isEmpty())
        {
            throw new IllegalStateException("Cannot take the bounds of an empty cell set");
        }

        final int first = cells.get(0);
        RegionBounds result = RegionBounds.of(xOf(first), yOf(first), zOf(first));

        for (int i = 1; i < cells.size(); i++)
        {
            final int index = cells.get(i);
            result = result.encompass(xOf(index), yOf(index), zOf(index));
        }

        return result;
    }

    // region index packing

    private int index(int x, int y, int z)
    {
        final int dx = x - this.bounds.minX();
        final int dy = y - this.bounds.minY();
        final int dz = z - this.bounds.minZ();
        return (dx * this.sizeY + dy) * this.sizeZ + dz;
    }

    private int xOf(int index)
    {
        return this.bounds.minX() + index / (this.sizeY * this.sizeZ);
    }

    private int yOf(int index)
    {
        return this.bounds.minY() + (index / this.sizeZ) % this.sizeY;
    }

    private int zOf(int index)
    {
        return this.bounds.minZ() + index % this.sizeZ;
    }

    // endregion

    /**
     * Primitive int stack. A soulhome scan touches hundreds of thousands of cells; boxing each of
     * them into an {@code ArrayDeque<Integer>} is the kind of cost that turns a background scan
     * into a stutter.
     */
    private static final class IntStack
    {
        private int[] values = new int[64];
        private int size;

        void push(int value)
        {
            if (this.size == this.values.length)
            {
                int[] grown = new int[this.values.length * 2];
                System.arraycopy(this.values, 0, grown, 0, this.size);
                this.values = grown;
            }

            this.values[this.size++] = value;
        }

        int pop()
        {
            return this.values[--this.size];
        }

        int get(int position)
        {
            return this.values[position];
        }

        int size()
        {
            return this.size;
        }

        boolean isEmpty()
        {
            return this.size == 0;
        }

        int[] toSortedArray()
        {
            int[] copy = new int[this.size];
            System.arraycopy(this.values, 0, copy, 0, this.size);
            Arrays.sort(copy);
            return copy;
        }
    }
}
