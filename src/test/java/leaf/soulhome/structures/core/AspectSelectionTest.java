/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aspect selection is a comparison, never a weighting - #173 of the Aspects epic (#171), and the
 * rule most likely to be broken by accident, because it runs against how everything else here works.
 *
 * <p>{@link #closeAspectsPayExactlyWhatFarApartOnesPay()} is the case the issue was written around
 * and the one to read first: if it ever fails, someone has let aspect support reach a magnitude, and
 * a player who built a library with both a fine archive and a fine scriptorium in it is being
 * charged for the half they did not win.
 */
class AspectSelectionTest
{
    private static final BlockMatcher BOOKSHELVES = BlockMatcher.ofTags("soulhome:bookshelves");
    private static final BlockMatcher LECTERN = BlockMatcher.ofBlocks("minecraft:lectern");

    @Test
    @DisplayName("two rooms of the same score pay the same, whether their aspects are 0.9/0.7 apart or 0.999/0.99 together")
    void closeAspectsPayExactlyWhatFarApartOnesPay()
    {
        // #173's worked table, with the two support figures produced by the leans rather than
        // asserted directly: room A leans hard toward X, room B leans nearly equally toward both.
        // The room itself is identical in both, so the magnitude has to be identical too.
        SoulRegion room = region(16, 4);

        ArchetypeDefinition farApart = archetype(
                lean(BOOKSHELVES, 0.9d), lean(LECTERN, 0.7d));
        ArchetypeDefinition together = archetype(
                lean(BOOKSHELVES, 0.999d), lean(LECTERN, 0.99d));

        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());

        ArchetypeScore a = classifier.score(room, farApart);
        ArchetypeScore b = classifier.score(room, together);

        assertEquals(a.score(), b.score(), 1e-9, "the aspect balance must not touch the score");
        assertEquals("archive", a.aspectId());
        assertEquals("archive", b.aspectId());

        assertEquals(
                magnitudeOf(a, farApart),
                magnitudeOf(b, together),
                1e-9,
                "both rooms take the archive and both pay it at their own, identical, magnitude");
    }

    @Test
    @DisplayName("a room whose contents lean to the alternative takes it, at the same score it would have had either way")
    void theLeadingAspectTakesTheRoom()
    {
        ArchetypeDefinition archetype = archetype(lean(BOOKSHELVES, 1d), lean(LECTERN, 4d));
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());

        ArchetypeScore leaningToTheDefault = classifier.score(region(32, 0), archetype);
        ArchetypeScore leaningToTheOther = classifier.score(region(32, 4), archetype);

        assertEquals("archive", leaningToTheDefault.aspectId());
        assertEquals("scriptorium", leaningToTheOther.aspectId());
        assertFalse(leaningToTheOther.aspect().takenIsDefault());
    }

    @Test
    @DisplayName("the default keeps a room a challenger leads by less than the margin")
    void theDefaultHoldsInsideTheMargin()
    {
        // both leans weigh the same and the counts are equal, so the challenger is level: inside
        // the margin by construction, whatever the margin happens to be set to
        ArchetypeDefinition archetype = archetype(lean(BOOKSHELVES, 1d), lean(LECTERN, 1d));

        AspectSelection selection = AspectSelector.select(region(9, 9), archetype, ScoringSettings.DEFAULTS);

        assertEquals("archive", selection.takenId());
        assertTrue(selection.takenIsDefault());
    }

    @Test
    @DisplayName("with the margin at 1 the challenger takes a room it merely leads")
    void marginOfOneIsAStraightContest()
    {
        // a 10% lead: ahead, and inside the default 15% margin. The whole question this test asks
        // is which of those two facts decides the room
        ArchetypeDefinition archetype = archetype(lean(BOOKSHELVES, 1d), lean(LECTERN, 1.1d));
        ScoringSettings straight = settingsWithMargin(1d);

        assertEquals("scriptorium", AspectSelector.select(region(9, 9), archetype, straight).takenId());
        assertEquals("archive", AspectSelector.select(region(9, 9), archetype, ScoringSettings.DEFAULTS).takenId(),
                "the same room at the default margin stays with the default");
    }

    @Test
    @DisplayName("a room scoring the same takes the same aspect every time, on every run")
    void selectionIsDeterministic()
    {
        ArchetypeDefinition archetype = archetype(lean(BOOKSHELVES, 1d), lean(LECTERN, 4d));
        SoulRegion room = region(16, 4);

        List<String> taken = new ArrayList<>();

        for (int run = 0; run < 20; run++)
        {
            taken.add(AspectSelector.select(room, archetype, ScoringSettings.DEFAULTS).takenId());
        }

        assertEquals(1, Set.copyOf(taken).size(), "the aspect flapped between runs: " + Set.copyOf(taken));
    }

    @Test
    @DisplayName("an archetype with no aspects takes no new code path and reports nothing")
    void noAspectsMeansNoSelection()
    {
        ArchetypeDefinition plain = new ArchetypeDefinition(
                "soulhome:plain", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BOOKSHELVES, 3d, "core", 32)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:xp_gain", 0.1d, 0.3d)),
                List.of());

        assertNull(AspectSelector.select(region(16, 4), plain, ScoringSettings.DEFAULTS));
        assertNull(new ArchetypeClassifier(List.of()).score(region(16, 4), plain).aspect());
    }

    @Test
    @DisplayName("a gated region is not asked what it is for")
    void gatedRegionsSkipSelection()
    {
        ArchetypeDefinition tooBig = new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1000,
                List.of(),
                List.of(
                        new ArchetypeDefinition.Signal(BOOKSHELVES, 3d, "core", 32),
                        new ArchetypeDefinition.Signal(LECTERN, 5d, "core", 4)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:xp_gain", 0.1d, 0.3d)),
                List.of(), List.of(),
                List.of(
                        new Aspect("archive", "aspect.soulhome.test.archive", true,
                                List.of(new Aspect.Lean(BOOKSHELVES, 1d)), List.of(), List.of()),
                        new Aspect("scriptorium", "aspect.soulhome.test.scriptorium", false,
                                List.of(new Aspect.Lean(LECTERN, 4d)), List.of(), List.of())));

        ArchetypeScore score = new ArchetypeClassifier(List.of()).score(region(16, 4), tooBig);

        assertTrue(score.isGated());
        assertNull(score.aspect());
    }

    @Test
    @DisplayName("the runner-up is named, with what would tip the room to it, in blocks")
    void theNearMissIsReported()
    {
        ArchetypeDefinition archetype = archetype(lean(BOOKSHELVES, 1d), lean(LECTERN, 1d));
        AspectSelection selection = AspectSelector.select(region(16, 1), archetype, ScoringSettings.DEFAULTS);

        assertEquals("archive", selection.takenId());
        assertNotNull(selection.runnerUp());
        assertEquals("scriptorium", selection.runnerUp().aspectId());

        AspectSelection.Tip tip = selection.tip();
        assertNotNull(tip, "a room one block short of a scriptorium should be told so");
        assertEquals("scriptorium", tip.aspectId());
        assertEquals("minecraft:lectern", tip.blockDescription());
        assertTrue(tip.blocksNeeded() >= 1);

        // and the tip is honest: adding exactly what it asks for does hand the room over
        SoulRegion tipped = region(16, 1 + tip.blocksNeeded());
        assertEquals("scriptorium", AspectSelector.select(tipped, archetype, ScoringSettings.DEFAULTS).takenId());
    }

    @Test
    @DisplayName("an aspect's own arrangement tips it without adding anything to the room's score")
    void aspectFormsAreTheAspectsAlone()
    {
        Form rows = new Form(
                "rows", 8d, "structure", Map.of("x", LECTERN), FakeClause.of(1d), Set.of("x"));

        ArchetypeDefinition withRows = new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(
                        new ArchetypeDefinition.Signal(BOOKSHELVES, 3d, "core", 32),
                        new ArchetypeDefinition.Signal(LECTERN, 5d, "core", 4)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:xp_gain", 0.1d, 0.3d)),
                List.of(), List.of(),
                List.of(
                        new Aspect("archive", "aspect.soulhome.test.archive", true,
                                List.of(new Aspect.Lean(BOOKSHELVES, 1d)), List.of(), List.of()),
                        new Aspect("scriptorium", "aspect.soulhome.test.scriptorium", false,
                                List.of(new Aspect.Lean(LECTERN, 1d)), List.of(rows),
                                List.of(new ArchetypeDefinition.BuffSpec("soulhome:enchantment_power", 1d, 3d)))));

        ArchetypeDefinition withoutRows = archetype(lean(BOOKSHELVES, 1d), lean(LECTERN, 1d));
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());
        SoulRegion room = region(16, 1);

        // the rows are worth 8 of support, which is plenty to take the room from the archive
        assertEquals("archive", classifier.score(room, withoutRows).aspectId());
        assertEquals("scriptorium", classifier.score(room, withRows).aspectId());

        // and none of it reached the score, which is the whole point of an aspect form being
        // the aspect's own: a library is expected to hold a lectern, not to hold them in rows
        assertEquals(
                classifier.score(room, withoutRows).score(),
                classifier.score(room, withRows).score(),
                1e-9);
        assertEquals(List.of(), classifier.score(room, withRows).structuralContributions());
    }

    /** What the room would actually be paid, so a test about payout asserts on the payout. */
    private static double magnitudeOf(ArchetypeScore score, ArchetypeDefinition archetype)
    {
        return archetype.buffsFor(score.aspectId()).get(0)
                .magnitudeAt(score.score(), archetype, BuffSettings.DEFAULTS);
    }

    private static Aspect.Lean lean(BlockMatcher match, double weight)
    {
        return new Aspect.Lean(match, weight);
    }

    /**
     * A library with two aspects: the default archive leaning on shelves, the scriptorium on
     * lecterns. Both blocks are the archetype's own signals, as rule 2 requires.
     */
    private static ArchetypeDefinition archetype(Aspect.Lean archiveLean, Aspect.Lean scriptoriumLean)
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(
                        new ArchetypeDefinition.Signal(BOOKSHELVES, 3d, "core", 32),
                        new ArchetypeDefinition.Signal(LECTERN, 5d, "core", 4)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(0.1d, 1), new ArchetypeDefinition.Tier(40d, 2)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:xp_gain", 0.1d, 0.3d)),
                List.of(), List.of(),
                List.of(
                        new Aspect("archive", "aspect.soulhome.test.archive", true,
                                List.of(archiveLean), List.of(), List.of()),
                        new Aspect("scriptorium", "aspect.soulhome.test.scriptorium", false,
                                List.of(scriptoriumLean), List.of(),
                                List.of(new ArchetypeDefinition.BuffSpec("soulhome:enchantment_power", 1d, 3d)))));
    }

    private static ScoringSettings settingsWithMargin(double margin)
    {
        ScoringSettings defaults = ScoringSettings.DEFAULTS;

        return new ScoringSettings(
                defaults.diversityBonusPerRole(), defaults.densityFloor(), defaults.minDensityFactor(),
                defaults.ambiguityMargin(), defaults.structuralShareCap(), defaults.structuralRoleThreshold(),
                defaults.bondShareCap(), true, margin);
    }

    private static SoulRegion region(int shelves, int lecterns)
    {
        BlockCounts.Builder contents = BlockCounts.builder();
        contents.add(TestBlocks.BOOKSHELF, shelves);
        contents.add(TestBlocks.LECTERN, lecterns);

        return SoulRegion.create(
                RegionType.ENCLOSED,
                new RegionBounds(0, 0, 0, 6, 4, 6),
                BlockCounts.empty(),
                contents.build(),
                64);
    }
}
