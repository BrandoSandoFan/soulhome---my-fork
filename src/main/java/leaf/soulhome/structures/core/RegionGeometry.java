/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static final RegionGeometry EMPTY = new RegionGeometry(List.of(), false);

    private final List<Cell> cells;
    private final boolean truncated;

    /**
     * Lazily populated per matcher. Not part of this object's identity - it is purely a memoized
     * derivative of {@link #cells}, rebuilt for free from it if ever needed.
     */
    private final Map<BlockMatcher, List<Cell>> matchCache = new HashMap<>();

    private RegionGeometry(List<Cell> cells, boolean truncated)
    {
        this.cells = cells;
        this.truncated = truncated;
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

    /** One indexed block: where it is, and what it is. */
    public record Cell(int x, int y, int z, BlockSignature signature)
    {
    }

    public static final class Builder
    {
        private final List<Cell> cells = new ArrayList<>();
        private final int maxCells;
        private boolean truncated;

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

        public RegionGeometry build()
        {
            return this.cells.isEmpty() && !this.truncated
                    ? EMPTY
                    : new RegionGeometry(List.copyOf(this.cells), this.truncated);
        }
    }
}
