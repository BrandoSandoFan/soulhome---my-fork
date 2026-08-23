/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcrossClauseTest
{
    private static final BlockMatcher CHAIR = BlockMatcher.ofBlocks("test:chair");
    private static final BlockMatcher LECTERN = BlockMatcher.ofBlocks("test:lectern");
    private static final Map<String, BlockMatcher> ELEMENTS = Map.of("chair", CHAIR, "lectern", LECTERN);

    private static final AcrossClauseType TYPE = new AcrossClauseType();

    private static FormClause across(int minDistance, int maxDistance, boolean requireClear)
    {
        return TYPE.create(ClauseParams.builder()
                .put("of", "chair").put("to", "lectern")
                .put("min_distance", minDistance).put("max_distance", maxDistance)
                .put("axes", "x,z").put("require_clear", requireClear ? 1 : 0).put("coverage", 1.0)
                .build());
    }

    private static BlockSignature block(String id)
    {
        return new TestBlocks.TestBlock(id, Set.of(), Passability.PASSABLE);
    }

    // region clause-level (hand-built geometry)

    @Test
    @DisplayName("lectern-gap-chair scores 1.0")
    void gapScoresFull()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 0, block("test:chair"))
                .build();

        assertEquals(1.0, across(2, 4, true).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a chair jammed against the lectern scores 0")
    void jammedScoresZero()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(1, 0, 0, block("test:chair"))
                .build();

        assertEquals(0.0, across(2, 4, true).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a chair with a wall in the gap scores 0")
    void wallInTheGapScoresZero()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 0, block("test:chair"))
                .addBlocked(2, 0, 0)
                .build();

        FormResult result = across(2, 4, true).evaluate(geometry, ELEMENTS);
        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("way"), result.diagnostic());
    }

    @Test
    @DisplayName("require_clear: false ignores the same wall and scores 1.0")
    void requireClearFalseIgnoresTheWall()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 0, block("test:chair"))
                .addBlocked(2, 0, 0)
                .build();

        assertEquals(1.0, across(2, 4, false).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a chair one cell off-axis still scores well")
    void offAxisStillScoresWell()
    {
        RegionGeometry onAxis = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 0, block("test:chair"))
                .build();

        RegionGeometry offAxis = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 1, block("test:chair"))
                .build();

        double onAxisConfidence = across(2, 4, true).evaluate(onAxis, ELEMENTS).confidence();
        double offAxisConfidence = across(2, 4, true).evaluate(offAxis, ELEMENTS).confidence();

        assertEquals(1.0, onAxisConfidence, 1e-9);
        assertTrue(offAxisConfidence > 0.3, "one cell off axis should still score well, was " + offAxisConfidence);
        assertTrue(offAxisConfidence < onAxisConfidence, "still worse than perfect alignment");
    }

    @Test
    @DisplayName("two cells off-axis is not considered aligned at all")
    void twoCellsOffAxisDoesNotCount()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 2, block("test:chair"))
                .build();

        assertEquals(0.0, across(2, 4, true).evaluate(geometry, ELEMENTS).confidence(), 1e-9);
    }

    @Test
    @DisplayName("needsClearance is true only when require_clear is set")
    void needsClearanceMirrorsRequireClear()
    {
        assertTrue(across(2, 4, true).needsClearance());
        assertFalse(across(2, 4, false).needsClearance());
    }

    @Test
    @DisplayName("an absent 'of' or 'to' scores 0 and names which side is missing")
    void absentElementNamesTheMissingSide()
    {
        RegionGeometry geometry = RegionGeometry.builder(100).add(0, 0, 0, block("test:lectern")).build();

        FormResult result = across(2, 4, true).evaluate(geometry, Map.of("lectern", LECTERN));

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().contains("'chair'"), result.diagnostic());
    }

    @Test
    @DisplayName("1 of 3 chairs face the lectern across clear space - the others are against it")
    void partialCoverageDiagnosticNamesCounts()
    {
        RegionGeometry geometry = RegionGeometry.builder(100)
                .add(0, 0, 0, block("test:lectern"))
                .add(3, 0, 0, block("test:chair"))   // clear, in range: satisfied
                .add(1, 0, 0, block("test:chair"))   // jammed against it: too close
                .add(1, 5, 0, block("test:chair"))   // far away on an unrelated axis: too close/none
                .build();

        FormResult result = across(2, 4, true).evaluate(geometry, ELEMENTS);
        assertTrue(result.diagnostic().contains("1 of 3"), result.diagnostic());
    }

    // endregion

    // region end-to-end through RegionScanner (proves the passability -> RegionGeometry.isBlocked plumbing)

    private static final Predicate<BlockSignature> FURNITURE_FILTER = signature ->
            signature.id().equals("test:lectern") || signature.id().equals("test:chair");

    /**
     * A room where the straight line between a lectern and a chair runs through a stone pillar,
     * but the room stays open on either side of it so the whole interior remains one region.
     */
    private static GridVolume roomWithObstruction()
    {
        Map<Character, TestBlocks.TestBlock> palette = GridVolume.defaultPalette();
        palette.put('l', new TestBlocks.TestBlock("test:lectern", Set.of(), Passability.BLOCKING));
        palette.put('c', new TestBlocks.TestBlock("test:chair", Set.of(), Passability.BLOCKING));

        return GridVolume.of(palette,
                new String[]{"#######", "#######", "#######", "#######", "#######"},
                new String[]{
                        "#######",
                        "#.....#",
                        "#l.#.c#",
                        "#.....#",
                        "#######"},
                new String[]{"#######", "#######", "#######", "#######", "#######"});
    }

    @Test
    @DisplayName("end-to-end: RegionScanner threads clearance through to RegionGeometry.isBlocked, so across sees the pillar")
    void endToEndClearanceThroughRegionScanner()
    {
        List<SoulRegion> withClearance = RegionScanner.scan(
                roomWithObstruction(), null, FURNITURE_FILTER, true, ScanSettings.DEFAULTS);

        assertEquals(1, withClearance.size());
        SoulRegion region = withClearance.get(0);

        FormResult blocked = across(2, 4, true).evaluate(region.geometry(), ELEMENTS);
        assertEquals(0.0, blocked.confidence(), 1e-9, "the pillar sits directly on the straight line between them");

        FormResult ignoringWalls = across(2, 4, false).evaluate(region.geometry(), ELEMENTS);
        assertEquals(1.0, ignoringWalls.confidence(), 1e-9, "require_clear: false must not consult isBlocked at all");
    }

    @Test
    @DisplayName("end-to-end: without indexClearance, no blocked data is tracked, so require_clear cannot see the pillar")
    void endToEndWithoutClearanceFlagSeesNothingBlocked()
    {
        List<SoulRegion> withoutClearance = RegionScanner.scan(
                roomWithObstruction(), null, FURNITURE_FILTER, false, ScanSettings.DEFAULTS);

        SoulRegion region = withoutClearance.get(0);
        assertFalse(region.geometry().isBlocked(3, 1, 2), "clearance was never asked for, so nothing was ever recorded");

        FormResult result = across(2, 4, true).evaluate(region.geometry(), ELEMENTS);
        assertEquals(1.0, result.confidence(), 1e-9, "absent clearance data reads as 'not blocked', not as a failure");
    }

    // endregion
}
