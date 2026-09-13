/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What terrain growth (#158) actually does to the three islands a player is really given, rather
 * than to a hand-written layout small enough to have been drawn around the answer.
 *
 * <p>The same bargain {@link SoulIslandCorpusTest} makes, for the same reason: every fault in the
 * region detection epic was found by a player walking into a fresh soul, and none of the
 * hand-written cases would have shown any of them. Growth is a feature that writes blocks into
 * somebody's private dimension with no undo, so the layouts it is proved against should be the
 * ones it will meet.
 *
 * <p>The islands are read in template coordinates and placed into world ones exactly as
 * {@code DimensionRegistry} places them - centred on the origin horizontally, and anchored so the
 * highest solid block in the spawn column sits on {@code FLOOR_LEVEL}. Getting that wrong would
 * make every case here agree with itself and disagree with the game.
 */
class TerrainGrowthCorpusTest
{
    /** {@code DimensionHelper.FLOOR_LEVEL}, which {@code SoulBounds.DEFAULT_FLOOR_Y} matches by hand. */
    private static final int FLOOR = SoulBounds.DEFAULT_FLOOR_Y;

    private static final TerrainGrowthSettings SETTINGS = TerrainGrowthSettings.DEFAULTS;

    @Test
    @DisplayName("every shipped island grows a coast on its first ascension")
    void everyIslandGrows()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            final ApronPlan plan = growOnce(island, 1, 0);

            assertFalse(
                    plan.isEmpty(),
                    "soul_island" + island.style() + " grew no ground at all on its first ascension");
        }
    }

    @Test
    @DisplayName("nothing the island already holds is ever planned over")
    void theIslandItselfIsUntouched()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            final GroundSurvey survey = surveyOf(island, 1);

            for (ApronPlan.Column column : growOnce(island, 1, 0).columns())
            {
                assertEquals(
                        GroundSurvey.Kind.VOID, survey.kindAt(column.x(), column.z()),
                        "soul_island" + island.style() + " planned ground at " + column.x() + "," + column.z()
                                + ", where it already holds something");
            }
        }
    }

    @Test
    @DisplayName("every column of new ground lands inside the box for the rank that granted it")
    void newGroundLandsInsideTheBox()
    {
        for (int rank = 1; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulBounds bounds = SoulBounds.forRank(rank);
            final int limit = SETTINGS.groundLimit(rank, bounds.vergeHalfExtent());

            for (SoulIslandVolume island : SoulIslandVolume.allShipped())
            {
                for (ApronPlan.Column column : growOnce(island, rank, rank - 1).columns())
                {
                    assertTrue(
                            bounds.contains(column.x(), column.surfaceY(), column.z()),
                            "soul_island" + island.style() + " at rank " + rank + " planned "
                                    + column.x() + "," + column.surfaceY() + "," + column.z() + " outside its box");

                    assertTrue(
                            Math.abs(column.x()) <= limit && Math.abs(column.z()) <= limit,
                            "soul_island" + island.style() + " at rank " + rank + " planned "
                                    + column.x() + "," + column.z() + " past its ground limit of " + limit);
                }
            }
        }
    }

    @Test
    @DisplayName("there is still open verge to build into once the ground has grown")
    void openVergeRemains()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            final SoulBounds bounds = SoulBounds.forRank(1);
            int reach = 0;

            for (ApronPlan.Column column : growOnce(island, 1, 0).columns())
            {
                reach = Math.max(reach, Math.max(Math.abs(column.x()), Math.abs(column.z())));
            }

            assertTrue(
                    reach < bounds.vergeHalfExtent(),
                    "soul_island" + island.style() + " grew ground out to " + reach
                            + ", and its walls are only at " + bounds.vergeHalfExtent());
        }
    }

    @Test
    @DisplayName("the same island at the same rank grows the same ground every time")
    void growthIsDeterministic()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            final List<ApronPlan.Column> first = growOnce(island, 2, 1).columns();
            final List<ApronPlan.Column> second = growOnce(island, 2, 1).columns();

            assertEquals(first, second, "soul_island" + island.style() + " grew differently the second time");
        }
    }

    @Test
    @DisplayName("a soul that climbs rank by rank ends up where one that catches up all at once does")
    void climbingAndCatchingUpAgree()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            // one rank at a time, each band measured from the ground the last one left
            GroundSurvey stepwise = surveyOf(island, SoulBounds.MAX_RANK);

            for (int rank = 1; rank <= SoulBounds.MAX_RANK; rank++)
            {
                apply(stepwise, plan(stepwise, rank, rank - 1, island));
            }

            // and all at once, as a soulhome that was already ranked before growth existed does
            GroundSurvey atOnce = surveyOf(island, SoulBounds.MAX_RANK);
            apply(atOnce, plan(atOnce, SoulBounds.MAX_RANK, 0, island));

            final int limit = SETTINGS.groundLimit(
                    SoulBounds.MAX_RANK, SoulBounds.forRank(SoulBounds.MAX_RANK).vergeHalfExtent());

            // The two do not grow an identical coastline, and are not meant to. The stepwise one
            // measures each band from the coast the previous band left and pays the edge jitter
            // five times; the catch-up pays it once, and so reaches a block or two further. What
            // has to hold is that neither crosses the limit and that they arrive at the same sort
            // of island - a player who climbed patiently must not find they have less ground than
            // one who was handed rank V by an operator.
            final int stepwiseReach = ApronPlanner.groundReach(stepwise);
            final int atOnceReach = ApronPlanner.groundReach(atOnce);
            final int startingReach = ApronPlanner.groundReach(surveyOf(island, SoulBounds.MAX_RANK));

            assertTrue(stepwiseReach <= limit, "climbing rank by rank reached " + stepwiseReach + " past " + limit);
            assertTrue(atOnceReach <= limit, "catching up reached " + atOnceReach + " past " + limit);

            assertTrue(
                    stepwiseReach > startingReach && atOnceReach > startingReach,
                    "soul_island" + island.style() + " grew no further than it started (" + startingReach + ")");

            assertTrue(
                    Math.abs(stepwiseReach - atOnceReach) <= SETTINGS.groundPerRank(),
                    "soul_island" + island.style() + " ends up a rank's worth of ground different depending on how it"
                            + " got there: " + stepwiseReach + " climbing against " + atOnceReach + " catching up");
        }
    }

    @Test
    @DisplayName("a player's tower on the island's edge keeps its clearance, and is never grown over")
    void aBuildOnTheEdgeKeepsItsClearance()
    {
        for (SoulIslandVolume island : SoulIslandVolume.allShipped())
        {
            final GroundSurvey survey = surveyOf(island, 1);

            // a tower standing in the void just off the island's east edge, as a player who
            // bridged out and built before ascending would leave it
            final int towerX = ApronPlanner.groundReach(survey) + 4;
            survey.set(towerX, 0, GroundSurvey.Kind.BUILT, FLOOR + 12);

            final ApronPlan plan = ApronPlanner.plan(
                    survey, SETTINGS, 1, 0, SoulBounds.forRank(1).vergeHalfExtent(), null, island.style());

            for (ApronPlan.Column column : plan.columns())
            {
                final int distance = Math.max(Math.abs(column.x() - towerX), Math.abs(column.z()));

                assertTrue(
                        distance > SETTINGS.clearanceMargin(),
                        "soul_island" + island.style() + " planned ground at " + column.x() + "," + column.z()
                                + ", only " + distance + " from a tower the player built");
            }
        }
    }

    private static ApronPlan growOnce(SoulIslandVolume island, int rank, int grownRank)
    {
        return plan(surveyOf(island, rank), rank, grownRank, island);
    }

    private static ApronPlan plan(GroundSurvey survey, int rank, int grownRank, SoulIslandVolume island)
    {
        return ApronPlanner.plan(
                survey, SETTINGS, rank, grownRank, SoulBounds.forRank(rank).vergeHalfExtent(), null, island.style());
    }

    /** Lay a plan down, so the next rank grows from the coast this one left. */
    private static void apply(GroundSurvey survey, ApronPlan plan)
    {
        for (ApronPlan.Column column : plan.columns())
        {
            survey.set(column.x(), column.z(), GroundSurvey.Kind.GROUND, column.surfaceY());
        }
    }

    /**
     * One shipped island as the columns growth sees, in world coordinates.
     *
     * <p>Placed the way {@code DimensionRegistry.createSoulDimension} places it: centred on the
     * origin horizontally, and anchored so the highest solid block in the spawn column lands on
     * {@code FLOOR_LEVEL}. That anchoring is #97's, and a survey that used template coordinates
     * instead would put the whole island below its own box's floor and conclude it was all void.
     */
    private static GroundSurvey surveyOf(SoulIslandVolume island, int rank)
    {
        final int offsetX = -island.templateSizeX() / 2;
        final int offsetZ = -island.templateSizeZ() / 2;
        final int offsetY = FLOOR - spawnColumnTop(island);

        final SoulBounds bounds = SoulBounds.forRank(rank);
        final int reach = SETTINGS.groundLimit(rank, bounds.vergeHalfExtent()) + SETTINGS.clearanceMargin();
        final GroundSurvey survey = new GroundSurvey(-reach, -reach, reach, reach);

        for (int x = -reach; x <= reach; x++)
        {
            for (int z = -reach; z <= reach; z++)
            {
                final int top = columnTop(island, x - offsetX, z - offsetZ);

                if (top == Integer.MIN_VALUE)
                {
                    survey.set(x, z, GroundSurvey.Kind.VOID, 0);
                    continue;
                }

                final int worldTop = top + offsetY;

                if (worldTop > FLOOR + SETTINGS.groundBand())
                {
                    survey.set(x, z, GroundSurvey.Kind.BUILT, worldTop);
                }
                else if (worldTop >= FLOOR)
                {
                    survey.set(x, z, GroundSurvey.Kind.GROUND, worldTop);
                }
                else
                {
                    survey.set(x, z, GroundSurvey.Kind.VOID, 0);
                }
            }
        }

        return survey;
    }

    /** The highest non-air block in one template column, or {@link Integer#MIN_VALUE} for an empty one. */
    private static int columnTop(SoulIslandVolume island, int localX, int localZ)
    {
        for (int y = island.templateSizeY() - 1; y >= 0; y--)
        {
            if (island.passabilityAt(localX, y, localZ) != Passability.EMPTY)
            {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    /** As {@code DimensionRegistry.highestSolidBlockY}: the spawn column first, the whole island after. */
    private static int spawnColumnTop(SoulIslandVolume island)
    {
        final int top = columnTop(island, island.templateSizeX() / 2, island.templateSizeZ() / 2);

        if (top != Integer.MIN_VALUE)
        {
            return top;
        }

        int highest = Integer.MIN_VALUE;

        for (int x = 0; x < island.templateSizeX(); x++)
        {
            for (int z = 0; z < island.templateSizeZ(); z++)
            {
                highest = Math.max(highest, columnTop(island, x, z));
            }
        }

        return highest == Integer.MIN_VALUE ? 0 : highest;
    }
}
