/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What terrain growth (#158) may and may not do to a soulhome.
 *
 * <p>The cases about {@link GroundSurvey.Kind#BUILT} are the ones that matter most: #160 is the
 * highest-consequence failure in the epic, because generating over a player's tower destroys work
 * with no undo in the one dimension whose whole premise is that it is theirs.
 */
class ApronPlannerTest
{
    private static final int FLOOR = 70;

    /** No jitter, so a case about extent is about extent rather than about a coastline. */
    private static final TerrainGrowthSettings SMOOTH = new TerrainGrowthSettings(
            true, 18, 12, 6, 3, 3, 4, 0, 4);

    private static final long SEED = 0x50554CL;

    @Test
    @DisplayName("an ascension grows ground outward from the island, and none of it lands on the island")
    void groundGrowsOutwardFromTheIsland()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 4, 20);
        ApronPlan plan = ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED);

        assertFalse(plan.isEmpty(), "an ascension with room to grow should plan some ground");

        for (ApronPlan.Column column : plan.columns())
        {
            assertEquals(
                    GroundSurvey.Kind.VOID, survey.kindAt(column.x(), column.z()),
                    "growth planned a column that already held something at " + column.x() + "," + column.z());
        }

        assertTrue(GroundGrid.planned(plan, 5, 0), "the column just off the island's edge should be apron");
    }

    @Test
    @DisplayName("the band follows the island's outline rather than squaring it off")
    void theBandFollowsTheOutline()
    {
        // an island with a bay bitten out of its east side
        GroundSurvey survey = GroundGrid.of(FLOOR,
                "...........",
                "...........",
                "..######...",
                "..######...",
                "..###......",
                "..###......",
                "..######...",
                "..######...",
                "...........",
                "...........",
                "...........");

        TerrainGrowthSettings narrow = new TerrainGrowthSettings(true, 18, 1, 6, 3, 3, 4, 0, 4);
        ApronPlan plan = ApronPlanner.plan(survey, narrow, 1, 0, 40, null, SEED);

        final String rendered = GroundGrid.render(survey, plan);

        // the bay is at x = 0..2, z = -1..0 in world terms. The coast east of the solid part of the
        // island grows; the coast east of the bay does not, because the ground there is recessed
        // and a band measured from the ground has nothing that far out to grow from. That is the
        // whole difference between an offset outline and a square drawn round the island.
        assertTrue(GroundGrid.planned(plan, 3, 2), "the coast should grow where the island is solid:\n" + rendered);
        assertFalse(GroundGrid.planned(plan, 3, 0), "and stay recessed where the island is:\n" + rendered);
        assertTrue(GroundGrid.planned(plan, 0, -1), "the band should follow the bay in:\n" + rendered);
    }

    @Test
    @DisplayName("ground never crosses the limit, and the limit never reaches the wall")
    void groundStaysInsideItsLimit()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 20, 60);
        ApronPlan plan = ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED);

        final int limit = SMOOTH.groundLimit(1, 40);

        assertEquals(30, limit);

        for (ApronPlan.Column column : plan.columns())
        {
            assertTrue(
                    Math.abs(column.x()) <= limit && Math.abs(column.z()) <= limit,
                    "growth planned " + column.x() + "," + column.z() + " outside the limit of " + limit);
        }

        assertTrue(GroundGrid.planned(plan, limit, 0), "the band should reach its limit");
        assertFalse(GroundGrid.planned(plan, limit + 1, 0), "and stop there");
    }

    @Test
    @DisplayName("a bridge into the verge is never built over, and keeps a moat")
    void aBridgeIsNeverBuiltOver()
    {
        // an island with a bridge running east into the verge, as a player who built out before
        // ascending would leave it
        GroundSurvey survey = GroundGrid.of(FLOOR,
                ".............",
                ".............",
                "..#####......",
                "..#####BBBBB.",
                "..#####......",
                ".............",
                ".............");

        ApronPlan plan = ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED);
        final String rendered = GroundGrid.render(survey, plan);

        for (ApronPlan.Column column : plan.columns())
        {
            assertNotEquals(
                    GroundSurvey.Kind.BUILT, survey.kindAt(column.x(), column.z()),
                    "growth planned a column a player had built in:\n" + rendered);
        }

        // the bridge runs along z = 0 from x = 1 to x = 5; nothing within three columns of it is
        // planned, in either direction
        for (int x = 1; x <= 5; x++)
        {
            for (int dz = -3; dz <= 3; dz++)
            {
                assertFalse(
                        GroundGrid.planned(plan, x, dz),
                        "growth planned " + x + "," + dz + ", inside the bridge's moat:\n" + rendered);
            }
        }
    }

    @Test
    @DisplayName("the moat is a margin, not a shadow: the island still grows past a bridge")
    void theIslandGrowsPastABridge()
    {
        GroundSurvey survey = GroundGrid.of(FLOOR,
                ".............",
                ".............",
                "..#####......",
                "..#####BB....",
                "..#####......",
                ".............",
                ".............");

        ApronPlan plan = ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED);
        final String rendered = GroundGrid.render(survey, plan);

        // the bridge ends at x = 2; four columns past its end is outside the moat and inside the
        // band, so the ground carries on rather than being shadowed by what the player threw across
        assertTrue(GroundGrid.planned(plan, 6, 0), "the band should resume past the bridge:\n" + rendered);
    }

    @Test
    @DisplayName("a legacy soulhome's grandfathered footprint is never generated into at all")
    void theLegacyFootprintIsOffLimits()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 4, 20);
        RegionBounds legacy = new RegionBounds(6, FLOOR, -8, 14, FLOOR + 40, 8);

        ApronPlan plan = ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, legacy, SEED);

        for (ApronPlan.Column column : plan.columns())
        {
            final boolean insideLegacy = column.x() >= legacy.minX() && column.x() <= legacy.maxX()
                    && column.z() >= legacy.minZ() && column.z() <= legacy.maxZ();

            assertFalse(insideLegacy, "growth planned " + column.x() + "," + column.z() + " inside the legacy grant");
        }

        // and the same soul without the grant does grow there, so the case is testing the exclusion
        // rather than a band that was never going to reach
        assertTrue(GroundGrid.planned(ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED), 7, 0));
    }

    @Test
    @DisplayName("a column that could not be read is neither grown into nor grown from")
    void anUnreadableColumnIsNotEmptyGround()
    {
        GroundSurvey survey = GroundGrid.of(FLOOR,
                "???????",
                "???????",
                "???????",
                "???????",
                "???????");

        assertTrue(
                ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED).isEmpty(),
                "a survey that read nothing must plan nothing");
    }

    @Test
    @DisplayName("a soulhome with no ground at all grows none - there is nothing to continue")
    void nothingGrowsFromNothing()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 0, 20);
        survey.set(0, 0, GroundSurvey.Kind.VOID, 0);

        assertTrue(ApronPlanner.plan(survey, SMOOTH, 1, 0, 40, null, SEED).isEmpty());
    }

    @Test
    @DisplayName("the apron inherits its height and its blocks from the ground it grew out of")
    void theApronInheritsFromItsSource()
    {
        // the west half of the island sits three blocks higher than the east half
        GroundSurvey survey = GroundGrid.of(FLOOR,
                ".........",
                ".........",
                "..333###.",
                "..333###.",
                "..333###.",
                ".........",
                ".........");

        TerrainGrowthSettings narrow = new TerrainGrowthSettings(true, 18, 1, 6, 3, 3, 4, 0, 4);
        ApronPlan plan = ApronPlanner.plan(survey, narrow, 1, 0, 40, null, SEED);

        final ApronPlan.Column west = columnAt(plan, -3, 0);
        final ApronPlan.Column east = columnAt(plan, 4, 0);

        assertEquals(FLOOR + 3, west.surfaceY(), "the apron off the high side should continue the high side");
        assertEquals(-2, west.sourceX());
        assertEquals(FLOOR, east.surfaceY(), "and the apron off the low side should continue the low side");
        assertEquals(3, east.sourceX());
    }

    @Test
    @DisplayName("growing is deterministic - the same soul at the same rank plans the same ground")
    void growthIsDeterministic()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 6, 40);

        List<ApronPlan.Column> first = ApronPlanner.plan(
                survey, TerrainGrowthSettings.DEFAULTS, 2, 1, 56, null, SEED).columns();
        List<ApronPlan.Column> second = ApronPlanner.plan(
                survey, TerrainGrowthSettings.DEFAULTS, 2, 1, 56, null, SEED).columns();

        assertEquals(first, second);
    }

    @Test
    @DisplayName("two souls do not grow the same coastline")
    void twoSoulsGrowDifferentCoastlines()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 6, 40);

        List<ApronPlan.Column> mine = ApronPlanner.plan(
                survey, TerrainGrowthSettings.DEFAULTS, 2, 0, 56, null, 1L).columns();
        List<ApronPlan.Column> theirs = ApronPlanner.plan(
                survey, TerrainGrowthSettings.DEFAULTS, 2, 0, 56, null, 2L).columns();

        assertNotEquals(mine, theirs);
    }

    @Test
    @DisplayName("the jitter ragged the edge and nothing else: it never bites into the island's own ground")
    void jitterOnlyTouchesTheEdge()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 6, 40);
        TerrainGrowthSettings settings = TerrainGrowthSettings.DEFAULTS;

        ApronPlan plan = ApronPlanner.plan(survey, settings, 1, 0, 40, null, SEED);

        // every column within the band less its largest possible jitter is planned regardless of
        // which four-block square it falls in - the coastline is ragged, the apron is not holed
        final int guaranteed = settings.bandWidth(1, 0) - settings.edgeJitter();

        for (int x = 7; x <= 6 + guaranteed; x++)
        {
            assertTrue(GroundGrid.planned(plan, x, 0), "the apron should be solid out to " + (6 + guaranteed));
        }
    }

    @Test
    @DisplayName("growth off means growth off: no plan at all, whatever the rank")
    void growthCanBeSwitchedOff()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 6, 40);
        TerrainGrowthSettings off = new TerrainGrowthSettings(false, 18, 12, 6, 3, 3, 4, 3, 4);

        assertTrue(ApronPlanner.plan(survey, off, 5, 0, 104, null, SEED).isEmpty());
    }

    @Test
    @DisplayName("a rank already grown plans nothing, so a trigger delivered twice costs nothing")
    void aRepeatedTriggerPlansNothing()
    {
        GroundSurvey survey = GroundGrid.island(FLOOR, 6, 40);

        assertTrue(ApronPlanner.plan(survey, SMOOTH, 2, 2, 56, null, SEED).isEmpty());
    }

    @Test
    @DisplayName("ground reach is the half-extent the island actually occupies, not the one it may")
    void groundReachMeasuresWhatIsThere()
    {
        assertEquals(4, ApronPlanner.groundReach(GroundGrid.island(FLOOR, 4, 20)));
        assertEquals(0, ApronPlanner.groundReach(GroundGrid.island(FLOOR, 0, 20)));
    }

    private static ApronPlan.Column columnAt(ApronPlan plan, int x, int z)
    {
        return plan.columns().stream()
                .filter(column -> column.x() == x && column.z() == z)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no apron planned at " + x + "," + z));
    }
}
