/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:irregularity}: was this hollow carved, or was it a box someone pasted sculk into?
 * See #96, and #37, which is where this class of measurement belongs.
 *
 * <p><b>This is the first form in the mod that rewards disorder</b>, which is worth saying plainly
 * rather than slipping in as one more clause. {@code loop}, {@code platform}, {@code enclosure} and
 * {@code line} all reward order - a closed circuit, a flat plane, a sealed shell, a straight run.
 * This one is the inverse of a symmetry check: the same question read backwards.
 *
 * <p>A cell is <b>regular</b> when it lies on a straight axis-aligned run of at least
 * {@code run_length} cells of the same element - a flat stretch of wall, floor or ceiling.
 * Confidence is {@code 1 - regular/total}, so a wandering hand-carved hollow scores high and a
 * clean 5x5x5 room scores near zero.
 *
 * <p><b>The tuning caution matters more than the formula.</b> Minecraft's grid makes right angles
 * the default rather than a lazy choice - most players build boxes because the game nudges them to.
 * This has to read as a bonus for going out of your way to carve something organic, never as a tax
 * for building normally. {@code structural_share_cap} already bounds all structural credit against
 * the block-signal score, which softens it; a form using this clause should still keep its weight
 * modest, and #25's rule 1 means a perfectly square grotto still classifies and still scores on its
 * signals.
 */
public final class IrregularityClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:irregularity";
    }

    @Override
    public Kind kind()
    {
        return Kind.SHAPE;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("run_length", ClauseParamSpec.Type.INT, 4));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new IrregularityClause(params.getElement("of"), params.getInt("run_length"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        IrregularityClause irregularity = (IrregularityClause) clause;
        return Map.of("of", irregularity.of(), "run_length", irregularity.runLength());
    }
}

record IrregularityClause(String of, int runLength) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:irregularity";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher matcher = elements.get(this.of);

        if (matcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        List<RegionGeometry.Cell> cells = geometry.cellsMatching(matcher);

        if (cells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        Set<Point> occupied = new HashSet<>();

        for (RegionGeometry.Cell cell : cells)
        {
            occupied.add(new Point(cell.x(), cell.y(), cell.z()));
        }

        int regular = 0;

        for (Point point : occupied)
        {
            if (isOnAStraightRun(point, occupied))
            {
                regular++;
            }
        }

        final double confidence = ClauseMath.clamp01(1d - ((double) regular / occupied.size()));
        return FormResult.of(confidence, diagnostic(regular, occupied.size()));
    }

    /**
     * Whether this cell lies anywhere on a straight run of at least {@link #runLength} along any of
     * the three axes - measured through the cell in both directions, not from it, so the middle of
     * a long wall counts as regular just as much as its end does.
     */
    private boolean isOnAStraightRun(Point point, Set<Point> occupied)
    {
        return runThrough(point, occupied, 1, 0, 0) >= this.runLength
                || runThrough(point, occupied, 0, 1, 0) >= this.runLength
                || runThrough(point, occupied, 0, 0, 1) >= this.runLength;
    }

    private int runThrough(Point point, Set<Point> occupied, int dx, int dy, int dz)
    {
        int length = 1;

        for (int step = 1; occupied.contains(point.offset(dx * step, dy * step, dz * step)); step++)
        {
            length++;
        }

        for (int step = 1; occupied.contains(point.offset(-dx * step, -dy * step, -dz * step)); step++)
        {
            length++;
        }

        return length;
    }

    private String diagnostic(int regular, int total)
    {
        if (regular == 0)
        {
            return "";
        }

        if (regular >= total)
        {
            return "'" + this.of + "' is built entirely of straight runs - this reads as a room, not as a hollow";
        }

        return regular + " of " + total + " '" + this.of + "' sit on flat runs of "
                + this.runLength + " or more";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' is carved rather than squared off";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.runLength < 2)
        {
            errors.add("'run_length' must be at least 2 - a run of one is every cell, got " + this.runLength);
        }

        return errors;
    }

    private record Point(int x, int y, int z)
    {
        Point offset(int dx, int dy, int dz)
        {
            return new Point(this.x + dx, this.y + dy, this.z + dz);
        }
    }
}
