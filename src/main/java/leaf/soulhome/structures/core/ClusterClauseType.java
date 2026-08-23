/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:cluster}: is the element one deliberate mass, or scattered leftovers - eight
 * barrels stacked together read as storage, eight barrels spread one to a corner read as clutter
 * nobody meant. Graded as the largest connected group's share of the whole element: a single tight
 * component scores 1.0, and a set with no two cells touching scores {@code 1/n}. See #32.
 */
public final class ClusterClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:cluster";
    }

    @Override
    public Kind kind()
    {
        return Kind.SHAPE;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new ClusterClause(params.getElement("of"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        return Map.of("of", ((ClusterClause) clause).of());
    }
}

record ClusterClause(String of) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:cluster";
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

        List<RegionGeometry.Cell> largest = CellGraphs.largestComponent(cells, CellGraphs.OFFSETS_26);
        double confidence = (double) largest.size() / cells.size();

        String diagnostic = confidence >= 1d
                ? ""
                : largest.size() + " of " + cells.size() + " '" + this.of + "' are in one group - the rest are scattered";

        return FormResult.of(confidence, diagnostic);
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' forms one cluster";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return List.of();
    }
}
