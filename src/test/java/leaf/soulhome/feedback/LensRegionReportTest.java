/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.BlockCounts;
import leaf.soulhome.structures.core.BuffBreakdown;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.RegionType;
import leaf.soulhome.structures.core.SoulBuffSet;
import leaf.soulhome.structures.core.SoulRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #50: the Soul Lens screen reads straight off {@link LensRegionReport}, so this is the class that
 * has to agree with {@link SoulReport} - the chat command's own explanation - about what a region
 * scored and why. Every case here has a {@code SoulReportTest}-equivalent: the same three
 * classification outcomes {@link RegionHighlightTest} already covers for the world outlines, one
 * level deeper.
 */
class LensRegionReportTest
{
    @Test
    @DisplayName("a CLASSIFIED region carries its tier, its signals, and the buffs its archetype grants")
    void classifiedCarriesTheFullBreakdown()
    {
        ArchetypeScore.SignalContribution signal =
                new ArchetypeScore.SignalContribution("Bookshelf", "furnishing", 1d, 16, 16, 8.5d);
        ArchetypeScore.SignalContribution missingSignal =
                new ArchetypeScore.SignalContribution("Lectern", "reading", 1d, 0, 0, 0d);

        ArchetypeScore best = new ArchetypeScore(
                "soulhome:library", "archetype.soulhome.library", 42d, 42d, 1.2d, 1d, 2,
                OptionalDouble.of(8d), List.of(signal), List.of(missingSignal), List.of(), null,
                List.of(), List.of(), false);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.CLASSIFIED, best, null, List.of(best));

        BuffBreakdown breakdown = new BuffBreakdown(
                SoulBuffSet.of(java.util.Map.of("soulhome:xp_gain", 0.2d)),
                List.of(new BuffBreakdown.Source("soulhome:xp_gain", "soulhome:library", "archetype.soulhome.library", 1, 2, 0.2d)));

        LensRegionReport report = LensRegionReport.of(result, 0, breakdown);

        assertTrue(report.isClassified());
        assertFalse(report.isAmbiguous());
        assertFalse(report.noArchetypes());
        assertEquals("soulhome:library", report.archetypeId());
        assertEquals(2, report.tier());
        assertEquals(42d, report.score());
        assertTrue(report.hasNextTier());
        assertEquals(8d, report.scoreToNextTier());
        assertEquals(1, report.matched().size());
        assertEquals("Bookshelf", report.matched().get(0).description());
        assertFalse(report.matched().get(0).isCapped());
        assertEquals(List.of("Lectern"), report.missing());
        assertEquals(1, report.buffs().size());
        assertEquals("soulhome:xp_gain", report.buffs().get(0).buffType());
    }

    @Test
    @DisplayName("an AMBIGUOUS region carries the runner-up, and is awarded no buffs")
    void ambiguousCarriesTheRunnerUpAndNoBuffs()
    {
        ArchetypeScore best = score("soulhome:library", 30d, 1);
        ArchetypeScore runnerUp = score("soulhome:enchanting_room", 29d, 1);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.AMBIGUOUS, best, runnerUp, List.of(best, runnerUp));

        LensRegionReport report = LensRegionReport.of(result, 0, BuffBreakdown.EMPTY);

        assertTrue(report.isAmbiguous());
        assertTrue(report.hasRunnerUp());
        assertEquals("archetype.soulhome.enchanting_room", report.runnerUpDisplayName());
        assertEquals(29d, report.runnerUpScore());
        assertTrue(report.buffs().isEmpty(), "ambiguous is not an award, so it grants nothing");
    }

    @Test
    @DisplayName("a signal capped short of its full count is reported as capped")
    void cappedSignalIsFlagged()
    {
        ArchetypeScore.SignalContribution capped =
                new ArchetypeScore.SignalContribution("Torch", "lighting", 0.5d, 40, 20, 5d);

        ArchetypeScore best = new ArchetypeScore(
                "soulhome:hearth", "archetype.soulhome.hearth", 20d, 20d, 1d, 1d, 1,
                OptionalDouble.empty(), List.of(capped), List.of(), List.of(), null, List.of(), List.of(), false);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.CLASSIFIED, best, null, List.of(best));

        LensRegionReport report = LensRegionReport.of(result, 0, BuffBreakdown.EMPTY);

        assertTrue(report.matched().get(0).isCapped());
        assertFalse(report.hasNextTier(), "no next tier means the sentinel, not a stray zero");
    }

    @Test
    @DisplayName("no archetypes loaded is carried as its own flag, not as an unclassified region")
    void noArchetypesIsItsOwnCase()
    {
        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.UNCLASSIFIED, null, null, List.of());

        LensRegionReport report = LensRegionReport.of(result, 0, BuffBreakdown.EMPTY);

        assertTrue(report.noArchetypes());
        assertFalse(report.isClassified());
        assertTrue(report.matched().isEmpty());
    }

    @Test
    @DisplayName("an arrangement clause tree flattens hit and missed leaves with their +/- sign")
    void arrangementFlattensHitAndMissedClauses()
    {
        ArchetypeScore.ClauseEvaluation hitLeaf =
                new ArchetypeScore.ClauseEvaluation("loop", "rails form a loop", 0.8d, "", List.of(), -1);
        ArchetypeScore.ClauseEvaluation missLeaf =
                new ArchetypeScore.ClauseEvaluation("above", "ice above the rails", 0d, "not found", List.of(), -1);
        ArchetypeScore.ClauseEvaluation all = new ArchetypeScore.ClauseEvaluation(
                "all", "", 0.4d, "", List.of(hitLeaf, missLeaf), -1);

        ArchetypeScore.StructureContribution hit =
                new ArchetypeScore.StructureContribution("circuit", "circuit", 8d, 0.4d, 3.2d, all);

        ArchetypeScore best = new ArchetypeScore(
                "soulhome:track", "archetype.soulhome.track", 20d, 20d, 1d, 1d, 1,
                OptionalDouble.empty(), List.of(), List.of(), List.of(), null, List.of(hit), List.of(), false);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.CLASSIFIED, best, null, List.of(best));

        LensRegionReport report = LensRegionReport.of(result, 0, BuffBreakdown.EMPTY);

        assertEquals(1, report.forms().size());
        LensRegionReport.Form form = report.forms().get(0);
        assertTrue(form.credited());
        assertEquals(2, form.clauses().size());
        assertTrue(form.clauses().get(0).hit());
        assertFalse(form.clauses().get(1).hit());
    }

    private static ArchetypeScore score(String archetypeId, double value, int tier)
    {
        return new ArchetypeScore(
                archetypeId, "archetype." + archetypeId.replace(':', '.'), value, value, 1d, 1d, tier,
                OptionalDouble.empty(), List.of(), List.of(), List.of(), null, List.of(), List.of(), false);
    }

    private static SoulRegion plainRegion()
    {
        return SoulRegion.create(
                RegionType.ENCLOSED, new RegionBounds(0, 0, 0, 3, 3, 3), BlockCounts.empty(), BlockCounts.empty(), 27);
    }
}
