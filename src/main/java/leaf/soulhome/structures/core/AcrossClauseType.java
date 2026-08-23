/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code across}: is {@code of} at range from {@code to}, lined up on an axis, with clear space
 * between - two chairs facing each other over a table, rails passing on either side of a lever.
 * See the relations table in #29.
 *
 * <p>Alignment is graded, not gated: a candidate up to one cell off the primary axis still counts,
 * at half credit, rather than failing outright - the issue's own worked example is a chair one
 * cell off-axis still scoring well. {@code require_clear} additionally demands the straight line
 * between the two cells cross no {@link RegionGeometry#isBlocked} cell - see
 * {@link ClauseMath#isPathClear} - which is why this is the one relation that reports
 * {@link #needsClearance()}.
 */
public final class AcrossClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "across";
    }

    @Override
    public Kind kind()
    {
        return Kind.RELATION;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("min_distance", ClauseParamSpec.Type.INT, 2),
                ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 4),
                ClauseParamSpec.optional("axes", ClauseParamSpec.Type.STRING, "x,z"),
                // no BOOLEAN clause-param type exists (see ClauseParamSpec.Type) - 0/1 stands in
                ClauseParamSpec.optional("require_clear", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("coverage", ClauseParamSpec.Type.DOUBLE, 1.0));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new AcrossClause(
                params.getElement("of"), params.getElement("to"),
                params.getInt("min_distance"), params.getInt("max_distance"), params.getString("axes"),
                params.getInt("require_clear") != 0, params.getDouble("coverage"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        AcrossClause across = (AcrossClause) clause;
        return Map.of(
                "of", across.of(), "to", across.to(),
                "min_distance", across.minDistance(), "max_distance", across.maxDistance(),
                "axes", across.axes(), "require_clear", across.requireClear() ? 1 : 0,
                "coverage", across.coverage());
    }
}

record AcrossClause(
        String of, String to, int minDistance, int maxDistance, String axes, boolean requireClear, double coverage)
        implements FormClause
{
    /** How much credit a candidate one cell off the primary axis earns, against 1.0 for exact. */
    private static final double OFF_AXIS_CREDIT = 0.5;

    /** Perpendicular offset past which a candidate is not "across", however good its distance is. */
    private static final int MAX_PERPENDICULAR_OFFSET = 1;

    @Override
    public String typeId()
    {
        return "across";
    }

    @Override
    public boolean needsClearance()
    {
        return this.requireClear;
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

        List<String> axisList = parsedAxes();

        int fullyAligned = 0;
        int partiallyAligned = 0;
        int tooClose = 0;
        int blocked = 0;
        int none = 0;
        double scoreTotal = 0d;

        for (RegionGeometry.Cell of : ofCells)
        {
            Candidate best = bestCandidate(of, toCells, axisList, geometry);
            scoreTotal += best.score;

            if (best.score >= 1d)
            {
                fullyAligned++;
            }
            else if (best.score > 0d)
            {
                partiallyAligned++;
            }
            else if (best.sawTooClose)
            {
                tooClose++;
            }
            else if (best.sawBlocked)
            {
                blocked++;
            }
            else
            {
                none++;
            }
        }

        final int total = ofCells.size();
        final int satisfied = fullyAligned + partiallyAligned;
        double confidence = ClauseMath.coverageConfidence(scoreTotal / total, this.coverage);

        return FormResult.of(confidence, diagnostic(satisfied, total, tooClose, blocked, none));
    }

    private Candidate bestCandidate(
            RegionGeometry.Cell of, List<RegionGeometry.Cell> toCells, List<String> axisList, RegionGeometry geometry)
    {
        double bestScore = 0d;
        boolean sawTooClose = false;
        boolean sawBlocked = false;

        for (RegionGeometry.Cell to : toCells)
        {
            for (String axis : axisList)
            {
                final int primaryDelta = "x".equals(axis) ? Math.abs(of.x() - to.x()) : Math.abs(of.z() - to.z());
                final int perpDelta = "x".equals(axis) ? Math.abs(of.z() - to.z()) : Math.abs(of.x() - to.x());

                if (perpDelta > MAX_PERPENDICULAR_OFFSET)
                {
                    continue;
                }

                if (primaryDelta < this.minDistance)
                {
                    sawTooClose = true;
                    continue;
                }

                if (primaryDelta > this.maxDistance)
                {
                    continue;
                }

                if (this.requireClear && !ClauseMath.isPathClear(of, to, geometry))
                {
                    sawBlocked = true;
                    continue;
                }

                double score = perpDelta == 0 ? 1.0 : OFF_AXIS_CREDIT;
                bestScore = Math.max(bestScore, score);
            }
        }

        return new Candidate(bestScore, sawTooClose, sawBlocked);
    }

    private List<String> parsedAxes()
    {
        List<String> result = new ArrayList<>();

        for (String part : this.axes.split(","))
        {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);

            if (trimmed.equals("x") || trimmed.equals("z"))
            {
                result.add(trimmed);
            }
        }

        return result.isEmpty() ? List.of("x", "z") : List.copyOf(result);
    }

    private String diagnostic(int satisfied, int total, int tooClose, int blocked, int none)
    {
        if (satisfied == total)
        {
            return "";
        }

        final String failing = satisfied == 0
                ? "no"
                : satisfied + " of " + total;

        if (blocked >= tooClose && blocked >= none)
        {
            return failing + " '" + this.of + "' face '" + this.to
                    + "' across clear space - something stands in the way";
        }

        if (tooClose >= none)
        {
            return failing + " '" + this.of + "' face '" + this.to
                    + "' across clear space - the others are against it";
        }

        return failing + " '" + this.of + "' face '" + this.to + "' across clear space";
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' is across from '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        return this.minDistance > this.maxDistance
                ? List.of("'min_distance' (" + this.minDistance + ") is greater than 'max_distance' ("
                        + this.maxDistance + "), so this relation can never be satisfied")
                : List.of();
    }

    private record Candidate(double score, boolean sawTooClose, boolean sawBlocked)
    {
    }
}
