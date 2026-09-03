/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:apex}: is the element at the <i>top</i> of the room - a lightning rod crowning a
 * mast, rather than one stuck at its foot beside it. See #94.
 *
 * <p>None of the clause types before this one could express "highest in the region". {@code above}
 * is the near miss and is a different question: it is local and comparative - "this head rests
 * within a block of that post" - and grades against another named element, never against the
 * region's own extent. So this is a single-element shape clause, in the same family as
 * {@code platform}, {@code enclosure}, {@code line} and {@code cluster}.
 *
 * <p><b>What it measures is the failure case.</b> On an open-air build the {@code of} element is
 * often the highest block by construction, because that is where people put a lightning rod - so a
 * correct build scoring 1.0 tells you little. What earns this clause its keep is that a rod at
 * ground level next to a tall mast scores near zero, which nothing else in the vocabulary noticed.
 * Paired with a {@code line} over the mast, the form then measures the spire as well as its tip.
 */
public final class ApexClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:apex";
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
                ClauseParamSpec.optional("tolerance", ClauseParamSpec.Type.INT, 1));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new ApexClause(params.getElement("of"), params.getInt("tolerance"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        ApexClause apex = (ApexClause) clause;
        return Map.of("of", apex.of(), "tolerance", apex.tolerance());
    }
}

record ApexClause(String of, int tolerance) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:apex";
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

        final int roofY = highestOccupiedLayer(geometry, cells);
        final int floor = roofY - Math.max(0, this.tolerance);

        int atApex = 0;

        for (RegionGeometry.Cell cell : cells)
        {
            if (cell.y() >= floor)
            {
                atApex++;
            }
        }

        final double confidence = ClauseMath.clamp01((double) atApex / cells.size());
        return FormResult.of(confidence, diagnostic(atApex, cells.size(), roofY));
    }

    /**
     * The top of the region, not the top of the element. Comparing the rod against the rod would
     * make every build a perfect apex - the whole question is where the element sits relative to
     * everything else.
     *
     * <p>{@link RegionGeometry#bounds()} carries the region's own extent when the scanner built the
     * geometry, which is the honest answer including the blocks nothing indexed. A hand-built
     * geometry with no bounds - every clause has to tolerate one - falls back to the highest
     * indexed cell instead.
     */
    private int highestOccupiedLayer(RegionGeometry geometry, List<RegionGeometry.Cell> ofCells)
    {
        if (geometry.bounds().isPresent())
        {
            return geometry.bounds().get().maxY();
        }

        int highest = Integer.MIN_VALUE;

        for (RegionGeometry.Cell cell : geometry.isEmpty() ? ofCells : geometry.cells())
        {
            highest = Math.max(highest, cell.y());
        }

        return highest;
    }

    private String diagnostic(int atApex, int total, int roofY)
    {
        if (atApex >= total)
        {
            return "";
        }

        if (atApex == 0)
        {
            return "no '" + this.of + "' is near the top of the structure (its highest layer is y=" + roofY + ")";
        }

        return atApex + " of " + total + " '" + this.of + "' crown the structure - the others sit below it";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' crowns the structure";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.tolerance < 0)
        {
            errors.add("'tolerance' must not be negative, got " + this.tolerance);
        }

        return errors;
    }
}
