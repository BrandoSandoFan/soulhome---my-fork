/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
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

    private final BlockVolume volume;
    private final ScanSettings settings;
    private final Predicate<BlockSignature> signalFilter;
    private final Predicate<BlockSignature> geometryFilter;

    private final RegionBounds bounds;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] flags;

    private RegionScanner(
            BlockVolume volume,
            Predicate<BlockSignature> signalFilter,
            Predicate<BlockSignature> geometryFilter,
            ScanSettings settings)
    {
        this.volume = volume;
        this.settings = settings;
        this.signalFilter = signalFilter;
        this.geometryFilter = geometryFilter == null ? signature -> false : geometryFilter;

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
        if (!isScannable(volume.bounds(), settings))
        {
            throw new IllegalArgumentException(
                    "Scan volume " + volume.bounds() + " holds " + volume.bounds().volume()
                            + " cells, above the limit of " + settings.maxScannedCells());
        }

        return new RegionScanner(volume, signalFilter, geometryFilter, settings).run();
    }

    private List<SoulRegion> run()
    {
        List<SoulRegion> regions = new ArrayList<>();

        markOutside();
        findEnclosedRegions(regions);

        // A building's own footprint belongs to that building. Without this, the outer corners and
        // edges of a room - which touch no interior air and so are never claimed as its shell -
        // are left loose, and a house with bookshelf walls sprouts a phantom open-air "region"
        // made of its own exterior.
        List<RegionBounds> buildings = new ArrayList<>(regions.size());

        for (SoulRegion region : regions)
        {
            buildings.add(region.bounds());
        }

        findOpenRegions(regions, buildings);

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
     * @return the room grown from this cell, or {@code null} if the pocket is too large to be one
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

        return oversized ? null : buildEnclosedRegion(interior);
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

    // region open-air clusters

    /**
     * Density-based clustering over signal blocks that belong to no room. Seeded on a signal block
     * and grown through every signal block within {@link ScanSettings#clusterRadius}, so a field
     * of wheat with a gap in it is still one farm.
     */
    private void findOpenRegions(List<SoulRegion> regions, List<RegionBounds> buildings)
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

                    if ((this.flags[index] & FLAG_CLAIMED) != 0 || isInside(buildings, x, y, z))
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

        for (int seed : seeds)
        {
            if ((this.flags[seed] & FLAG_CLAIMED) != 0)
            {
                continue;
            }

            SoulRegion region = growCluster(seed, signalCells, buildings);

            if (region != null)
            {
                regions.add(region);
            }
        }
    }

    private SoulRegion growCluster(int seed, Set<Integer> signalCells, List<RegionBounds> buildings)
    {
        final int radius = this.settings.clusterRadius();

        IntStack stack = new IntStack();
        IntStack cluster = new IntStack();
        Set<Integer> clusterCells = new HashSet<>();

        this.flags[seed] |= FLAG_CLAIMED;
        stack.push(seed);

        while (!stack.isEmpty())
        {
            final int index = stack.pop();
            cluster.push(index);
            clusterCells.add(index);

            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dy = -radius; dy <= radius; dy++)
                {
                    for (int dz = -radius; dz <= radius; dz++)
                    {
                        final int nx = x + dx;
                        final int ny = y + dy;
                        final int nz = z + dz;

                        if (!this.bounds.contains(nx, ny, nz))
                        {
                            continue;
                        }

                        final int neighbour = index(nx, ny, nz);

                        if ((this.flags[neighbour] & FLAG_CLAIMED) != 0 || !signalCells.contains(neighbour))
                        {
                            continue;
                        }

                        this.flags[neighbour] |= FLAG_CLAIMED;
                        stack.push(neighbour);
                    }
                }
            }
        }

        if (cluster.size() < this.settings.minClusterSize())
        {
            // too sparse to be a deliberate build - a single planted sapling is not a farm.
            // Left claimed so it is not reconsidered as another cluster's seed.
            return null;
        }

        // one cell of slack around the signal blocks, clamped to the scan volume. A field of wheat
        // has its bounding box at crop height, and the farmland holding it up is a layer below;
        // without the slack the ground a farm is grown on would not count as part of the farm.
        // Anything already claimed by a room is skipped below, so the slack cannot steal walls.
        RegionBounds clusterBounds = clamp(boundsOf(cluster).expand(1));
        BlockCounts.Builder contents = BlockCounts.builder();
        RegionGeometry.Builder geometry = RegionGeometry.builder(this.settings.maxGeometryCells());

        // everything standing in the cluster's footprint counts, not just the signal blocks:
        // the farmland under the wheat and the barrel in the barn are part of the farm too
        for (int x = clusterBounds.minX(); x <= clusterBounds.maxX(); x++)
        {
            for (int y = clusterBounds.minY(); y <= clusterBounds.maxY(); y++)
            {
                for (int z = clusterBounds.minZ(); z <= clusterBounds.maxZ(); z++)
                {
                    final int index = index(x, y, z);

                    if (this.volume.passabilityAt(x, y, z) == Passability.EMPTY)
                    {
                        continue;
                    }

                    // skip anything already spoken for by a room or an earlier cluster - but not
                    // this cluster's own signal blocks, which we claimed on the way in
                    if (!clusterCells.contains(index)
                            && ((this.flags[index] & FLAG_CLAIMED) != 0 || isInside(buildings, x, y, z)))
                    {
                        continue;
                    }

                    this.flags[index] |= FLAG_CLAIMED;
                    BlockSignature signature = this.volume.signatureAt(x, y, z);
                    contents.add(signature);
                    indexIfInteresting(geometry, x, y, z, signature);
                }
            }
        }

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

    private static boolean isInside(List<RegionBounds> buildings, int x, int y, int z)
    {
        for (RegionBounds building : buildings)
        {
            if (building.contains(x, y, z))
            {
                return true;
            }
        }

        return false;
    }

    private RegionBounds clamp(RegionBounds box)
    {
        return new RegionBounds(
                Math.max(box.minX(), this.bounds.minX()),
                Math.max(box.minY(), this.bounds.minY()),
                Math.max(box.minZ(), this.bounds.minZ()),
                Math.min(box.maxX(), this.bounds.maxX()),
                Math.min(box.maxY(), this.bounds.maxY()),
                Math.min(box.maxZ(), this.bounds.maxZ()));
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
    }
}
