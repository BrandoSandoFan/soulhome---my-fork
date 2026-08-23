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
 * {@code surrounds}: does {@code of} ring {@code to} - chairs around a hearth's fire, not bunched
 * to one side of it. Graded as angular coverage around {@code to}'s own cluster centre rather than
 * as a count or a distance, so a ring and a square of the same seats score the same and a lonely
 * campfire in the corner cannot drag down the real hearth's score. See #30.
 */
public final class SurroundsClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "surrounds";
    }

    @Override
    public Kind kind()
    {
        return Kind.RELATION;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return AngularCoverage.params();
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return AngularCoverage.create(params);
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        return AngularCoverage.encode(clause);
    }
}

/**
 * @param minRadius       horizontal distance from a {@code to} cluster's centre below which an
 *                         {@code of} cell is "pressed against it" rather than surrounding it
 * @param maxRadius        horizontal distance past which an {@code of} cell is too far away to count
 * @param maxHeightDelta   vertical distance from the cluster centre an {@code of} cell may sit at
 * @param sectors          how many equal angular slices the circle around a cluster is divided into
 * @param minSectors       distinct slices that must be touched before this starts counting as
 *                         "surrounding" at all - below this, {@link #confidence} floors to 0
 */
record AngularCoverage(String of, String to, int minRadius, int maxRadius, int maxHeightDelta, int sectors, int minSectors)
        implements FormClause
{
    static List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("min_radius", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("max_radius", ClauseParamSpec.Type.INT, 5),
                ClauseParamSpec.optional("max_height_delta", ClauseParamSpec.Type.INT, 2),
                ClauseParamSpec.optional("sectors", ClauseParamSpec.Type.INT, 8),
                ClauseParamSpec.optional("min_sectors", ClauseParamSpec.Type.INT, 3));
    }

    static FormClause create(ClauseParams params)
    {
        return new AngularCoverage(
                params.getElement("of"), params.getElement("to"),
                params.getInt("min_radius"), params.getInt("max_radius"), params.getInt("max_height_delta"),
                params.getInt("sectors"), params.getInt("min_sectors"));
    }

    static Map<String, Object> encode(FormClause clause)
    {
        AngularCoverage coverage = (AngularCoverage) clause;
        return Map.of(
                "of", coverage.of(), "to", coverage.to(),
                "min_radius", coverage.minRadius(), "max_radius", coverage.maxRadius(),
                "max_height_delta", coverage.maxHeightDelta(),
                "sectors", coverage.sectors(), "min_sectors", coverage.minSectors());
    }

    @Override
    public String typeId()
    {
        return "surrounds";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher ofMatcher = elements.get(this.of);
        BlockMatcher toMatcher = elements.get(this.to);

        if (ofMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        if (toMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.to));
        }

        List<RegionGeometry.Cell> ofCells = geometry.cellsMatching(ofMatcher);
        List<RegionGeometry.Cell> toCells = geometry.cellsMatching(toMatcher);

        if (ofCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        if (toCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.to));
        }

        double bestConfidence = 0d;
        int bestOccupied = 0;
        boolean bestMostlyTooClose = false;

        for (List<RegionGeometry.Cell> cluster : CellGraphs.components(toCells, CellGraphs.chebyshevOffsets(2)))
        {
            CellGraphs.Centroid centre = CellGraphs.centroidOf(cluster);

            Set<Integer> occupied = new HashSet<>();
            int tooClose = 0;
            int eligible = 0;

            for (RegionGeometry.Cell of : ofCells)
            {
                double dx = of.x() - centre.x();
                double dz = of.z() - centre.z();
                double horizontal = Math.sqrt(dx * dx + dz * dz);

                if (Math.abs(of.y() - centre.y()) > this.maxHeightDelta)
                {
                    continue;
                }

                if (horizontal < this.minRadius)
                {
                    tooClose++;
                    continue;
                }

                if (horizontal > this.maxRadius)
                {
                    continue;
                }

                eligible++;
                double angle = Math.atan2(dz, dx);

                if (angle < 0)
                {
                    angle += 2 * Math.PI;
                }

                int sector = (int) (angle / (2 * Math.PI / this.sectors));
                occupied.add(Math.min(sector, this.sectors - 1));
            }

            double denominator = this.sectors - this.minSectors + 1;
            double confidence = denominator <= 0
                    ? (occupied.size() >= this.minSectors ? 1d : 0d)
                    : ClauseMath.clamp01((occupied.size() - this.minSectors + 1) / denominator);

            if (confidence > bestConfidence || (confidence == bestConfidence && occupied.size() > bestOccupied))
            {
                bestConfidence = confidence;
                bestOccupied = occupied.size();
                bestMostlyTooClose = eligible == 0 && tooClose > 0;
            }
        }

        return FormResult.of(bestConfidence, diagnostic(bestConfidence, bestOccupied, bestMostlyTooClose));
    }

    private String diagnostic(double confidence, int occupiedSectors, boolean mostlyTooClose)
    {
        if (confidence >= 1d)
        {
            return "";
        }

        if (mostlyTooClose)
        {
            return "the '" + this.of + "' ring '" + this.to + "', but are pressed right against it";
        }

        return "'" + this.of + "' covers " + occupiedSectors + " of " + this.sectors + " directions around '" + this.to + "'";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' surrounds '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.minRadius > this.maxRadius)
        {
            errors.add("'min_radius' (" + this.minRadius + ") is greater than 'max_radius' ("
                    + this.maxRadius + "), so this relation can never be satisfied");
        }

        if (this.sectors < 1)
        {
            errors.add("'sectors' must be at least 1, got " + this.sectors);
        }

        return errors;
    }
}
