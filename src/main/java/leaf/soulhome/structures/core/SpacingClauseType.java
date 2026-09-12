/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:spacing}: are these repeated elements placed at regular intervals - a colonnade,
 * not a pile of pillars? A mead hall is a hall partly because things in it repeat, and a tower reads
 * as built rather than stacked because its windows march up the shaft. See #169.
 *
 * <p>{@link PlatformClauseType} tells a laid floor from a heap and {@link LineClauseType} tells a
 * run from a scatter; neither can tell <b>regularly placed</b> from <b>clumped</b>, which is what
 * this asks.
 *
 * <h2>Groups first, and that is what defeats the clump</h2>
 *
 * The naive measure - variance of nearest-neighbour distances over the matched cells - scores a
 * solid 4x4 slab of bookshelves a flawless 1.0, because every cell in a clump has a neighbour
 * exactly one away and the variance of a constant is zero. That is the precise opposite of the
 * intent, and #98's checkerboard is the cautionary tale for trusting a per-cell measure of
 * arrangement.
 *
 * <p>So the matched cells are collapsed into <b>groups</b> first - connected components under
 * {@code group_radius} - and spacing is measured between group centres. A pillar is one group
 * however many blocks tall it is, a wide bookcase is one group however many blocks wide, and a
 * solid clump is <i>one</i> group, which falls under {@code min_count} and grades 0 rather than 1.
 * This also settles the question #169 raises about what shelving should look like: a library's
 * shelves are properly big slabs, and grouping means a wide, tall bookcase is not punished for
 * being solid - only the intervals between separate bookcases are read.
 *
 * <p>Confidence is the coefficient of variation of the groups' nearest-neighbour distances,
 * inverted against {@code tolerance}: {@code 1 - cv/tolerance}, clamped. Evenly spaced groups have
 * a cv of 0 and score 1; a random scatter's nearest-neighbour distances have a cv around 0.5, which
 * is the default {@code tolerance} and so scores 0. One pillar out of line moves the cv a little and
 * grades just under 1, which is the continuity #25's rule 2 requires.
 *
 * <p>Worth knowing before tuning a form: six bays of 4 with one pillar a block off its mark measure
 * 4, 4, 4, 3, 3, 4 - a cv of 0.13, so about a quarter off the clause. That is sensitive, and it is
 * the price of the default {@code tolerance} being the cv a genuinely random scatter produces (about
 * 0.52 for a Poisson point process), which is what makes a scatter grade 0 rather than merely low. A
 * form that wants to forgive a wonkier hall raises {@code tolerance} and accepts that a scatter then
 * scores a little above nothing.
 *
 * <h2>Below {@code min_count} this says nothing, and says it as 0</h2>
 *
 * Two groups are always perfectly spaced - there is one distance and it cannot vary - so a pair
 * would otherwise score a flawless 1.0 on no evidence at all. Below {@code min_count} the clause
 * grades 0, because "not enough to tell" and "perfectly regular" must not be the same answer, and
 * {@code min_count} is validated at 3 or more for the same reason.
 *
 * <h2>Why this is not {@code soulhome:irregularity} with a sign flip</h2>
 *
 * #169 asks the question directly, and the answer is that the two measure different objects.
 * {@link IrregularityClauseType} asks whether one element's cells lie on straight axis-aligned runs
 * - a property of the surface of a single mass, which is how it tells a carved hollow from a squared
 * off room. This asks how separate masses are distributed relative to each other, which is a
 * property of the gaps between them and not of any mass. Inverting either does not produce the
 * other: a solid cube is maximally regular under {@code irregularity} and says nothing whatsoever
 * about repetition, while an evenly spaced colonnade is "regular" under both readings and a scatter
 * of single blocks is irregular under one and irregular under the other for unrelated reasons. They
 * stay separate clauses; the same note is on {@code IrregularityClauseType}.
 */
public final class SpacingClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:spacing";
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
                ClauseParamSpec.optional("min_count", ClauseParamSpec.Type.INT, 3),
                ClauseParamSpec.optional("group_radius", ClauseParamSpec.Type.INT, 1),
                ClauseParamSpec.optional("tolerance", ClauseParamSpec.Type.DOUBLE, 0.5d));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new SpacingClause(
                params.getElement("of"), params.getInt("min_count"), params.getInt("group_radius"),
                params.getDouble("tolerance"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        SpacingClause spacing = (SpacingClause) clause;
        return Map.of(
                "of", spacing.of(),
                "min_count", spacing.minCount(),
                "group_radius", spacing.groupRadius(),
                "tolerance", spacing.tolerance());
    }
}

record SpacingClause(String of, int minCount, int groupRadius, double tolerance) implements FormClause
{
    /** Two groups are always perfectly spaced, so nothing below this can be evidence of anything. */
    static final int LEAST_MEANINGFUL_COUNT = 3;

    @Override
    public String typeId()
    {
        return "soulhome:spacing";
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

        List<CellGraphs.Centroid> centres = groupCentres(cells);

        if (centres.size() < Math.max(this.minCount, LEAST_MEANINGFUL_COUNT))
        {
            return FormResult.of(0d, tooFewDiagnostic(centres.size()));
        }

        double[] nearest = nearestNeighbourDistances(centres);
        final double mean = mean(nearest);

        if (mean <= 0d)
        {
            return FormResult.of(0d, tooFewDiagnostic(centres.size()));
        }

        final double variation = standardDeviation(nearest, mean) / mean;
        final double confidence = ClauseMath.clamp01(1d - variation / this.tolerance);

        return FormResult.of(confidence, diagnostic(confidence, centres.size(), mean));
    }

    /**
     * One centre per connected group of matched cells. {@code group_radius} is what counts as the
     * same group: 1 - the default - makes any two touching cells, diagonals included, one unit, so a
     * pillar, a wide bookcase or a two-block lantern-and-chain fixture each collapse to a single
     * point before any interval is measured.
     */
    private List<CellGraphs.Centroid> groupCentres(List<RegionGeometry.Cell> cells)
    {
        List<CellGraphs.Centroid> centres = new ArrayList<>();

        for (List<RegionGeometry.Cell> group : CellGraphs.components(cells, CellGraphs.chebyshevOffsets(this.groupRadius)))
        {
            centres.add(CellGraphs.centroidOf(group));
        }

        return centres;
    }

    /** For each group, how far the closest other group's centre is - straight-line, in three dimensions. */
    private double[] nearestNeighbourDistances(List<CellGraphs.Centroid> centres)
    {
        double[] nearest = new double[centres.size()];

        for (int i = 0; i < centres.size(); i++)
        {
            double closest = Double.MAX_VALUE;

            for (int j = 0; j < centres.size(); j++)
            {
                if (i == j)
                {
                    continue;
                }

                closest = Math.min(closest, distance(centres.get(i), centres.get(j)));
            }

            nearest[i] = closest;
        }

        return nearest;
    }

    private static double distance(CellGraphs.Centroid a, CellGraphs.Centroid b)
    {
        final double dx = a.x() - b.x();
        final double dy = a.y() - b.y();
        final double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double mean(double[] values)
    {
        double total = 0d;

        for (double value : values)
        {
            total += value;
        }

        return total / values.length;
    }

    private static double standardDeviation(double[] values, double mean)
    {
        double squares = 0d;

        for (double value : values)
        {
            final double delta = value - mean;
            squares += delta * delta;
        }

        return Math.sqrt(squares / values.length);
    }

    private String tooFewDiagnostic(int groups)
    {
        if (groups <= 1)
        {
            return "the '" + this.of + "' are one solid mass - nothing here repeats at an interval";
        }

        return "there are only " + groups + " runs of '" + this.of + "', too few to read as a pattern";
    }

    private String diagnostic(double confidence, int groups, double meanSpacing)
    {
        if (confidence >= 1d)
        {
            return "";
        }

        if (confidence <= 0d)
        {
            return "the " + groups + " runs of '" + this.of + "' are placed unevenly, not at intervals";
        }

        return groups + " runs of '" + this.of + "' stand about " + trim(meanSpacing)
                + " blocks apart, but unevenly";
    }

    private static String trim(double value)
    {
        return Math.abs(value - Math.rint(value)) < 1.0e-6d
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' repeats at regular intervals, in at least " + this.minCount
                + " separate runs";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.minCount < LEAST_MEANINGFUL_COUNT)
        {
            errors.add("'min_count' must be at least " + LEAST_MEANINGFUL_COUNT
                    + " - two groups are always perfectly spaced, so a lower bar scores full marks on no"
                    + " evidence, got " + this.minCount);
        }

        if (this.groupRadius < 1)
        {
            errors.add("'group_radius' must be at least 1 - below that no two cells are ever one group,"
                    + " got " + this.groupRadius);
        }

        if (this.tolerance <= 0d)
        {
            errors.add("'tolerance' must be positive - it is the unevenness at which spacing reads as"
                    + " random, got " + this.tolerance);
        }

        return errors;
    }
}
