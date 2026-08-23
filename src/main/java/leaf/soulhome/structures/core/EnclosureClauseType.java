/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:enclosure}: is the element walled in, or does it just sit near some fencing -
 * a ring of fence around the room's own footprint (or, with {@code around}, around another
 * element's footprint) reads as an enclosure; a fence line that touches only part of the edge does
 * not. Graded as the fraction of the target footprint's perimeter cells with an {@code of} cell
 * coincident with or adjacent to them, which is deliberately what lets a gate-sized single-cell gap
 * through without tanking the score - a perfect ring and a ring with one gate differ by one
 * perimeter cell out of many, not by "enclosed" versus "not". See #32.
 */
public final class EnclosureClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:enclosure";
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
                // no element named - not "an element named the empty string" - is what "around
                // absent" means; the parser only adds a param to a form's referencedElements when
                // the datapack actually supplied it (see FormCodecs), so an omitted "around" never
                // demands a declared element the way a real one would
                ClauseParamSpec.optional("around", ClauseParamSpec.Type.ELEMENT, ""),
                ClauseParamSpec.optional("min_coverage", ClauseParamSpec.Type.DOUBLE, 0.5));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new EnclosureClause(
                params.getElement("of"), params.getElement("around"), params.getDouble("min_coverage"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        EnclosureClause enclosure = (EnclosureClause) clause;
        return Map.of(
                "of", enclosure.of(), "around", enclosure.around(), "min_coverage", enclosure.minCoverage());
    }
}

record EnclosureClause(String of, String around, double minCoverage) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:enclosure";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher ofMatcher = elements.get(this.of);

        if (ofMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        List<RegionGeometry.Cell> ofCells = geometry.cellsMatching(ofMatcher);

        if (ofCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        Set<XZ> ofFootprint = new HashSet<>();

        for (RegionGeometry.Cell cell : ofCells)
        {
            ofFootprint.add(new XZ(cell.x(), cell.z()));
        }

        String targetName = this.around.isEmpty() ? "the room" : this.around;
        List<XZ> perimeter = perimeterOf(geometry, elements);

        if (perimeter == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.around));
        }

        if (perimeter.isEmpty())
        {
            return FormResult.of(0d, "");
        }

        int touched = 0;

        for (XZ point : perimeter)
        {
            if (isTouched(point, ofFootprint))
            {
                touched++;
            }
        }

        double fraction = (double) touched / perimeter.size();
        double confidence = fraction < this.minCoverage ? 0d : fraction;

        return FormResult.of(confidence, diagnostic(confidence, fraction, touched, perimeter.size(), targetName));
    }

    /** {@code null} means {@code around} was named but does not resolve to any cell. */
    private List<XZ> perimeterOf(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        if (!this.around.isEmpty())
        {
            BlockMatcher aroundMatcher = elements.get(this.around);

            if (aroundMatcher == null)
            {
                return null;
            }

            List<RegionGeometry.Cell> aroundCells = geometry.cellsMatching(aroundMatcher);

            if (aroundCells.isEmpty())
            {
                return null;
            }

            return footprintBoundary(aroundCells);
        }

        RegionBounds bounds = geometry.bounds().orElse(null);

        if (bounds == null)
        {
            return List.of();
        }

        return boundsRing(bounds);
    }

    /** The cells of an arbitrary XZ footprint that have at least one 4-neighbour outside it. */
    private static List<XZ> footprintBoundary(List<RegionGeometry.Cell> cells)
    {
        Set<XZ> footprint = new HashSet<>();

        for (RegionGeometry.Cell cell : cells)
        {
            footprint.add(new XZ(cell.x(), cell.z()));
        }

        List<XZ> boundary = new ArrayList<>();

        for (XZ point : footprint)
        {
            if (!footprint.contains(new XZ(point.x() + 1, point.z()))
                    || !footprint.contains(new XZ(point.x() - 1, point.z()))
                    || !footprint.contains(new XZ(point.x(), point.z() + 1))
                    || !footprint.contains(new XZ(point.x(), point.z() - 1)))
            {
                boundary.add(point);
            }
        }

        return boundary;
    }

    /** The edge ring of a rectangle - computed directly, without filling its interior. */
    private static List<XZ> boundsRing(RegionBounds bounds)
    {
        List<XZ> ring = new ArrayList<>();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
        {
            ring.add(new XZ(x, bounds.minZ()));

            if (bounds.maxZ() != bounds.minZ())
            {
                ring.add(new XZ(x, bounds.maxZ()));
            }
        }

        for (int z = bounds.minZ() + 1; z < bounds.maxZ(); z++)
        {
            ring.add(new XZ(bounds.minX(), z));

            if (bounds.maxX() != bounds.minX())
            {
                ring.add(new XZ(bounds.maxX(), z));
            }
        }

        return ring;
    }

    private static boolean isTouched(XZ point, Set<XZ> ofFootprint)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (ofFootprint.contains(new XZ(point.x() + dx, point.z() + dz)))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private String diagnostic(double confidence, double fraction, int touched, int total, String targetName)
    {
        if (confidence >= 1d)
        {
            return "";
        }

        if (fraction < this.minCoverage)
        {
            return "'" + this.of + "' does not enclose " + targetName + " - only " + touched + " of " + total
                    + " perimeter cells are fenced";
        }

        return "'" + this.of + "' rings " + targetName + ", but " + (total - touched) + " of " + total
                + " perimeter cells still have a gap";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' encloses " + (this.around.isEmpty() ? "the room" : "'" + this.around + "'");
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.minCoverage <= 0d || this.minCoverage > 1d)
        {
            errors.add("'min_coverage' must be between 0 (exclusive) and 1, got " + this.minCoverage);
        }

        return errors;
    }

    private record XZ(int x, int z)
    {
    }
}
