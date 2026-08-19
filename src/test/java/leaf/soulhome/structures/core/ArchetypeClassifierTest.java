/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scores realistic rooms against the archetype definitions the mod actually ships.
 */
class ArchetypeClassifierTest
{
    private static List<ArchetypeDefinition> shipped;
    private static ArchetypeClassifier classifier;
    private static Predicate<BlockSignature> signals;

    @BeforeAll
    static void loadShippedArchetypes() throws IOException
    {
        shipped = ArchetypeJsonReader.shipped();
        classifier = new ArchetypeClassifier(shipped);
        signals = ArchetypeSignals.filterFor(shipped);
    }

    private static ClassificationResult classifyOnly(GridVolume volume)
    {
        List<SoulRegion> regions = RegionScanner.scan(volume, signals, ScanSettings.DEFAULTS);
        assertEquals(1, regions.size(), "expected exactly one region in this layout");
        return classifier.classify(regions.get(0));
    }

    // region shipped definitions

    @Test
    @DisplayName("every shipped archetype is valid and reachable")
    void shippedArchetypesAreValid()
    {
        assertEquals(10, shipped.size(),
                "alchemy lab, armoury, bedchamber, enchanting room, farm, hearth, library, mine, "
                        + "track, training yard");

        for (ArchetypeDefinition archetype : shipped)
        {
            assertEquals(List.of(), archetype.validationErrors(), archetype.id() + " should be valid");
            assertFalse(archetype.buffs().isEmpty(), archetype.id() + " should grant something");
            assertEquals(1, archetype.tierFor(archetype.tiers().get(0).minScore()),
                    archetype.id() + " should award tier 1 at its own first threshold");
        }
    }

    @Test
    @DisplayName("every shipped archetype grants a buff something is registered to apply")
    void shippedBuffTypesAreImplemented()
    {
        // a buff id is a string in the archetype JSON and again in the effect that applies it.
        // Get one of them wrong and the room classifies perfectly and then does nothing at all,
        // which is a miserable thing to debug from a bug report.
        for (ArchetypeDefinition archetype : shipped)
        {
            for (ArchetypeDefinition.BuffSpec buff : archetype.buffs())
            {
                assertTrue(SoulBuffTypes.BUILT_IN.contains(buff.type()),
                        archetype.id() + " grants '" + buff.type()
                                + "', which is not one of " + SoulBuffTypes.BUILT_IN);
            }
        }
    }

    @Test
    @DisplayName("every built-in buff is reachable from some shipped archetype")
    void everyBuiltInBuffIsGrantedBySomething()
    {
        Set<String> granted = new HashSet<>();

        for (ArchetypeDefinition archetype : shipped)
        {
            for (ArchetypeDefinition.BuffSpec buff : archetype.buffs())
            {
                granted.add(buff.type());
            }
        }

        assertEquals(SoulBuffTypes.BUILT_IN, granted,
                "an implemented buff nothing can grant is dead code");
    }

    @Test
    @DisplayName("shipped archetypes prefer tags over specific blocks")
    void shippedArchetypesLeanOnTags()
    {
        // the single biggest lever on whether the system feels creative or feels like a checklist:
        // matching #minecraft:bookshelves rather than minecraft:bookshelf means chiselled and
        // modded variants count for free
        int taggedSignals = 0;
        int totalSignals = 0;

        for (ArchetypeDefinition archetype : shipped)
        {
            for (ArchetypeDefinition.Signal signal : archetype.signals())
            {
                totalSignals++;

                if (!signal.match().tags().isEmpty())
                {
                    taggedSignals++;
                }
            }
        }

        assertTrue(taggedSignals * 2 >= totalSignals,
                "at least half of shipped signals should match on tags, got "
                        + taggedSignals + " of " + totalSignals);
    }

    // endregion

    // region the core promise of the design

    @Test
    @DisplayName("a thoughtfully built study classifies as a library")
    void canonicalLibraryClassifies()
    {
        ClassificationResult result = classifyOnly(library());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:library", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a box made of bookshelves does not out-score a real study")
    void bookshelfBoxDoesNotBeatAStudy()
    {
        // this is the whole reason the scoring curve is sublinear. With weight-times-count, the
        // optimal library is the largest possible mass of bookshelves, and the soulhome stops
        // being a personal space and becomes a spreadsheet.
        ArchetypeDefinition libraryArchetype = ArchetypeJsonReader.byId(shipped, "soulhome:library");

        SoulRegion study = onlyRegion(library());
        SoulRegion box = onlyRegion(bookshelfBox());

        double studyScore = classifier.score(study, libraryArchetype).score();
        double boxScore = classifier.score(box, libraryArchetype).score();

        assertTrue(box.allBlocks().count(BlockMatcher.ofTags("minecraft:bookshelves"))
                        > study.allBlocks().count(BlockMatcher.ofTags("minecraft:bookshelves")) * 4,
                "the box really does contain far more bookshelves");

        assertTrue(studyScore > boxScore,
                "variety must beat volume: study scored " + studyScore + ", box scored " + boxScore);

        assertEquals(ClassificationResult.Status.UNCLASSIFIED, classifier.classify(box).status(),
                "and the box should not qualify as anything at all");
    }

    @Test
    @DisplayName("an empty room scores zero and explains why")
    void emptyRoomScoresZero()
    {
        ClassificationResult result = classifyOnly(emptyRoom());

        assertEquals(ClassificationResult.Status.UNCLASSIFIED, result.status());

        for (ArchetypeScore score : result.allScores())
        {
            assertEquals(0d, score.score(), 1e-9, score.archetypeId() + " should score zero");
        }

        // "nothing happened" is the failure mode that kills a fuzzy classifier, so the reason has
        // to be available to the feedback UX rather than reconstructed later
        ArchetypeScore libraryScore = scoreFor(result, "soulhome:library");
        assertFalse(libraryScore.failedRequirements().isEmpty());
        assertEquals("needs 16 of #minecraft:bookshelves, found 0",
                libraryScore.failedRequirements().get(0).toString());
        assertFalse(libraryScore.missingSignals().isEmpty(), "should be able to say what is missing");
    }

    @Test
    @DisplayName("a library with an anvil in it is still a library")
    void libraryWithAnAnvilResolvesSensibly()
    {
        ClassificationResult result = classifyOnly(libraryWithAnvil());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:library", result.awardedArchetypeId().orElseThrow());

        // the anvil should cost it something, though
        double withAnvil = scoreFor(result, "soulhome:library").score();
        double without = classifier.score(onlyRegion(library()),
                ArchetypeJsonReader.byId(shipped, "soulhome:library")).score();

        assertTrue(withAnvil < without, "the detractor should bite: " + withAnvil + " vs " + without);
    }

    @Test
    @DisplayName("an enchanting room is told apart from the library it overlaps with")
    void enchantingRoomBeatsLibraryOnSharedSignals()
    {
        // the hardest pair in the launch set: both are bookshelves and candles in a room. The
        // requirement gate and the detractors are what separate them.
        ClassificationResult result = classifyOnly(enchantingRoom());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:enchanting_room", result.awardedArchetypeId().orElseThrow());

        ArchetypeScore libraryScore = scoreFor(result, "soulhome:library");
        assertTrue(libraryScore.score() > 0, "it is genuinely library-ish, just less so");
        assertFalse(libraryScore.qualifies(), "but not enough to be a contender");
    }

    @Test
    @DisplayName("an open-air farm classifies without ever being a room")
    void openAirFarmClassifies()
    {
        ClassificationResult result = classifyOnly(farm());

        assertEquals(RegionType.OPEN, result.region().type());
        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:farm", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1);
    }

    @Test
    @DisplayName("a brewing room classifies as an alchemy lab")
    void canonicalAlchemyLabClassifies()
    {
        ClassificationResult result = classifyOnly(alchemyLab());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:alchemy_lab", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a room built to sleep in classifies as a bedchamber")
    void canonicalBedchamberClassifies()
    {
        ClassificationResult result = classifyOnly(bedchamber());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:bedchamber", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a worked-out ore face classifies as a mine")
    void canonicalMineClassifies()
    {
        ClassificationResult result = classifyOnly(mine());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:mine", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a bedchamber is not mistaken for the library it borrows its seating from")
    void bedchamberIsNotALibrary()
    {
        // beds and carpets are both in soulhome:seating, so a bedchamber genuinely trips one of
        // the library's signals. The requirement gate is what keeps that from mattering.
        ClassificationResult result = classifyOnly(bedchamber());

        ArchetypeScore libraryScore = scoreFor(result, "soulhome:library");

        assertEquals(0d, libraryScore.score(), 1e-9);
        assertFalse(libraryScore.failedRequirements().isEmpty(), "and it should say which gate it failed");
    }

    @Test
    @DisplayName("a mine full of iron is still not an armoury")
    void mineIsNotAnArmoury()
    {
        ClassificationResult result = classifyOnly(mine());

        ArchetypeScore armouryScore = scoreFor(result, "soulhome:armoury");

        assertEquals(0d, armouryScore.score(), 1e-9, "no forge, no armoury");
    }

    @Test
    @DisplayName("a looping rail line classifies as a track")
    void canonicalTrackClassifies()
    {
        ClassificationResult result = classifyOnly(track());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:track", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a slime-block practice room classifies as a training yard")
    void canonicalTrainingYardClassifies()
    {
        ClassificationResult result = classifyOnly(trainingYard());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:training_yard", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a furnace ringed with netherrack classifies as a hearth")
    void canonicalHearthClassifies()
    {
        ClassificationResult result = classifyOnly(hearth());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertEquals("soulhome:hearth", result.awardedArchetypeId().orElseThrow());
        assertTrue(result.awardedTier() >= 1, "should reach at least tier 1");
    }

    @Test
    @DisplayName("a track full of rails is not mistaken for a mine")
    void trackIsNotAMine()
    {
        // both value the rails tag, but the mine's hard requirement is ore, which a track has none of
        ClassificationResult result = classifyOnly(track());

        ArchetypeScore mineScore = scoreFor(result, "soulhome:mine");

        assertEquals(0d, mineScore.score(), 1e-9);
        assertFalse(mineScore.failedRequirements().isEmpty(), "and it should say which gate it failed");
    }

    // endregion

    // region assignment rules

    @Test
    @DisplayName("two archetypes within the margin leave the region ambiguous")
    void tooCloseToCallIsAmbiguous()
    {
        // synthetic twins, so the margin logic is tested rather than the shipped weights
        ArchetypeDefinition twinA = twin("soulhome:twin_a");
        ArchetypeDefinition twinB = twin("soulhome:twin_b");

        ArchetypeClassifier twins = new ArchetypeClassifier(List.of(twinA, twinB));
        ClassificationResult result = twins.classify(onlyRegion(library()));

        assertEquals(ClassificationResult.Status.AMBIGUOUS, result.status());
        assertTrue(result.awarded().isEmpty(), "an ambiguous region grants nothing");
        assertNotNull(result.runnerUp(), "but it must be able to say what it was torn between");
    }

    @Test
    @DisplayName("being narrowly ahead of a non-contender is not ambiguous")
    void closeButUnqualifiedRunnerUpStillClassifies()
    {
        ClassificationResult result = classifyOnly(library());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertNotNull(result.runnerUp());
        assertFalse(result.runnerUp().qualifies());
    }

    @Test
    @DisplayName("an archetype that will not consider a region says so")
    void wrongRegionTypeIsExplained()
    {
        SoulRegion field = onlyRegion(farm());
        ArchetypeScore libraryScore = classifier.score(field, ArchetypeJsonReader.byId(shipped, "soulhome:library"));

        assertEquals(0d, libraryScore.score(), 1e-9);
        assertNotNull(libraryScore.ineligibleReason());
        assertTrue(libraryScore.ineligibleReason().contains("enclosed"), libraryScore.ineligibleReason());
    }

    @Test
    @DisplayName("the score breakdown adds up")
    void breakdownReconstructsTheScore()
    {
        ArchetypeScore score = classifier.score(onlyRegion(library()),
                ArchetypeJsonReader.byId(shipped, "soulhome:library"));

        double summed = 0d;

        for (ArchetypeScore.SignalContribution contribution : score.contributions())
        {
            summed += contribution.contribution();
        }

        assertEquals(score.rawScore(), summed, 1e-9, "contributions must account for the raw score");
        assertEquals(score.rawScore() * score.diversityMultiplier() * score.densityMultiplier(),
                score.score(), 1e-9, "and the multipliers must account for the rest");
        assertTrue(score.diversityMultiplier() > 1d, "a varied room earns its diversity bonus");
    }

    @Test
    @DisplayName("a signal held back by its cap is flagged as capped")
    void cappedSignalsAreFlagged()
    {
        ArchetypeScore score = classifier.score(onlyRegion(bookshelfBox()),
                ArchetypeJsonReader.byId(shipped, "soulhome:library"));

        ArchetypeScore.SignalContribution bookshelves = score.contributions().get(0);

        assertTrue(bookshelves.isCapped(), "110 bookshelves against a cap of 32");
        assertEquals(32, bookshelves.countedCount());
    }

    // endregion

    // region helpers

    private static SoulRegion onlyRegion(GridVolume volume)
    {
        List<SoulRegion> regions = RegionScanner.scan(volume, signals, ScanSettings.DEFAULTS);
        assertEquals(1, regions.size(), "expected exactly one region in this layout");
        return regions.get(0);
    }

    private static ArchetypeScore scoreFor(ClassificationResult result, String archetypeId)
    {
        for (ArchetypeScore score : result.allScores())
        {
            if (score.archetypeId().equals(archetypeId))
            {
                return score;
            }
        }

        throw new AssertionError("No score recorded for " + archetypeId);
    }

    /** Two of these score identically on any region, which is what the margin rule is for. */
    private static ArchetypeDefinition twin(String id)
    {
        return new ArchetypeDefinition(
                id,
                "archetype.soulhome.twin",
                List.of(RegionType.ENCLOSED),
                1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(
                        BlockMatcher.ofTags("minecraft:bookshelves"), 3.0d, "core", 32)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:nothing", 0.1d, 0.3d)));
    }

    // endregion

    // region layouts - a 7x7x5 shell with a 5x5x3 interior

    private static final String[] SLAB = {
            "#######", "#######", "#######", "#######", "#######", "#######", "#######"};

    private static GridVolume library()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#BBBBB#",
                        "#S...S#",
                        "#.....#",
                        "#..L..#",
                        "#.....#",
                        "#S...S#",
                        "#BBBBB#"},
                new String[]{
                        "#BBBBB#",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#BBBBB#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume libraryWithAnvil()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#BBBBB#",
                        "#S...S#",
                        "#.....#",
                        "#..L.A#",
                        "#.....#",
                        "#S...S#",
                        "#BBBBB#"},
                new String[]{
                        "#BBBBB#",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#BBBBB#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume enchantingRoom()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#BBBBB#",
                        "#o...o#",
                        "#.....#",
                        "#..E..#",
                        "#.....#",
                        "#o...o#",
                        "#BBBBB#"},
                new String[]{
                        "#BBBBB#",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#BBBBB#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume emptyRoom()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#######",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#######"},
                new String[]{
                        "#######",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#######"},
                new String[]{
                        "#######",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#######"},
                SLAB);
    }

    private static GridVolume bookshelfBox()
    {
        String[] shelfSlab = {
                "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB", "BBBBBBB"};
        String[] shelfWalls = {
                "BBBBBBB",
                "B.....B",
                "B.....B",
                "B.....B",
                "B.....B",
                "B.....B",
                "BBBBBBB"};

        return GridVolume.of(shelfSlab, shelfWalls, shelfWalls, shelfWalls, shelfSlab);
    }

    private static GridVolume alchemyLab()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#sssss#",
                        "#u...u#",
                        "#.....#",
                        "#..p..#",
                        "#.....#",
                        "#u...b#",
                        "#sssss#"},
                new String[]{
                        "#sssss#",
                        "#c...c#",
                        "#.rrr.#",
                        "#.rrr.#",
                        "#.rrr.#",
                        "#c...c#",
                        "#sssss#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume bedchamber()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#WWWWW#",
                        "#d...d#",
                        "#.....#",
                        "#..j..#",
                        "#.....#",
                        "#b...x#",
                        "#WWWWW#"},
                new String[]{
                        "#WWWWW#",
                        "#c...c#",
                        "#.xxx.#",
                        "#.xxx.#",
                        "#.xxx.#",
                        "#c...c#",
                        "#WWWWW#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume mine()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#OOOOO#",
                        "#l...l#",
                        "#.....#",
                        "#..Y..#",
                        "#.....#",
                        "#b...l#",
                        "#OOOOO#"},
                new String[]{
                        "#OOOOO#",
                        "#t...t#",
                        "#.===.#",
                        "#.===.#",
                        "#.===.#",
                        "#t...t#",
                        "#OOOOO#"},
                new String[]{
                        "#######",
                        "#t...t#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#t...t#",
                        "#######"},
                SLAB);
    }

    private static GridVolume track()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#FFFFF#",
                        "#=====#",
                        "#.....#",
                        "#=====#",
                        "#.....#",
                        "#=====#",
                        "#FFFFF#"},
                new String[]{
                        "#FFFFF#",
                        "#I...I#",
                        "#.....#",
                        "#..h..#",
                        "#.....#",
                        "#I...I#",
                        "#FFFFF#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume trainingYard()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#hhhhh#",
                        "#l...l#",
                        "#.....#",
                        "#..M..#",
                        "#.....#",
                        "#l...l#",
                        "#hhhhh#"},
                new String[]{
                        "#hhhhh#",
                        "#c...c#",
                        "#.aaa.#",
                        "#.aaa.#",
                        "#.aaa.#",
                        "#c...c#",
                        "#hhhhh#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume hearth()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#kkkkk#",
                        "#v...v#",
                        "#.....#",
                        "#..N..#",
                        "#.....#",
                        "#v...v#",
                        "#kkkkk#"},
                new String[]{
                        "#kkkkk#",
                        "#c...c#",
                        "#.mmm.#",
                        "#.mmm.#",
                        "#.mmm.#",
                        "#c...c#",
                        "#kkkkk#"},
                new String[]{
                        "#######",
                        "#c...c#",
                        "#.....#",
                        "#.....#",
                        "#.....#",
                        "#c...c#",
                        "#######"},
                SLAB);
    }

    private static GridVolume farm()
    {
        return GridVolume.of(
                new String[]{
                        "fffffff",
                        "fffffff",
                        "fffffff",
                        "fff~fff",
                        "fffffff",
                        "fffffff",
                        "fffffff"},
                new String[]{
                        "Cwwwwwb",
                        "wwwwwww",
                        "wwwwwww",
                        "www.www",
                        "wwwwwww",
                        "wwwwwww",
                        "hwwwwwh"});
    }

    // endregion
}
