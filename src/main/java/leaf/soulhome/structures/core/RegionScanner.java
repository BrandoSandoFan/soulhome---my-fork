/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * An open-air cluster reaches out to the next signal block by stepping through cells it can
 * actually cross, up to {@link ScanSettings#clusterRadius} steps of clear space at a time.
 * Straight-line distance would have been cheaper, and was what this did first, but it means solid
 * matter is not a boundary: a farm and a racetrack on opposite sides of a wall still read as one
 * region, and a player who reaches for the obvious fix - build a wall between them - watches it
 * make no difference at all. Walls are the tool players already have for saying "these are two
 * different places", so they have to work.
 *
 * <p>Only a block filling its whole cell is one of those walls - see {@link Passability}. A fence,
 * a wall, a pane, a slab or a stair is something a player puts <i>inside</i> a build; the track
 * archetype scores fencing as part of a track, and a circuit cut off from its own trackside by its
 * own fence would be the mod disagreeing with itself. Signal blocks are crossed whatever they are
 * made of, so a haystack is taken in whole rather than skinned, and whatever a finished region has
 * closed around is taken in as well: a region is a solid thing, never a ring with a hole in it.
 *
 * <h2>A building owns its own fabric</h2>
 *
 * A room's shell is only the layer of blocks touching its air, which leaves the rest of the
 * building - a roof over the ceiling, the outer half of a thick wall, the corners of a plain box -
 * belonging to nothing. Those loose blocks used to seed open-air clusters of their own, so a barn
 * with a hay roof came back as a barn plus a mysterious second region sitting on top of it. Blocks
 * within {@link ScanSettings#shellDepth} of a shell are claimed for that building instead. They are
 * not scored - the shell alone is still what a room is worth - they just stop being available to
 * anything else. Only full blocks are fabric: the farmland of a roof garden is something standing
 * on the building, not part of it - see {@link #claimBuildingFabric}.
 *
 * <p>This used to be done by excluding each room's whole bounding box, which was worse in both
 * directions: it still missed anything above the roofline, and for any build that is not a plain
 * box it swallowed the ground around it. A farm planted in the crook of an L-shaped house fell
 * inside the house's bounding box and was never reported at all.
 *
 * <h2>A shared wall is one wall, and a floor belongs to the room that stands on it</h2>
 *
 * A shell cell is worth one block in total however many rooms touch it, and a cell a room's air
 * stands on is that room's floor before it is anyone's ceiling. See {@link #creditShells} for the
 * rule, the cases that forced it, and the alternatives that were rejected.
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

    /** 4-neighbourhood within one horizontal layer, for {@link #fillInteriorHoles}. */
    private static final int[][] NEIGHBOURS_IN_PLANE = {
            {1, 0}, {-1, 0},
            {0, 1}, {0, -1}
    };

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
     *                     block named by some loaded archetype that accepts open regions, see
     *                     {@code ArchetypeSignals#openClusterFilterFor}. May be {@code null},
     *                     which skips open-air detection entirely.
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
     *                       block named by some loaded archetype that accepts open regions. May be
     *                       {@code null}, which skips open-air detection entirely.
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
        return scanWithAdjacency(volume, signalFilter, geometryFilter, indexClearance, 0, settings).regions();
    }

    /**
     * The regions, and how they relate to each other - see {@link RegionAdjacency} and the Soul
     * Architecture epic (#140). What {@code StructureScanService} calls.
     *
     * @param adjacencyReach how far, in cells, to look for a path or a route between two regions
     *                       - in practice the furthest any loaded bond could grade, see
     *                       {@code ArchetypeSignals#adjacencyReachFor}. {@code 0} skips both
     *                       traversals, so a pack with no bonds pays nothing for them; shared
     *                       shells and footprints are always computed, being nearly free.
     * @throws IllegalArgumentException if the volume fails {@link #isScannable}
     */
    public static ScanResult scanWithAdjacency(
            BlockVolume volume,
            Predicate<BlockSignature> signalFilter,
            Predicate<BlockSignature> geometryFilter,
            boolean indexClearance,
            int adjacencyReach,
            ScanSettings settings)
    {
        if (!isScannable(volume.bounds(), settings))
        {
            throw new IllegalArgumentException(
                    "Scan volume " + volume.bounds() + " holds " + volume.bounds().volume()
                            + " cells, above the limit of " + settings.maxScannedCells());
        }

        return new RegionScanner(volume, signalFilter, geometryFilter, indexClearance, settings).run(adjacencyReach);
    }

    /** Everything one scan found: the regions, in their final order, and how they relate. */
    public record ScanResult(List<SoulRegion> regions, RegionAdjacency adjacency)
    {
        public ScanResult
        {
            regions = List.copyOf(regions);
        }
    }

    /**
     * One region together with the cells it was built from, which {@link SoulRegion} deliberately
     * does not carry and {@link #computeAdjacency} needs.
     *
     * @param interior for a room, its air; for an open-air region, every cell it took in. What a
     *                 walkable path has to arrive at to have reached the region.
     * @param shell    for a room, the blocks touching its air; empty for an open-air region
     * @param interiorBounds the box around {@code interior} alone - a room's air, not its walls
     */
    private record Built(SoulRegion region, IntStack interior, IntStack shell, RegionBounds interiorBounds)
    {
    }

    private ScanResult run(int adjacencyReach)
    {
        List<Built> built = new ArrayList<>();

        markOutside();
        findEnclosedRegions(built);
        claimBuildingFabric();
        findOpenRegions(built);

        built = capRegions(built);

        RegionAdjacency adjacency = computeAdjacency(built, adjacencyReach);

        return new ScanResult(foldRelationships(built, adjacency), adjacency);
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
        if (this.volume.passabilityAt(x, y, z).stopsFill())
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

                if (this.volume.passabilityAt(nx, ny, nz).stopsFill())
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
     *
     * <p>Every pocket is found before any room is built from one, because what a shell cell is
     * worth to a room depends on which other rooms touch it - see {@link #creditShells}.
     */
    private void findEnclosedRegions(List<Built> regions)
    {
        List<Pocket> pockets = new ArrayList<>();

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

                    if (this.volume.passabilityAt(x, y, z).stopsFill())
                    {
                        continue;
                    }

                    IntStack interior = collectPocket(index);

                    if (interior != null)
                    {
                        pockets.add(shellOf(interior));
                    }
                }
            }
        }

        creditShells(pockets);

        for (Pocket pocket : pockets)
        {
            regions.add(buildEnclosedRegion(pocket));
        }
    }

    /**
     * @return the interior of the room grown from this cell, or {@code null} if the pocket is too
     *         large or too small to be one
     */
    private IntStack collectPocket(int seed)
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

                if (this.volume.passabilityAt(nx, ny, nz).stopsFill())
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

        return interior;
    }

    /**
     * A sealed pocket and the shell around it, before either has been turned into a region.
     *
     * @param interior every air cell of the room, in flood order
     * @param shell    every solid block touching that air, each once, in the order the interior
     *                 first reached it
     * @param floor    for each entry of {@code shell}, whether the room's air stands directly on
     *                 it - the cell is the room's floor - as opposed to only beside or below it
     * @param credit   what each entry of {@code shell} is worth to this room, filled in by
     *                 {@link #creditShells} once every other room's shell is known
     */
    private record Pocket(IntStack interior, IntStack shell, BitSet floor, double[] credit)
    {
    }

    /**
     * The shell: every solid block touching the room's air. Claimed as it is found, so open-air
     * clustering does not later treat a wall as a loose signal.
     */
    private Pocket shellOf(IntStack interior)
    {
        // which slot in the shell a cell landed in, so a cell reached first from the side and
        // then from above can still be marked as the floor it turns out to be
        Map<Integer, Integer> slotOf = new HashMap<>();
        IntStack shell = new IntStack();
        BitSet floor = new BitSet();

        for (int i = 0; i < interior.size(); i++)
        {
            final int index = interior.get(i);
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            this.flags[index] |= FLAG_CLAIMED;

            for (int[] offset : NEIGHBOURS)
            {
                final int nx = x + offset[0];
                final int ny = y + offset[1];
                final int nz = z + offset[2];

                if (!this.bounds.contains(nx, ny, nz))
                {
                    continue;
                }

                if (!this.volume.passabilityAt(nx, ny, nz).stopsFill())
                {
                    continue;
                }

                final int neighbour = index(nx, ny, nz);
                Integer slot = slotOf.get(neighbour);

                if (slot == null)
                {
                    slot = shell.size();
                    slotOf.put(neighbour, slot);
                    this.flags[neighbour] |= FLAG_CLAIMED;
                    shell.push(neighbour);
                    this.shellCells.push(neighbour);
                }

                if (offset[1] < 0)
                {
                    floor.set(slot);
                }
            }
        }

        return new Pocket(interior, shell, floor, new double[shell.size()]);
    }

    /**
     * Decide what each shell cell is worth to each room that touches it.
     *
     * <h2>A shared wall is one wall</h2>
     *
     * A room's shell used to be deduplicated per room and nothing more, so a wall standing between
     * two rooms was scored in full by both. The argument for that was fair: each room really does
     * face one side of the wall, and two identical studies should score identically whatever is
     * next door. What it missed is that a player can choose to subdivide. One long space cut into
     * four by three bookshelf walls placed 18 bookshelves and was credited 36, each room nearly
     * clearing a gate the whole build could not clear once - and the repeated-room falloff that
     * then softened the payout is meant to say "your second library is worth less than your
     * first", not to make up for a first library that was never really there (#137).
     *
     * <p>So a shell cell is worth one block <i>in total</i>, however many rooms touch it. Two
     * rooms either side of a partition get half each; a room's own outer wall, which nothing else
     * touches, still counts in full. Standalone rooms are untouched by this, two identical studies
     * still score identically, and partitioning becomes exactly break-even rather than
     * profitable. The alternative rejected here was crediting a shared cell to one room only:
     * whichever room the scan happened to reach first would win, which is precisely the
     * order-dependence the old comment warned about, and which would defeat the identity hashing
     * that skips rescans.
     *
     * <h2>A floor belongs to the room that stands on it</h2>
     *
     * A slab between two stacked rooms is a different case from a wall between two neighbours,
     * and splitting it evenly gets it wrong in both directions. The player laid that slab
     * <i>for the room above</i>: it is the loft's floorboards, and its underside being visible
     * from the cellar does not make the cellar a room built out of floorboards. Floor a loft in
     * hay and the library beneath it used to be a library holding six hay blocks (#136); halve
     * the credit and it is a library holding three, while the loft has lost half of the floor it
     * really is built out of.
     *
     * <p>So the face a room reaches a cell from decides precedence. A cell some room's air stands
     * directly on is that room's floor, and floors are credited only to the rooms they are floors
     * of - split evenly if, oddly, more than one room stands on the same cell. Every other shared
     * cell - a partition seen from either side, a ceiling nothing stands on - is split evenly
     * among everyone touching it. The room below a shared slab is credited nothing for it, which
     * is the reading a player would give: its ceiling is somebody's floor. The alternative
     * rejected was crediting a ceiling at some reduced weight, which keeps a sliver of the
     * contamination this exists to remove and adds a tuning knob nobody could set from first
     * principles.
     *
     * <p>Only credit changes. Which cells a room's shell contains, which blocks are claimed as
     * building fabric, and what {@link RegionGeometry} indexes are all unchanged: a bed is still
     * against a wall whether or not the room next door shares that wall. The decision is made on
     * how many rooms touch a cell and from which faces, never on the order the rooms were found
     * in, so the same build yields the same credits in the same order whichever room is scanned
     * first.
     */
    private void creditShells(List<Pocket> pockets)
    {
        if (pockets.size() < 2)
        {
            // nothing to share: the common case, and it should cost nothing
            for (Pocket pocket : pockets)
            {
                Arrays.fill(pocket.credit(), 1d);
            }

            return;
        }

        byte[] touches = new byte[this.flags.length];
        byte[] floorTouches = new byte[this.flags.length];

        for (Pocket pocket : pockets)
        {
            for (int i = 0; i < pocket.shell().size(); i++)
            {
                final int cell = pocket.shell().get(i);
                touches[cell]++;

                if (pocket.floor().get(i))
                {
                    floorTouches[cell]++;
                }
            }
        }

        for (Pocket pocket : pockets)
        {
            for (int i = 0; i < pocket.shell().size(); i++)
            {
                final int cell = pocket.shell().get(i);

                if (floorTouches[cell] == 0)
                {
                    pocket.credit()[i] = 1d / touches[cell];
                }
                else
                {
                    pocket.credit()[i] = pocket.floor().get(i) ? 1d / floorTouches[cell] : 0d;
                }
            }
        }
    }

    private Built buildEnclosedRegion(Pocket pocket)
    {
        final IntStack interior = pocket.interior();
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

            if (this.volume.passabilityAt(x, y, z) == Passability.PASSABLE)
            {
                BlockSignature signature = this.volume.signatureAt(x, y, z);
                contents.add(signature);
                indexIfInteresting(geometry, x, y, z, signature);
            }
        }

        final IntStack shell = pocket.shell();

        for (int i = 0; i < shell.size(); i++)
        {
            final int index = shell.get(i);
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            BlockSignature signature = this.volume.signatureAt(x, y, z);
            regionBounds = regionBounds.encompass(x, y, z);
            indexIfInteresting(geometry, x, y, z, signature);

            // every shell cell stops the fill by construction - it was only pushed here because a
            // passabilityAt check just above said so - so this is free: no extra query, just
            // recording what the scanner already knows while it is looking at the cell anyway
            if (this.indexClearance)
            {
                geometry.addBlocked(x, y, z);
            }

            // what this cell is worth to this room, see creditShells - a cell another room owns
            // outright still shapes the bounds and the geometry above, it just scores nothing here
            final double credit = pocket.credit()[i];

            // walls, floor and ceiling sit outside the air's bounding box; anything solid *inside*
            // it is furniture standing in the room - a pillar, an enchanting table, an anvil
            if (interiorBounds.contains(x, y, z))
            {
                contents.add(signature, credit);
            }
            else
            {
                boundary.add(signature, credit);
            }
        }

        geometry.bounds(regionBounds);

        SoulRegion region = SoulRegion.create(
                RegionType.ENCLOSED,
                regionBounds,
                boundary.build(),
                contents.build(),
                interior.size(),
                geometry.build());

        return new Built(region, interior, shell, interiorBounds);
    }

    private void indexIfInteresting(RegionGeometry.Builder geometry, int x, int y, int z, BlockSignature signature)
    {
        if (signature != null && this.geometryFilter.test(signature))
        {
            geometry.add(x, y, z, signature, this.volume.facingAt(x, y, z));
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
     *
     * <h2>Fabric is full blocks</h2>
     *
     * Only a block that fills its cell is fabric - {@link Passability#isFullBlock}, not merely
     * {@link Passability#stopsFill}. This used to claim anything that stopped the fill, and so
     * claimed the farmland of a garden planted on a flat roof: the soil sat directly against the
     * ceiling's outer face, was taken as the building's, and the farm came back as wheat with no
     * ground under it - {@code /soulhome analyse} telling a player their farm was missing farmland
     * while they stood on it (#138).
     *
     * <p>The tempting rules were the direction the spread travelled - upward off a roof, with sky
     * above, is more likely a thing on the roof than part of it - and whether the candidate is the
     * same kind of block as the shell it sits against. Both misfire on the case this pass exists
     * for: a barn's hay roof is also one layer of a different material laid upward off a stone
     * ceiling under open sky, and it has to stay the barn's. What actually separates the two is
     * that hay fills its cell and farmland does not. The same split {@link Passability} already
     * draws for what divides one open-air build from the next holds here too: a fence, a slab, a
     * chest or a tilled field is something a player puts <i>on</i> a building, and a full block
     * against its shell is the building. A single layer of a full-block signal on a roof - one
     * course of ice under a rooftop rail circuit, say - still reads as roof, and that is the
     * trade this makes knowingly rather than the one it makes by accident.
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

                    if (!this.volume.passabilityAt(nx, ny, nz).isFullBlock())
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
     *
     * <p>Done in phases rather than one cluster at a time, because the later phases take in ground
     * a cluster did not grow through - the slack under a field, and whatever a ring of blocks has
     * closed around. Growing every cluster before any of that happens is what stops a rail loop
     * from swallowing the shrine somebody built in its infield: by the time the loop looks at the
     * space it encloses, the shrine is already a structure of its own.
     */
    private void findOpenRegions(List<Built> regions)
    {
        if (this.signalFilter == null)
        {
            return;
        }

        IntStack seeds = new IntStack();
        // one bit of membership per scan cell rather than boxing every signal index into a
        // HashSet<Integer> - signalCells is read inside growCluster's 26-neighbour inner loop, and
        // Integer.valueOf caches only -128..127, so on a real box every lookup there would box a
        // fresh Integer purely to hash it and discard it. See #125.
        boolean[] signalCells = new boolean[this.flags.length];

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
                        seeds.push(index);
                        signalCells[index] = true;
                    }
                }
            }
        }

        if (seeds.isEmpty())
        {
            return;
        }

        // one cell of working space per scan cell, handed from phase to phase and always left
        // zeroed. Allocated once rather than per cluster: a soulhome full of torches has a great
        // many seeds and only one of them is ever being worked on at a time.
        byte[] scratch = new byte[this.flags.length];

        List<IntStack> clusters = new ArrayList<>();

        for (int i = 0; i < seeds.size(); i++)
        {
            final int seed = seeds.get(i);

            if ((this.flags[seed] & FLAG_CLAIMED) != 0)
            {
                continue;
            }

            IntStack cluster = growCluster(seed, signalCells, scratch);

            if (cluster != null)
            {
                clusters.add(cluster);
            }
        }

        List<IntStack> absorbed = new ArrayList<>(clusters.size());
        List<RegionBounds> boxes = new ArrayList<>(clusters.size());

        for (IntStack cluster : clusters)
        {
            IntStack cells = absorbSlack(cluster);
            absorbed.add(cells);
            boxes.add(boundsOf(cells));
        }

        for (int i = 0; i < absorbed.size(); i++)
        {
            fillInteriorHoles(absorbed.get(i), boxes.get(i), scratch);
        }

        for (int i = 0; i < absorbed.size(); i++)
        {
            regions.add(buildOpenRegion(absorbed.get(i), boxes.get(i)));
        }
    }

    /**
     * Grow one cluster out from a signal block.
     *
     * <p>Reaching the next signal block costs a step per cell of clear space crossed, and arriving
     * at one refills the allowance, so {@link ScanSettings#clusterRadius} is the widest gap a
     * cluster will bridge rather than the size of the whole thing. Only a block filling its whole
     * cell ends the spread, which is the whole point: a wall between a farm and a track is a
     * boundary, while the fence around the track and the slabs edging the farm are parts of the
     * builds themselves and would cut them into pieces if they counted.
     *
     * @param reach scratch space, left as it was found
     * @return the signal cells of the cluster, or {@code null} if it is too sparse to be a build
     */
    private IntStack growCluster(int seed, boolean[] signalCells, byte[] reach)
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

                if (signalCells[neighbour])
                {
                    // joined however solid it is. Hay bales, ice and farmland are all full blocks,
                    // and a cluster that could not step into one could not cross its own surface -
                    // a haystack would come back as a hollow shell of its own outside faces.
                    this.flags[neighbour] |= FLAG_CLAIMED;
                    cluster.push(neighbour);
                    reach[neighbour] = (byte) radius;
                    touched.push(neighbour);
                    frontier.push(neighbour);
                    continue;
                }

                if (this.volume.passabilityAt(nx, ny, nz).isFullBlock())
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

        return cluster;
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
    private IntStack absorbSlack(IntStack cluster)
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

        return absorbed;
    }

    /**
     * Take in whatever the region has closed around, so a region is a solid thing rather than a
     * shell with unaccounted space inside it.
     *
     * <p>The infield of a rail loop, the courtyard inside a ring of crops, the stone a raised bed
     * was built around: none of it is reachable from a signal block, so none of it was absorbed,
     * and the region came back as a ring with a hole in the middle. That is wrong twice over. The
     * blocks in the hole belong to this build and went uncounted - and, worse, the clearance index
     * that {@code across ... require_clear} reads is only written for cells the region took in, so
     * a solid infield read back as clear open space and a form that asks for room to move got the
     * answer exactly backwards.
     *
     * <p>Judged layer by layer: within each horizontal slice of the region's box, anything a flood
     * coming in from the edge of that slice cannot reach is enclosed by the region and taken in.
     * Layers rather than the whole box because the builds this is for are flat - a rail circuit is
     * a ring with open sky over its infield, so in three dimensions nothing about it is enclosed at
     * all, and yet the infield is plainly inside the track. Anything a room or an earlier cluster
     * has already claimed is left where it is.
     *
     * <p>The flood is 4-connected while the region is 26-connected, which is deliberate: a ring
     * that closes only across a diagonal has still closed.
     *
     * @param scratch per-cell scratch space, left zeroed
     */
    private void fillInteriorHoles(IntStack absorbed, RegionBounds box, byte[] scratch)
    {
        final byte MINE = 1;
        final byte OUTSIDE = 2;

        IntStack touched = new IntStack();

        for (int i = 0; i < absorbed.size(); i++)
        {
            final int index = absorbed.get(i);
            scratch[index] = MINE;
            touched.push(index);
        }

        IntStack stack = new IntStack();

        for (int y = box.minY(); y <= box.maxY(); y++)
        {
            for (int x = box.minX(); x <= box.maxX(); x++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    final boolean onEdge = x == box.minX() || x == box.maxX()
                            || z == box.minZ() || z == box.maxZ();

                    if (!onEdge)
                    {
                        continue;
                    }

                    final int index = index(x, y, z);

                    if (scratch[index] == 0)
                    {
                        scratch[index] = OUTSIDE;
                        touched.push(index);
                        stack.push(index);
                    }
                }
            }
        }

        while (!stack.isEmpty())
        {
            final int index = stack.pop();
            final int x = xOf(index);
            final int y = yOf(index);
            final int z = zOf(index);

            for (int[] offset : NEIGHBOURS_IN_PLANE)
            {
                final int nx = x + offset[0];
                final int nz = z + offset[1];

                if (!box.contains(nx, y, nz))
                {
                    continue;
                }

                final int neighbour = index(nx, y, nz);

                if (scratch[neighbour] != 0)
                {
                    continue;
                }

                scratch[neighbour] = OUTSIDE;
                touched.push(neighbour);
                stack.push(neighbour);
            }
        }

        // x, y, z order, so which cells a truncated geometry index keeps does not depend on the
        // order the flood happened to run in
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int y = box.minY(); y <= box.maxY(); y++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    final int index = index(x, y, z);

                    if (scratch[index] != 0 || (this.flags[index] & FLAG_CLAIMED) != 0)
                    {
                        continue;
                    }

                    this.flags[index] |= FLAG_CLAIMED;
                    absorbed.push(index);
                }
            }
        }

        for (int i = 0; i < touched.size(); i++)
        {
            scratch[touched.get(i)] = 0;
        }
    }

    private Built buildOpenRegion(IntStack absorbed, RegionBounds box)
    {
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

            if (this.indexClearance && this.volume.passabilityAt(x, y, z).stopsFill())
            {
                geometry.addBlocked(x, y, z);
            }
        }

        geometry.bounds(box);

        final long boundsVolume = box.volume();

        SoulRegion region = SoulRegion.create(
                RegionType.OPEN,
                box,
                BlockCounts.empty(),
                contents.build(),
                (int) Math.min(boundsVolume, Integer.MAX_VALUE),
                geometry.build());

        return new Built(region, absorbed, new IntStack(), box);
    }
    // endregion

    /**
     * Keep the richest regions when a build produces more than the cap. Sorting by block count
     * rather than discovery order means the cap trims sheds, not the great hall.
     */
    private List<Built> capRegions(List<Built> regions)
    {
        Comparator<Built> byInterest = Comparator
                .comparingDouble((Built built) -> built.region().allBlocks().totalCredit()).reversed()
                .thenComparingInt(built -> built.region().bounds().minX())
                .thenComparingInt(built -> built.region().bounds().minY())
                .thenComparingInt(built -> built.region().bounds().minZ());

        regions.sort(byInterest);

        return regions.size() <= this.settings.maxRegions()
                ? regions
                : new ArrayList<>(regions.subList(0, this.settings.maxRegions()));
    }

    // region adjacency (#141, #142)

    /** Six faces, so at most six rooms can touch one cell; anything with more owners is a bug. */
    private static final int OWNER_NONE = 0;

    /** Set on an owner entry for a cell a walkable path has to reach to count as arriving. */
    private static final int OWNER_ARRIVAL = 0x8000;

    /** The distance flood stores one byte per cell, so the reach has to leave room for it. */
    private static final int MAX_ADJACENCY_REACH = 120;

    /**
     * How the final regions relate - see {@link RegionAdjacency} for what each answer means and
     * why doors are crossed here when region detection treats them as walls.
     *
     * <p>Ownership is one map over the scan, written once: which region a cell belongs to, and
     * whether arriving there counts as reaching the region (a room's air, or any cell of an
     * open-air build) rather than merely touching its wall. Every question after that is either a
     * lookup or one bounded flood from the region being asked about, never a flood per pair.
     */
    private RegionAdjacency computeAdjacency(List<Built> built, int adjacencyReach)
    {
        final int count = built.size();
        final int reach = Math.max(0, Math.min(MAX_ADJACENCY_REACH, adjacencyReach));

        int[] shellCells = new int[count];
        int[][] shared = new int[count][count];
        int[][] path = filled(count, RegionAdjacency.UNREACHABLE);
        int[][] separation = filled(count, RegionAdjacency.UNREACHABLE);
        int[] footprint = new int[count];
        int[][] overlap = new int[count][count];
        int[] interiorMinY = new int[count];
        int[] interiorMaxY = new int[count];

        if (count == 0)
        {
            return new RegionAdjacency(reach, shellCells, shared, path, separation, footprint, overlap, interiorMinY, interiorMaxY);
        }

        short[] owner = new short[this.flags.length];
        List<BitSet> footprints = new ArrayList<>(count);

        for (int i = 0; i < count; i++)
        {
            Built region = built.get(i);
            shellCells[i] = region.shell().size();
            interiorMinY[i] = region.interiorBounds().minY();
            interiorMaxY[i] = region.interiorBounds().maxY();

            for (int c = 0; c < region.interior().size(); c++)
            {
                owner[region.interior().get(c)] = (short) ((i + 1) | OWNER_ARRIVAL);
            }

            footprints.add(footprintOf(region));
            footprint[i] = footprints.get(i).cardinality();
        }

        // shell cells after every interior, so a room's air is never overwritten by a neighbour's
        // wall; a cell two shells share keeps whichever room came first, which only matters for
        // the separation flood below - and that one reads shared cells off the counts instead
        for (int i = 0; i < count; i++)
        {
            Built region = built.get(i);

            for (int c = 0; c < region.shell().size(); c++)
            {
                final int cell = region.shell().get(c);

                if (owner[cell] == OWNER_NONE)
                {
                    owner[cell] = (short) (i + 1);
                }
            }
        }

        countSharedShells(built, shared);

        for (int i = 0; i < count; i++)
        {
            for (int j = 0; j < count; j++)
            {
                if (i != j)
                {
                    overlap[i][j] = overlapOf(footprints.get(i), footprints.get(j));

                    if (shared[i][j] > 0)
                    {
                        separation[i][j] = 0;
                    }
                }
            }
        }

        if (reach > 0)
        {
            byte[] distance = new byte[this.flags.length];

            for (int i = 0; i < count; i++)
            {
                Built region = built.get(i);

                flood(i, region.interior(), true, owner, reach, distance, path[i]);
                flood(i, allCellsOf(region), false, owner, reach, distance, separation[i]);
            }

            // a flood from A to B and one from B to A walk the same cells in opposite directions
            // and so agree, but the reach cuts each off separately; take the shorter so the
            // answer is one answer whichever side asks
            for (int i = 0; i < count; i++)
            {
                for (int j = i + 1; j < count; j++)
                {
                    path[i][j] = path[j][i] = Math.min(path[i][j], path[j][i]);
                    separation[i][j] = separation[j][i] = Math.min(separation[i][j], separation[j][i]);
                }
            }
        }

        return new RegionAdjacency(reach, shellCells, shared, path, separation, footprint, overlap, interiorMinY, interiorMaxY);
    }

    private static int[][] filled(int count, int value)
    {
        int[][] matrix = new int[count][count];

        for (int[] row : matrix)
        {
            Arrays.fill(row, value);
        }

        return matrix;
    }

    private static IntStack allCellsOf(Built region)
    {
        if (region.shell().isEmpty())
        {
            return region.interior();
        }

        IntStack all = new IntStack();

        for (int c = 0; c < region.interior().size(); c++)
        {
            all.push(region.interior().get(c));
        }

        for (int c = 0; c < region.shell().size(); c++)
        {
            all.push(region.shell().get(c));
        }

        return all;
    }

    /**
     * Pairwise counts of shell cells in common. A cell is in at most six shells, so the lists
     * here are short and only exist for cells more than one room touches.
     */
    private void countSharedShells(List<Built> built, int[][] shared)
    {
        Map<Integer, int[]> firstOwner = new HashMap<>();
        Map<Integer, List<Integer>> multiOwner = new HashMap<>();

        for (int i = 0; i < built.size(); i++)
        {
            IntStack shell = built.get(i).shell();

            for (int c = 0; c < shell.size(); c++)
            {
                final int cell = shell.get(c);
                int[] first = firstOwner.get(cell);

                if (first == null)
                {
                    firstOwner.put(cell, new int[]{i});
                    continue;
                }

                List<Integer> owners = multiOwner.get(cell);

                if (owners == null)
                {
                    owners = new ArrayList<>(3);
                    owners.add(first[0]);
                    multiOwner.put(cell, owners);
                }

                for (int other : owners)
                {
                    shared[other][i]++;
                    shared[i][other]++;
                }

                owners.add(i);
            }
        }
    }

    /**
     * The columns a region stands in, holes filled: a rail loop's infield is inside the loop
     * whether or not the loop took it in, and a shrine standing there is within the track.
     */
    private BitSet footprintOf(Built region)
    {
        BitSet columns = new BitSet(this.sizeX * this.sizeZ);
        IntStack cells = allCellsOf(region);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int c = 0; c < cells.size(); c++)
        {
            final int cell = cells.get(c);
            final int x = xOf(cell) - this.bounds.minX();
            final int z = zOf(cell) - this.bounds.minZ();

            columns.set(x * this.sizeZ + z);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        // flood the empty columns from the edge of the region's own box; whatever is left is
        // closed around in plan and belongs to the footprint
        BitSet outside = new BitSet(this.sizeX * this.sizeZ);
        IntStack stack = new IntStack();

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                final boolean onEdge = x == minX || x == maxX || z == minZ || z == maxZ;
                final int column = x * this.sizeZ + z;

                if (onEdge && !columns.get(column) && !outside.get(column))
                {
                    outside.set(column);
                    stack.push(column);
                }
            }
        }

        while (!stack.isEmpty())
        {
            final int column = stack.pop();
            final int x = column / this.sizeZ;
            final int z = column % this.sizeZ;

            for (int[] offset : NEIGHBOURS_IN_PLANE)
            {
                final int nx = x + offset[0];
                final int nz = z + offset[1];

                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ)
                {
                    continue;
                }

                final int neighbour = nx * this.sizeZ + nz;

                if (columns.get(neighbour) || outside.get(neighbour))
                {
                    continue;
                }

                outside.set(neighbour);
                stack.push(neighbour);
            }
        }

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                final int column = x * this.sizeZ + z;

                if (!outside.get(column))
                {
                    columns.set(column);
                }
            }
        }

        return columns;
    }

    private static int overlapOf(BitSet a, BitSet b)
    {
        BitSet both = (BitSet) a.clone();
        both.and(b);
        return both.cardinality();
    }

    /**
     * One bounded breadth-first flood out from a region, recording the first arrival at every
     * other region.
     *
     * @param walkable {@code true} to cross what a player could walk through - air, anything
     *                 passable, and a door, trapdoor or fence gate - and to arrive only at another
     *                 region's air; {@code false} to cross anything that is not a full block (the
     *                 open-air cluster's own notion of reach) and to arrive at any cell of another
     *                 region, wall included
     * @param distance scratch, one byte per cell, zero on entry and left zero
     * @param out      cells strictly between this region and each other one, written only where
     *                 an arrival improves on what is there
     */
    private void flood(int self, IntStack sources, boolean walkable, short[] owner, int reach, byte[] distance, int[] out)
    {
        IntStack touched = new IntStack();
        IntStack layer = new IntStack();

        for (int c = 0; c < sources.size(); c++)
        {
            final int cell = sources.get(c);

            if (distance[cell] != 0)
            {
                continue;
            }

            distance[cell] = 1;
            touched.push(cell);
            layer.push(cell);

            // a cell this region shares with another is an arrival at no distance at all
            final int other = ownerOf(owner[cell]);

            if (other != OWNER_NONE && other - 1 != self && arrives(owner[cell], walkable))
            {
                out[other - 1] = 0;
            }
        }

        // layer d holds cells d steps out; an arrival there has d - 1 cells between, so the
        // flood runs until arrivals would have more than reach cells between
        for (int step = 1; step <= reach + 1 && !layer.isEmpty(); step++)
        {
            IntStack next = new IntStack();

            for (int c = 0; c < layer.size(); c++)
            {
                final int cell = layer.get(c);
                final int x = xOf(cell);
                final int y = yOf(cell);
                final int z = zOf(cell);

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

                    if (distance[neighbour] != 0)
                    {
                        continue;
                    }

                    final int other = ownerOf(owner[neighbour]);
                    final boolean foreign = other != OWNER_NONE && other - 1 != self;

                    if (foreign && arrives(owner[neighbour], walkable))
                    {
                        final int between = step - 1;

                        if (between < out[other - 1])
                        {
                            out[other - 1] = between;
                        }
                    }

                    if (!crosses(nx, ny, nz, walkable))
                    {
                        continue;
                    }

                    distance[neighbour] = (byte) Math.min(127, step + 1);
                    touched.push(neighbour);
                    next.push(neighbour);
                }
            }

            layer = next;
        }

        for (int c = 0; c < touched.size(); c++)
        {
            distance[touched.get(c)] = 0;
        }
    }

    private static int ownerOf(short entry)
    {
        return entry & ~OWNER_ARRIVAL & 0xFFFF;
    }

    private static boolean arrives(short entry, boolean walkable)
    {
        return !walkable || (entry & OWNER_ARRIVAL) != 0;
    }

    /**
     * Whether a flood may step into this cell. Walking crosses air, anything passable, and a
     * door - see {@link RegionAdjacency} for why doors are crossed here and nowhere else in this
     * class; crossing (the open-air reach) is stopped only by a full block.
     */
    private boolean crosses(int x, int y, int z, boolean walkable)
    {
        final Passability passability = this.volume.passabilityAt(x, y, z);

        if (!walkable)
        {
            return !passability.isFullBlock();
        }

        if (!passability.stopsFill())
        {
            return true;
        }

        BlockSignature signature = this.volume.signatureAt(x, y, z);

        return signature != null
                && (signature.hasTag("minecraft:doors")
                || signature.hasTag("minecraft:trapdoors")
                || signature.hasTag("minecraft:fence_gates"));
    }

    /**
     * Fold each region's relationships into its identity hash - #142. Every region's own hash is
     * read first, then each region's digest is built from those, so no hash ever depends on a
     * neighbour's folded hash and the result cannot depend on the order regions were found in.
     * The digest is a sum over neighbours, which commutes; the values folded are integers from a
     * deterministic flood, so an unchanged soulhome folds the same numbers twice without any need
     * to quantise them.
     */
    private static List<SoulRegion> foldRelationships(List<Built> built, RegionAdjacency adjacency)
    {
        final int count = built.size();
        long[] own = new long[count];

        for (int i = 0; i < count; i++)
        {
            own[i] = built.get(i).region().identityHash();
        }

        List<SoulRegion> regions = new ArrayList<>(count);

        for (int i = 0; i < count; i++)
        {
            long digest = 0L;

            for (int j = 0; j < count; j++)
            {
                if (j == i || !adjacency.related(i, j))
                {
                    continue;
                }

                long term = own[j];
                term = term * 31 + adjacency.sharedShellCells(i, j);
                term = term * 31 + adjacency.pathLength(i, j);
                term = term * 31 + adjacency.separation(i, j);
                term = term * 31 + adjacency.footprintOverlap(i, j);
                term = term * 31 + (adjacency.isAbove(i, j) ? 1 : adjacency.isAbove(j, i) ? 2 : 0);

                // stirred so that two different pairs summing to the same total is no more likely
                // than any other collision
                term ^= term >>> 29;
                term *= 0xBF58476D1CE4E5B9L;
                term ^= term >>> 32;

                digest += term;
            }

            regions.add(digest == 0L ? built.get(i).region() : built.get(i).region().withRelationships(digest));
        }

        return regions;
    }

    // endregion

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
