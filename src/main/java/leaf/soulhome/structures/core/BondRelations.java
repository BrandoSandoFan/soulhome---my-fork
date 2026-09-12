/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;
import java.util.Locale;

/**
 * The seven relations the mod ships - #144 ({@code adjoins}, {@code near}), #145
 * ({@code connects}) and #146 ({@code above}/{@code beneath}, {@code encloses}/{@code within}).
 * Registered onto {@link BondRelationRegistry#BUILTIN} by {@code BuiltinBondRelations}.
 *
 * <p>Every one is graded, never boolean (rule 2 of #140): two rooms sharing a third of a wall are
 * a third adjoined, not "not adjoined". Every one reads only {@link RegionAdjacency}: no relation
 * here walks the volume itself.
 */
public final class BondRelations
{
    private BondRelations()
    {
    }

    public static List<BondRelation> all()
    {
        return List.of(
                new Adjoins(), new Near(), new Connects(),
                new Above(), new Beneath(), new Encloses(), new Within());
    }

    private static String percent(double fraction)
    {
        return Math.round(fraction * 100d) + "%";
    }

    /**
     * {@code adjoins}: these two rooms share a wall.
     *
     * <p>Graded as the shared cells against the <b>smaller</b> room's largest wall, so a closet
     * sharing its entire wall with a great hall is fully adjoined even though it covers a tenth
     * of the hall's shell, and a small study off a hall is not penalised for the hall being large.
     * Against a wall rather than the whole shell because "shares its whole wall" is what a player
     * means by adjoining, and one face of a box is a sixth of its shell. Below {@code min_coverage}
     * it grades zero, so two rooms brushing at a corner do not read as adjoining. An open-air
     * region has no shell and never adjoins anything - {@code near} is the relation for a garden
     * against a house.
     */
    public static final class Adjoins implements BondRelation
    {
        @Override
        public String id()
        {
            return "adjoins";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(ClauseParamSpec.optional("min_coverage", ClauseParamSpec.Type.DOUBLE, 0.1d));
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            final int wall = Math.min(adjacency.faceArea(self), adjacency.faceArea(other));

            if (wall <= 0)
            {
                return BondGrade.of(0d, "has no walls to share");
            }

            final int shared = adjacency.sharedShellCells(self, other);
            final double coverage = (double) shared / wall;
            final double minCoverage = params.getDouble("min_coverage");

            if (shared == 0)
            {
                return BondGrade.of(0d, "shares no wall with it");
            }

            if (coverage < minCoverage)
            {
                return BondGrade.of(0d, "shares only " + shared + " wall block(s) with it; "
                        + (int) Math.ceil(minCoverage * wall) + " would count");
            }

            return BondGrade.of(coverage, "shares " + percent(Math.min(1d, coverage)) + " of a wall with it");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "sharing a wall";
        }
    }

    /**
     * {@code near}: close together, touching or not. Geodesic distance through crossable space,
     * for the same reason the open-air cluster reach is (see {@link RegionScanner}): a wall between
     * two rooms should mean something. The right relation for atmosphere rather than architecture
     * - a hearth warming the rooms around it - where a player should not have to cut a doorway.
     */
    public static final class Near implements BondRelation
    {
        @Override
        public String id()
        {
            return "near";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 12));
        }

        @Override
        public int reach(ClauseParams params)
        {
            return params.getInt("max_distance");
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            final int max = params.getInt("max_distance");
            final int separation = adjacency.separation(self, other);

            if (separation == RegionAdjacency.UNREACHABLE)
            {
                return BondGrade.of(0d, "no route to it within " + max + " blocks");
            }

            if (separation >= max)
            {
                return BondGrade.of(0d, separation + " blocks away; within " + max + " would count");
            }

            final double confidence = 1d - (double) separation / max;
            return BondGrade.of(confidence, separation == 0 ? "right beside it" : separation + " blocks away");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "within " + params.getInt("max_distance") + " blocks";
        }
    }

    /**
     * {@code connects}: a way to get from one room to the other - the thing that distinguishes a
     * house from a warehouse of boxes. A library with a door into the enchanting room connects; a
     * short hall between them connects nearly as well; a shared wall with no opening does not
     * connect at all, however fully it adjoins, which is the distinction that earns this relation
     * its place. Doors, trapdoors and gates, an open arch, a ladder, a one-cell gap: anything a
     * player could walk, climb or crawl, and nothing special-cased - see {@link RegionAdjacency}
     * for the door asymmetry with region detection.
     */
    public static final class Connects implements BondRelation
    {
        @Override
        public String id()
        {
            return "connects";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(
                    ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 16),
                    // a doorway in a shared wall is one cell between the two interiors
                    ClauseParamSpec.optional("ideal_length", ClauseParamSpec.Type.INT, 1));
        }

        @Override
        public int reach(ClauseParams params)
        {
            return params.getInt("max_distance");
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            final int max = params.getInt("max_distance");
            final int ideal = params.getInt("ideal_length");
            final int length = adjacency.pathLength(self, other);

            if (length == RegionAdjacency.UNREACHABLE)
            {
                return BondGrade.of(0d, adjacency.sharedShellCells(self, other) > 0
                        ? "shares a wall with it but there is no way through"
                        : "no way through to it within " + max + " blocks");
            }

            if (length <= ideal)
            {
                return BondGrade.of(1d, "opens straight into it");
            }

            if (length >= max)
            {
                return BondGrade.of(0d, "the way through is " + length + " blocks long; " + max + " or less would count");
            }

            final double confidence = 1d - (double) (length - ideal) / (max - ideal);
            return BondGrade.of(confidence, "a way through " + length + " blocks long");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "with a way through between them, up to " + params.getInt("max_distance") + " blocks long";
        }
    }

    /**
     * {@code above}: this room is built over the other. Footprint overlap against the smaller
     * footprint, as {@code adjoins} grades against the smaller wall and for the same reason: a
     * small tower over a large hall is fully above it. Zero unless the room's air sits wholly
     * above the other's, and zero past {@code max_vertical_gap} so a ground floor and a room six
     * storeys up are not "above" each other in any sense a player means.
     */
    public static final class Above implements BondRelation
    {
        @Override
        public String id()
        {
            return "above";
        }

        @Override
        public String mirror()
        {
            return "beneath";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(ClauseParamSpec.optional("max_vertical_gap", ClauseParamSpec.Type.INT, 4));
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            return gradeStacked(self, other, adjacency, params, "above");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "built above it, with at most " + params.getInt("max_vertical_gap") + " blocks between";
        }
    }

    /** {@code beneath}: {@link Above} from the other end. */
    public static final class Beneath implements BondRelation
    {
        @Override
        public String id()
        {
            return "beneath";
        }

        @Override
        public String mirror()
        {
            return "above";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(ClauseParamSpec.optional("max_vertical_gap", ClauseParamSpec.Type.INT, 4));
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            return gradeStacked(other, self, adjacency, params, "beneath");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "built beneath it, with at most " + params.getInt("max_vertical_gap") + " blocks between";
        }
    }

    private static BondGrade gradeStacked(int upper, int lower, RegionAdjacency adjacency, ClauseParams params, String word)
    {
        if (!adjacency.isAbove(upper, lower))
        {
            return BondGrade.of(0d, adjacency.footprintOverlap(upper, lower) > 0
                    ? "not wholly " + word + " it"
                    : "not built over the same ground");
        }

        final int gap = adjacency.verticalGap(upper, lower);
        final int maxGap = params.getInt("max_vertical_gap");

        if (gap > maxGap)
        {
            return BondGrade.of(0d, gap + " blocks between them; at most " + maxGap + " would count");
        }

        final int smaller = Math.min(adjacency.footprint(upper), adjacency.footprint(lower));

        if (smaller <= 0)
        {
            return BondGrade.NONE;
        }

        final double coverage = (double) adjacency.footprintOverlap(upper, lower) / smaller;
        return BondGrade.of(coverage, word + " it over " + percent(Math.min(1d, coverage)) + " of its ground");
    }

    /**
     * {@code encloses}: the other room stands inside this one's footprint - a garden in a
     * courtyard, a shrine in the infield of a track. Graded as the contained share of the
     * <i>inner</i> region's footprint, so a garden wholly inside the ring is 1.0 and one half
     * spilling out is 0.5. Requires the two to overlap in height, or every cellar would be
     * "within" the kitchen over it; that is {@code beneath}.
     */
    public static final class Encloses implements BondRelation
    {
        @Override
        public String id()
        {
            return "encloses";
        }

        @Override
        public String mirror()
        {
            return "within";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(ClauseParamSpec.optional("min_coverage", ClauseParamSpec.Type.DOUBLE, 0.25d));
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            return gradeContained(self, other, adjacency, params, "standing around");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "standing around it";
        }
    }

    /** {@code within}: {@link Encloses} from the inside. */
    public static final class Within implements BondRelation
    {
        @Override
        public String id()
        {
            return "within";
        }

        @Override
        public String mirror()
        {
            return "encloses";
        }

        @Override
        public List<ClauseParamSpec> params()
        {
            return List.of(ClauseParamSpec.optional("min_coverage", ClauseParamSpec.Type.DOUBLE, 0.25d));
        }

        @Override
        public BondGrade grade(int self, int other, RegionAdjacency adjacency, ClauseParams params)
        {
            return gradeContained(other, self, adjacency, params, "standing inside");
        }

        @Override
        public String describe(ClauseParams params)
        {
            return "standing inside it";
        }
    }

    private static BondGrade gradeContained(int outer, int inner, RegionAdjacency adjacency, ClauseParams params, String word)
    {
        final int footprint = adjacency.footprint(inner);

        if (footprint <= 0)
        {
            return BondGrade.NONE;
        }

        if (!adjacency.sharesStorey(outer, inner))
        {
            return BondGrade.of(0d, "not on the same level");
        }

        final double coverage = (double) adjacency.footprintOverlap(outer, inner) / footprint;
        final double minCoverage = params.getDouble("min_coverage");

        if (coverage <= 0d)
        {
            return BondGrade.of(0d, "not " + word + " it");
        }

        if (coverage < minCoverage)
        {
            return BondGrade.of(0d, percent(coverage) + " of it is inside; " + percent(minCoverage) + " would count");
        }

        return BondGrade.of(coverage, String.format(Locale.ROOT, "%s it, %s inside", word, percent(Math.min(1d, coverage))));
    }
}
