/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Small, stateless building blocks shared by the {@code shape}/{@code relation} clause types
 * (#29-#32): distance metrics, the {@code coverage} scaling every relation grades through, and the
 * straight-line clearance walk {@code across} uses for {@code require_clear}.
 */
final class ClauseMath
{
    private ClauseMath()
    {
    }

    static double clamp01(double value)
    {
        return Math.max(0d, Math.min(1d, value));
    }

    /** Chebyshev (max of the per-axis deltas) or Euclidean distance between two cells, by name. */
    static double distance(RegionGeometry.Cell a, RegionGeometry.Cell b, String metric)
    {
        final int dx = Math.abs(a.x() - b.x());
        final int dy = Math.abs(a.y() - b.y());
        final int dz = Math.abs(a.z() - b.z());

        if ("euclidean".equalsIgnoreCase(metric))
        {
            return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        }

        return Math.max(dx, Math.max(dy, dz));
    }

    /** Chebyshev distance restricted to the X/Z plane - "horizontally adjacent", ignoring height. */
    static int horizontalChebyshev(RegionGeometry.Cell a, RegionGeometry.Cell b)
    {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    /**
     * A relation's confidence given what fraction of its {@code of} cells were satisfied and the
     * {@code coverage} the datapack asked for: {@code coverage} is the fraction that already counts
     * as fully satisfied, ramped linearly below that. See the relations table in #29.
     */
    static double coverageConfidence(double satisfiedFraction, double coverage)
    {
        if (coverage <= 0d)
        {
            return satisfiedFraction > 0d ? 1d : 0d;
        }

        return clamp01(satisfiedFraction / coverage);
    }

    /**
     * Whether every cell strictly between {@code from} and {@code to} is open, per
     * {@link RegionGeometry#isBlocked}. Steps along whichever axis separates the two cells most,
     * interpolating the others - exact on an axis-aligned pair, an approximation (but a reasonable
     * one, since {@code across} only ever considers pairs at most one cell off axis) otherwise.
     * Adjacent or coincident cells have nothing between them and are trivially clear.
     */
    static boolean isPathClear(RegionGeometry.Cell from, RegionGeometry.Cell to, RegionGeometry geometry)
    {
        final int dx = to.x() - from.x();
        final int dy = to.y() - from.y();
        final int dz = to.z() - from.z();
        final int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));

        if (steps <= 1)
        {
            return true;
        }

        for (int i = 1; i < steps; i++)
        {
            final double t = (double) i / steps;
            final int x = from.x() + (int) Math.round(dx * t);
            final int y = from.y() + (int) Math.round(dy * t);
            final int z = from.z() + (int) Math.round(dz * t);

            if (geometry.isBlocked(x, y, z))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * A short, specific sentence for the common "N of M satisfied" shape every relation's
     * diagnostic takes: silent when everything is satisfied, otherwise naming the count and phrase.
     *
     * @param verbPhrase describes what a satisfied {@code of} cell does, e.g. {@code "is within
     *                    reading distance of"} - stitched together as "{@code <of> <verbPhrase} {@code
     *                    <to>}"
     */
    static String coverageDiagnostic(int satisfied, int total, String of, String verbPhrase, String to)
    {
        if (total == 0)
        {
            return "";
        }

        if (satisfied == 0)
        {
            return "no '" + of + "' " + verbPhrase + " '" + to + "'";
        }

        if (satisfied < total)
        {
            return satisfied + " of " + total + " '" + of + "' " + verbPhrase + " '" + to
                    + "' - the others do not";
        }

        return "";
    }

    /** "no '<name>' were found" - the shared shape of the absent-element diagnostic every clause uses. */
    static String missingElementDiagnostic(String name)
    {
        return "no '" + name + "' were found";
    }

    /**
     * The shape almost every relation in #29 shares: for each {@code of} cell, look for any
     * {@code to} cell that satisfies a pairwise test; grade the fraction that found one through
     * {@link #coverageConfidence}, and explain a partial or zero result through
     * {@link #coverageDiagnostic}. An absent {@code of} or {@code to} - the matcher not declared, or
     * declared but matching nothing in this region - scores 0 and names which side is missing,
     * exactly as #29 requires.
     */
    static FormResult evaluateCoverage(
            RegionGeometry geometry,
            Map<String, BlockMatcher> elements,
            String ofName,
            String toName,
            double coverage,
            String verbPhrase,
            BiPredicate<RegionGeometry.Cell, RegionGeometry.Cell> satisfies)
    {
        BlockMatcher ofMatcher = elements.get(ofName);
        BlockMatcher toMatcher = elements.get(toName);

        if (ofMatcher == null)
        {
            return FormResult.of(0d, missingElementDiagnostic(ofName));
        }

        if (toMatcher == null)
        {
            return FormResult.of(0d, missingElementDiagnostic(toName));
        }

        List<RegionGeometry.Cell> ofCells = geometry.cellsMatching(ofMatcher);
        List<RegionGeometry.Cell> toCells = geometry.cellsMatching(toMatcher);

        if (ofCells.isEmpty())
        {
            return FormResult.of(0d, missingElementDiagnostic(ofName));
        }

        if (toCells.isEmpty())
        {
            return FormResult.of(0d, missingElementDiagnostic(toName));
        }

        int satisfied = 0;

        for (RegionGeometry.Cell of : ofCells)
        {
            for (RegionGeometry.Cell to : toCells)
            {
                if (satisfies.test(of, to))
                {
                    satisfied++;
                    break;
                }
            }
        }

        double confidence = coverageConfidence((double) satisfied / ofCells.size(), coverage);
        String diagnostic = coverageDiagnostic(satisfied, ofCells.size(), ofName, verbPhrase, toName);
        return FormResult.of(confidence, diagnostic);
    }

    /**
     * Whether {@code of} sits directly above (or, mirrored, below) {@code to}: same column - exact
     * X/Z when {@code alignment} is {@code "column"}, within one horizontal cell when it is
     * {@code "loose"} - and within {@code maxDrop} vertically, strictly on the named side.
     */
    static boolean verticallyAligned(
            RegionGeometry.Cell of, RegionGeometry.Cell to, boolean ofAboveTo, int maxDrop, String alignment)
    {
        boolean sameColumn = "loose".equalsIgnoreCase(alignment)
                ? horizontalChebyshev(of, to) <= 1
                : of.x() == to.x() && of.z() == to.z();

        if (!sameColumn)
        {
            return false;
        }

        final int drop = ofAboveTo ? of.y() - to.y() : to.y() - of.y();
        return drop > 0 && drop <= maxDrop;
    }

    /** Horizontally adjacent (including diagonally) at nearly the same height - what {@code beside} means. */
    static boolean isBeside(RegionGeometry.Cell of, RegionGeometry.Cell to, int maxDrop)
    {
        return horizontalChebyshev(of, to) == 1 && Math.abs(of.y() - to.y()) <= maxDrop;
    }

    /** Bounding box of a set of cells, expanded by {@code margin} on every side - a fallback when a geometry carries no {@link RegionGeometry#bounds()} of its own. */
    static RegionBounds boundingBox(List<RegionGeometry.Cell> cells, int margin)
    {
        RegionGeometry.Cell first = cells.get(0);
        RegionBounds box = RegionBounds.of(first.x(), first.y(), first.z());

        for (RegionGeometry.Cell cell : cells)
        {
            box = box.encompass(cell.x(), cell.y(), cell.z());
        }

        return box.expand(margin);
    }
}
