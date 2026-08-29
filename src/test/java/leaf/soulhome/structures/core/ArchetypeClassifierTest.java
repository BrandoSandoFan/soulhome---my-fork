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
import java.util.Map;
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
    private static Predicate<BlockSignature> geometry;

    @BeforeAll
    static void loadShippedArchetypes() throws IOException
    {
        shipped = ArchetypeJsonReader.shipped();
        classifier = new ArchetypeClassifier(shipped);
        signals = ArchetypeSignals.filterFor(shipped);
        geometry = ArchetypeSignals.geometryFilterFor(shipped);
    }

    private static ClassificationResult classifyOnly(GridVolume volume)
    {
        List<SoulRegion> regions = RegionScanner.scan(volume, signals, geometry, ScanSettings.DEFAULTS);
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

        ArchetypeScore studyScore = classifier.score(study, libraryArchetype);
        ArchetypeScore boxScore = classifier.score(box, libraryArchetype);

        assertTrue(box.allBlocks().count(BlockMatcher.ofTags("minecraft:bookshelves"))
                        > study.allBlocks().count(BlockMatcher.ofTags("minecraft:bookshelves")) * 4,
                "the box really does contain far more bookshelves");

        assertTrue(studyScore.score() > boxScore.score(),
                "variety must beat volume: study scored " + studyScore.score() + ", box scored " + boxScore.score());

        // #38 lowered the tier-1 bar so a bare pile of the defining blocks counts weakly, and a
        // wall of bookshelves with nothing else is exactly that kind of pile in a different shape -
        // it should just barely qualify rather than being shut out entirely
        assertEquals(ClassificationResult.Status.CLASSIFIED, classifier.classify(box).status());
        assertEquals(1, boxScore.tier(), "a pile of bookshelves alone should not reach past tier 1");
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
    @DisplayName("a room lit with redstone lamps scores the same light contribution as the same room lit with candles - #45")
    void redstoneLampsCountAsLighting()
    {
        ArchetypeScore litWithCandles = classifyOnly(hearth(TestBlocks.CANDLE)).best();
        ArchetypeScore litWithLamps = classifyOnly(hearth(TestBlocks.REDSTONE_LAMP)).best();

        assertEquals("soulhome:hearth", litWithLamps.archetypeId());
        assertEquals(litWithCandles.score(), litWithLamps.score(), 1.0e-9,
                "redstone lamps should count as lighting exactly like candles do, not score zero for it");
    }

    @Test
    @DisplayName("the hearth's new comfort, storage and cooking signals fire on their blocks - #48")
    void hearthRewardsComfortStorageAndCooking()
    {
        ArchetypeDefinition hearthArchetype = ArchetypeJsonReader.byId(shipped, "soulhome:hearth");

        BlockCounts.Builder blocks = BlockCounts.builder();
        blocks.add(TestBlocks.CHAIR, 1);
        blocks.add(TestBlocks.WOOL, 1);
        blocks.add(TestBlocks.BARREL, 1);
        blocks.add(TestBlocks.SMOKER, 1);

        BlockCounts counts = blocks.build();

        int comfortCount = 0;
        int storageCount = 0;
        int cookingCount = 0;

        for (ArchetypeDefinition.Signal signal : hearthArchetype.signals())
        {
            switch (signal.role())
            {
                case "comfort" -> comfortCount = counts.count(signal.match());
                case "storage" -> storageCount = counts.count(signal.match());
                case "cooking" -> cookingCount = counts.count(signal.match());
                default -> { }
            }
        }

        assertEquals(2, comfortCount, "the chair and the wool should both count toward comfort");
        assertEquals(1, storageCount, "the barrel should count toward storage");
        assertEquals(1, cookingCount, "the smoker should count toward cooking");
    }

    @Test
    @DisplayName("a furnished fireplace out-scores a netherrack-and-lava box of the same size - #48")
    void furnishedHearthOutscoresANetherPit()
    {
        ArchetypeDefinition hearthArchetype = ArchetypeJsonReader.byId(shipped, "soulhome:hearth");

        double furnished = classifier.score(onlyRegion(furnishedHearth()), hearthArchetype).score();
        double netherPit = classifier.score(onlyRegion(netherPitHearth()), hearthArchetype).score();

        assertTrue(furnished > netherPit,
                "a furnished fireplace (" + furnished + ") should out-score a nether pit of the same "
                        + "size (" + netherPit + ") - see #48");
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

    @Test
    @DisplayName("a bare pile of each archetype's defining blocks qualifies weakly, not fully")
    void pileOfDefiningBlocksQualifiesWeakly()
    {
        // #38: lowering the tier-1 bar is only safe once a pile is worth little - this is the
        // "worth little" half of that, measured against the shipped definitions rather than
        // asserted in the abstract. One representative, single-tag block per archetype, so the
        // pile cannot accidentally pick up credit from a second signal.
        Map<String, TestBlocks.TestBlock> definingBlock = Map.ofEntries(
                Map.entry("soulhome:alchemy_lab", TestBlocks.BREWING_STAND),
                Map.entry("soulhome:armoury", TestBlocks.GRINDSTONE),
                Map.entry("soulhome:bedchamber", TestBlocks.BED),
                Map.entry("soulhome:enchanting_room", TestBlocks.ENCHANTING_TABLE),
                Map.entry("soulhome:farm", TestBlocks.WHEAT),
                Map.entry("soulhome:hearth", TestBlocks.FURNACE),
                Map.entry("soulhome:library", TestBlocks.BOOKSHELF),
                Map.entry("soulhome:mine", TestBlocks.ORE),
                Map.entry("soulhome:track", TestBlocks.RAIL),
                Map.entry("soulhome:training_yard", TestBlocks.SLIME_BLOCK));

        for (ArchetypeDefinition archetype : shipped)
        {
            TestBlocks.TestBlock block = definingBlock.get(archetype.id());
            assertNotNull(block, archetype.id() + " needs a defining-block fixture for this test");
            assertFalse(archetype.requirements().isEmpty(), archetype.id() + " should gate on something");

            ArchetypeDefinition.Requirement requirement = archetype.requirements().get(0);
            assertTrue(requirement.match().test(block),
                    archetype.id() + "'s pile block should satisfy its own requirement");

            BlockCounts.Builder pile = BlockCounts.builder();
            pile.add(block, requirement.minCount());

            SoulRegion region = SoulRegion.create(
                    archetype.regionTypes().get(0),
                    new RegionBounds(0, 0, 0, 4, 4, 4),
                    BlockCounts.empty(),
                    pile.build(),
                    archetype.minVolume());

            ArchetypeScore score = classifier.score(region, archetype);

            assertTrue(score.qualifies(), archetype.id() + ": a pile of " + requirement.minCount() + " "
                    + block.id() + " should just barely qualify, scored " + score.score());

            AwardedRoom room = new AwardedRoom(archetype.id(), score.tier(), score.score());
            BuffBreakdown breakdown = BuffCalculator.explain(List.of(room), List.of(archetype), BuffSettings.DEFAULTS);

            for (ArchetypeDefinition.BuffSpec buff : archetype.buffs())
            {
                double granted = breakdown.totals().magnitude(buff.type());
                assertTrue(granted < buff.max() * 0.2d,
                        archetype.id() + "'s " + buff.type() + " should stay under a fifth of its ceiling for a "
                                + "bare pile, got " + granted + " of " + buff.max());
            }
        }
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

        double signalSum = 0d;

        for (ArchetypeScore.SignalContribution contribution : score.contributions())
        {
            signalSum += contribution.contribution();
        }

        double structuralSum = 0d;

        for (ArchetypeScore.StructureContribution contribution : score.structuralContributions())
        {
            structuralSum += contribution.contribution();
        }

        // structural credit is capped as a share of the signal total (#34), so the canonical study
        // - reading_spot's own weight comfortably under that cap - should reconstruct exactly; only
        // a form far past the cap would need the inequality instead
        if (score.structuralCapped())
        {
            assertTrue(score.rawScore() < signalSum + structuralSum,
                    "capped structural credit must be less than the uncapped total");
        }
        else
        {
            assertEquals(signalSum + structuralSum, score.rawScore(), 1e-9,
                    "contributions must account for the raw score");
        }

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

    @Test
    @DisplayName("structural credit is flagged as capped only once it actually hits the share cap")
    void structuralCappedFlagsOnlyWhenTheCapBites()
    {
        // #33 needs to tell a player "arrangement stopped moving the score" apart from "arrangement
        // simply is not worth much yet" - both look identical from the raw numbers alone unless the
        // classifier says which one happened
        SoulRegion region = onePileRegion();

        ArchetypeDefinition generous = structuralOnly("soulhome:cap_generous", 0.1d);
        ArchetypeScore generousScore = new ArchetypeClassifier(List.of(generous)).score(region, generous);

        assertFalse(generousScore.structuralCapped(), "a form well under half the signal total should not be flagged");
        assertEquals(1, generousScore.structuralContributions().size());

        ArchetypeDefinition greedy = structuralOnly("soulhome:cap_greedy", 50d);
        ArchetypeScore greedyScore = new ArchetypeClassifier(List.of(greedy)).score(region, greedy);

        assertTrue(greedyScore.structuralCapped(), "a form worth 100x the signal total should be flagged");
    }

    @Test
    @DisplayName("#34's forms never cost a canonical build a point or a tier")
    void formsNeverRegressAShippedArchetype()
    {
        // the epic's own guarantee: structural weights are additive and thresholds only ever come
        // down, so this should hold by construction - which is exactly the kind of claim that needs
        // a test rather than a comment. Run through the real shipped JSON via ArchetypeJsonReader,
        // so a weight typo in an archetype file fails here rather than quietly demoting a build.
        Map<String, GridVolume> canonicalBuild = Map.ofEntries(
                Map.entry("soulhome:library", library()),
                Map.entry("soulhome:hearth", hearth()),
                Map.entry("soulhome:track", track()),
                Map.entry("soulhome:bedchamber", bedchamber()),
                Map.entry("soulhome:training_yard", trainingYard()),
                Map.entry("soulhome:mine", mine()),
                Map.entry("soulhome:alchemy_lab", alchemyLab()),
                Map.entry("soulhome:farm", farm()),
                Map.entry("soulhome:enchanting_room", enchantingRoom()),
                Map.entry("soulhome:armoury", armoury()));

        assertEquals(shipped.size(), canonicalBuild.size(),
                "every shipped archetype needs a canonical-build fixture here, or a newly added one goes untested");

        for (ArchetypeDefinition archetype : shipped)
        {
            GridVolume volume = canonicalBuild.get(archetype.id());
            assertNotNull(volume, archetype.id() + " has no canonical-build fixture in this test");

            SoulRegion region = onlyRegion(volume);
            ArchetypeScore withForms = classifier.score(region, archetype);
            ArchetypeScore withoutForms = classifier.score(region, withoutStructures(archetype));

            assertTrue(withForms.score() >= withoutForms.score() - 1e-9,
                    archetype.id() + ": adding forms must never lower the score - "
                            + withForms.score() + " with, " + withoutForms.score() + " without");
            assertTrue(withForms.tier() >= withoutForms.tier(),
                    archetype.id() + ": adding forms must never cost a tier - tier "
                            + withForms.tier() + " with, tier " + withoutForms.tier() + " without");
        }
    }

    // endregion

    // region helpers

    private static SoulRegion onlyRegion(GridVolume volume)
    {
        List<SoulRegion> regions = RegionScanner.scan(volume, signals, geometry, ScanSettings.DEFAULTS);
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

    /** The same archetype, with its forms stripped - the "before #34" baseline for a regression check. */
    private static ArchetypeDefinition withoutStructures(ArchetypeDefinition archetype)
    {
        return new ArchetypeDefinition(
                archetype.id(), archetype.displayName(), archetype.regionTypes(), archetype.minVolume(),
                archetype.requirements(), archetype.signals(), archetype.detractors(), archetype.tiers(),
                archetype.buffs(), List.of());
    }

    /** One bookshelf, enclosed, nothing else - just enough signal total to have a cap worth hitting. */
    private static SoulRegion onePileRegion()
    {
        return SoulRegion.create(
                RegionType.ENCLOSED,
                new RegionBounds(0, 0, 0, 1, 1, 1),
                BlockCounts.empty(),
                BlockCounts.builder().add(TestBlocks.BOOKSHELF, 1).build(),
                1);
    }

    /**
     * A signal worth exactly 1.0 (one bookshelf, weight 1, {@code sqrt(1)}) and a single fully-
     * confident form at {@code formWeight}, so the form's raw contribution against the default
     * {@code structuralShareCap} (the signal total, once again as much - see #54) is controlled
     * entirely by the caller.
     */
    private static ArchetypeDefinition structuralOnly(String id, double formWeight)
    {
        return new ArchetypeDefinition(
                id,
                "archetype.soulhome.test",
                List.of(RegionType.ENCLOSED),
                1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(
                        BlockMatcher.ofTags("minecraft:bookshelves"), 1.0d, "core", 100)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(0.5d, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:nothing", 0.1d, 0.3d)),
                List.of(new Form(
                        "gathering", formWeight, "arrangement", Map.of(), FakeClause.of(1.0d), Set.of())));
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
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:nothing", 0.1d, 0.3d)),
                List.of());
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
        return hearth(TestBlocks.CANDLE);
    }

    /** The canonical hearth, lit with whatever {@code light} is - see #45. */
    private static GridVolume hearth(TestBlocks.TestBlock light)
    {
        return GridVolume.of(
                Map.of('c', light),
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

    /** A furnished fireplace room - #48's answer to {@link #netherPitHearth()}. */
    private static GridVolume furnishedHearth()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#WWWWW#",
                        "#S...S#",
                        "#.....#",
                        "#..N..#",
                        "#.....#",
                        "#S...S#",
                        "#WWWWW#"},
                new String[]{
                        "#WWWWW#",
                        "#c...c#",
                        "#.bbb.#",
                        "#.bbb.#",
                        "#.bbb.#",
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

    /**
     * A furnace ringed with lava, magma and netherrack and nothing else - the pre-#48 idea of a
     * hearth, and the build {@link #furnishedHearth()} of the same size and volume should beat.
     */
    private static GridVolume netherPitHearth()
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
                        "#.....#",
                        "#.mmm.#",
                        "#.mmm.#",
                        "#.mmm.#",
                        "#.....#",
                        "#kkkkk#"},
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

    private static GridVolume armoury()
    {
        return GridVolume.of(
                SLAB,
                new String[]{
                        "#GGGGG#",
                        "#i...i#",
                        "#.....#",
                        "#.bA..#",
                        "#.....#",
                        "#i...i#",
                        "#GGGGG#"},
                new String[]{
                        "#GGGGG#",
                        "#n...n#",
                        "#.....#",
                        "#..A..#",
                        "#.....#",
                        "#n...n#",
                        "#GGGGG#"},
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
