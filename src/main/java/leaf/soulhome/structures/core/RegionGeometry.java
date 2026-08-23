/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Positions of the structurally-interesting blocks in one region, so a form (see the structural
 * considerations epic) can ask not just <i>how many</i> of a thing a room holds but <i>where</i>.
 *
 * <p>{@link RegionScanner} otherwise keeps only a {@link BlockCounts} multiset - the right choice
 * for the classifier's block-signal scoring, and useless for anything that has to reason about
 * arrangement. This is the positional sibling: a packed list of cells, in the scanner's own
 * deterministic walk order, each paired with the {@link BlockSignature} standing there.
 *
 * <p><b>Only what a form will ask about is indexed.</b> A caller filters what reaches
 * {@link Builder#add}; a room full of stone that nothing structural cares about indexes nothing,
 * and the memory cost stays proportional to what is actually being asked, not to region volume.
 *
 * <p>Minecraft-free, like the rest of {@code core}, so arrangement logic stays unit-testable
 * against a synthetic {@link GridVolume} rather than a running server.
 */
public final class RegionGeometry
{
    public static final RegionGeometry EMPTY = new RegionGeometry(List.of(), false, Set.of(), null);

    private final List<Cell> cells;
    private final boolean truncated;

    /**
     * Positions the scanner found to be {@link Passability#BLOCKING}, for the {@code across}
     * relation's {@code require_clear} check (#29) - "is the straight line between these two cells
     * open, or does a wall run through it". Empty unless a caller actually needed clearance data;
     * see {@link RegionScanner}'s {@code indexClearance} flag. Deliberately a separate index from
     * {@link #cells} rather than folded into it: a blocked cell is not itself a structurally
     * interesting element, so it should never show up in {@link #cellsMatching}.
     */
    private final Set<Blocked> blocked;

    /**
     * The region's own extent, when the caller building this geometry had one to give - the
     * enclosed room's bounds, or the open-air cluster's. {@code null} for a hand-built geometry
     * that never set one, which every shape/relation clause has to tolerate: {@code enclosure}'s
     * "no {@code around}" case and {@code inside}'s flood-fill both fall back to a bounding box
     * derived from their own elements when this is absent, rather than failing outright.
     */
    private final RegionBounds bounds;

    /**
     * Lazily populated per matcher. Not part of this object's identity - it is purely a memoized
     * derivative of {@link #cells}, rebuilt for free from it if ever needed.
     */
    private final Map<BlockMatcher, List<Cell>> matchCache = new HashMap<>();

    private RegionGeometry(List<Cell> cells, boolean truncated, Set<Blocked> blocked, RegionBounds bounds)
    {
        this.cells = cells;
        this.truncated = truncated;
        this.blocked = blocked;
        this.bounds = bounds;
    }

    public static Builder builder(int maxCells)
    {
        return new Builder(maxCells);
    }

    /** Every indexed cell, in the scanner's deterministic walk order. */
    public List<Cell> cells()
    {
        return this.cells;
    }

    public int size()
    {
        return this.cells.size();
    }

    public boolean isEmpty()
    {
        return this.cells.isEmpty();
    }

    /**
     * Whether the index hit its cap and stopped short of the whole region. A form evaluated against
     * a truncated index can score low for a reason that has nothing to do with the build, so this
     * has to survive as far as the player-facing report rather than being silently absorbed here.
     */
    public boolean isTruncated()
    {
        return this.truncated;
    }

    /**
     * Every indexed cell whose signature matches, in the geometry's own order. Memoized per
     * matcher for the life of this geometry: the same matcher - "soulhome:seating", say - is
     * commonly asked for by more than one archetype's forms within a single scan, and re-filtering
     * the whole index each time would be wasted work.
     */
    public List<Cell> cellsMatching(BlockMatcher matcher)
    {
        return this.matchCache.computeIfAbsent(matcher, this::computeMatches);
    }

    private List<Cell> computeMatches(BlockMatcher matcher)
    {
        List<Cell> matched = new ArrayList<>();

        for (Cell cell : this.cells)
        {
            if (matcher.test(cell.signature()))
            {
                matched.add(cell);
            }
        }

        return List.copyOf(matched);
    }

    /**
     * Whether the scanner found a wall, a piece of furniture, or anything else that blocks the
     * fill standing at this position. Only meaningful when {@link #blocked} was actually populated
     * - see {@link RegionScanner}'s {@code indexClearance} flag - so an absence here means "no
     * clearance data was tracked", not "definitely open", and every caller (in practice, just the
     * {@code across} relation) has to be written to treat the two the same: a require-clear check
     * that nobody asked to pay for should not start failing regions it never used to.
     */
    public boolean isBlocked(int x, int y, int z)
    {
        return this.blocked.contains(new Blocked(x, y, z));
    }

    /** This region's own extent, if the caller building this geometry supplied one. */
    public Optional<RegionBounds> bounds()
    {
        return Optional.ofNullable(this.bounds);
    }

    /** One indexed block: where it is, and what it is. */
    public record Cell(int x, int y, int z, BlockSignature signature)
    {
    }

    /** A blocked position, with no signature - {@link #isBlocked} only ever needs "is it solid". */
    private record Blocked(int x, int y, int z)
    {
    }

    public static final class Builder
    {
        private final List<Cell> cells = new ArrayList<>();
        private final Set<Blocked> blocked = new HashSet<>();
        private final int maxCells;
        private boolean truncated;
        private RegionBounds bounds;

        private Builder(int maxCells)
        {
            this.maxCells = maxCells;
        }

        /**
         * Records one cell. The caller decides what is worth indexing in the first place - this
         * only enforces the cap once it has been asked to keep a cell.
         */
        public Builder add(int x, int y, int z, BlockSignature signature)
        {
            if (signature == null)
            {
                return this;
            }

            if (this.cells.size() >= this.maxCells)
            {
                this.truncated = true;
                return this;
            }

            this.cells.add(new Cell(x, y, z, signature));
            return this;
        }

        /**
         * Records one blocked position, for {@link #isBlocked}. Uncapped, deliberately: it is only
         * ever populated at all when a loaded form's {@code across} relation needs it (see
         * {@code ArchetypeSignals#needsClearance}), and even then only for the cells the scanner was
         * already visiting to build the shell - see {@link RegionScanner}.
         */
        public Builder addBlocked(int x, int y, int z)
        {
            this.blocked.add(new Blocked(x, y, z));
            return this;
        }

        /** This region's own extent - see {@link RegionGeometry#bounds()}. */
        public Builder bounds(RegionBounds bounds)
        {
            this.bounds = bounds;
            return this;
        }

        public RegionGeometry build()
        {
            return this.cells.isEmpty() && !this.truncated && this.blocked.isEmpty() && this.bounds == null
                    ? EMPTY
                    : new RegionGeometry(List.copyOf(this.cells), this.truncated, Set.copyOf(this.blocked), this.bounds);
        }
    }
}
