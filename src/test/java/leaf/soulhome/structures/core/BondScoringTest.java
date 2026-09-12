/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #147: bonds are scored against the awards from the unbonded pass and folded into what a room is
 * worth, capped as a share of what it earned alone, and never change what a room is.
 */
class BondScoringTest
{
    private static final BondRelationRegistry REGISTRY = registry();
    private static final int REACH = 24;

    private static BondRelationRegistry registry()
    {
        BondRelationRegistry registry = new BondRelationRegistry();

        for (BondRelation relation : BondRelations.all())
        {
            registry.register(relation);
        }

        return registry;
    }

    private static ClauseParams params(String relation)
    {
        ClauseParams.Builder params = ClauseParams.builder();

        for (ClauseParamSpec spec : REGISTRY.get(relation).orElseThrow().params())
        {
            params.put(spec.name(), spec.defaultValue());
        }

        return params.build();
    }

    /** A room of bookshelves, tier 1 at 3 and tier 3 at 9 - a 3x3 wall of nine shelves scores 9. */
    private static ArchetypeDefinition study(Bond... bonds)
    {
        return new ArchetypeDefinition(
                "soulhome:study", "archetype.soulhome.study", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 3d, "shelves", 64)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(3d, 1), new ArchetypeDefinition.Tier(6d, 2), new ArchetypeDefinition.Tier(9d, 3)),
                List.of(),
                List.of(),
                List.of(bonds));
    }

    /** A room with a lectern in it. */
    private static ArchetypeDefinition reading(Bond... bonds)
    {
        return new ArchetypeDefinition(
                "soulhome:reading", "archetype.soulhome.reading", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:lectern"), 5d, "lectern", 4)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(3d, 1), new ArchetypeDefinition.Tier(10d, 2)),
                List.of(),
                List.of(),
                List.of(bonds));
    }

    private static ArchetypeClassifier classifier(ArchetypeDefinition... archetypes)
    {
        List<ArchetypeDefinition> list = List.of(archetypes);
        return new ArchetypeClassifier(list, ScoringSettings.DEFAULTS, BondBook.of(list, REGISTRY));
    }

    private static RegionScanner.ScanResult scan(GridVolume volume)
    {
        return RegionScanner.scanWithAdjacency(volume, null, null, false, REACH, ScanSettings.DEFAULTS);
    }

    private static ClassificationResult awarded(List<ClassificationResult> results, String archetypeId)
    {
        for (ClassificationResult result : results)
        {
            if (archetypeId.equals(result.awardedArchetypeId().orElse(null)))
            {
                return result;
            }
        }

        throw new AssertionError("Nothing awarded " + archetypeId + " among " + results);
    }

    @Test
    @DisplayName("a bonded room scores more than the same room with no partner, and both sides are credited")
    void aBondRaisesBothRooms()
    {
        ArchetypeClassifier bonded = classifier(
                study(new Bond("soulhome:reading", "connects", 4d, "reading", params("connects"))), reading());
        ArchetypeClassifier plain = classifier(study(), reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('D'));

        List<ClassificationResult> with = bonded.classify(scan.regions(), scan.adjacency());
        List<ClassificationResult> without = plain.classify(scan.regions(), scan.adjacency());

        ArchetypeScore studyWith = awarded(with, "soulhome:study").best();
        ArchetypeScore studyWithout = awarded(without, "soulhome:study").best();
        ArchetypeScore readingWith = awarded(with, "soulhome:reading").best();
        ArchetypeScore readingWithout = awarded(without, "soulhome:reading").best();

        assertTrue(studyWith.score() > studyWithout.score(), studyWith.score() + " vs " + studyWithout.score());
        assertTrue(readingWith.score() > readingWithout.score(), "declared once, credited to both");
        assertEquals(1, studyWith.bondContributions().size());
        assertEquals("soulhome:reading", studyWith.bondContributions().get(0).otherArchetypeId());
        assertEquals(1.0d, studyWith.bondContributions().get(0).confidence(), 1e-9, "a doorway between them");
        assertTrue(studyWith.bondContributions().get(0).otherRegion() >= 0, "the lens can highlight the partner");
    }

    @Test
    @DisplayName("a bond cannot change which archetype a region is awarded")
    void awardsAreDecidedBeforeBonds()
    {
        // two archetypes that tie on the study's own blocks, so it is ambiguous on its own. A
        // bond on one of them with the room next door would, if bonds could vote, break the tie
        ArchetypeDefinition twin = new ArchetypeDefinition(
                "soulhome:twin", "archetype.soulhome.twin", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 3d, "shelves", 64)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(3d, 1)),
                List.of(),
                List.of());

        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "connects", 40d, "reading", params("connects"))), twin, reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('D'));
        List<ClassificationResult> results = classifier.classify(scan.regions(), scan.adjacency());

        ClassificationResult shelves = results.stream()
                .filter(result -> result.region().allBlocks().count(BlockMatcher.ofTags("soulhome:bookshelves")) > 0)
                .findFirst().orElseThrow();

        assertEquals(ClassificationResult.Status.AMBIGUOUS, shelves.status());
        assertTrue(shelves.best().bondContributions().isEmpty(), "an unawarded room scores no bonds");
    }

    @Test
    @DisplayName("bond credit is capped as a share of what the room earned on its own")
    void bondCreditIsCapped()
    {
        // one small study, three enormous bonds. Without a cap the floor plan would be worth
        // more than the room; with it, the credit is held to bondShareCap of the room's own total
        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "connects", 1000d, "reading", params("connects")),
                        new Bond("soulhome:reading", "near", 1000d, "warmth", params("near")),
                        new Bond("soulhome:reading", "adjoins", 1000d, "wall", params("adjoins"))),
                reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('D'));
        ArchetypeScore study = awarded(classifier.classify(scan.regions(), scan.adjacency()), "soulhome:study").best();
        ArchetypeScore alone = classifier.classify(scan.regions()).stream()
                .filter(result -> "soulhome:study".equals(result.awardedArchetypeId().orElse(null)))
                .findFirst().orElseThrow().best();

        assertTrue(study.bondCapped(), "three thousand-point bonds must hit the cap");
        assertTrue(study.score() <= alone.score() * (1d + ScoringSettings.DEFAULT_BOND_SHARE_CAP) * 2d,
                "held to a share of the room's own worth, not the bonds': " + study.score() + " vs " + alone.score());
    }

    @Test
    @DisplayName("a discord is not share-capped, and costs tiers but never the room")
    void aDiscordCostsTiersButNotTheRoom()
    {
        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "near", -1000d, "hazard", params("near"))), reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('D'));
        List<ClassificationResult> results = classifier.classify(scan.regions(), scan.adjacency());
        ClassificationResult study = awarded(results, "soulhome:study");

        assertEquals(ClassificationResult.Status.CLASSIFIED, study.status(), "still a study");
        assertEquals(1, study.best().tier(), "but no better than tier 1");
        assertTrue(study.best().bondContributions().get(0).discord());
        assertTrue(study.best().bondContributions().get(0).contribution() < -100d, "uncapped");
    }

    @Test
    @DisplayName("a bond's role feeds the diversity multiplier once it clears the threshold")
    void bondRolesFeedDiversity()
    {
        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "connects", 1d, "reading", params("connects"))), reading());
        ArchetypeClassifier plain = classifier(study(), reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('D'));

        double with = awarded(classifier.classify(scan.regions(), scan.adjacency()), "soulhome:study").best().diversityMultiplier();
        double without = awarded(plain.classify(scan.regions(), scan.adjacency()), "soulhome:study").best().diversityMultiplier();

        assertEquals(without + ScoringSettings.DEFAULTS.diversityBonusPerRole(), with, 1e-9);
    }

    @Test
    @DisplayName("the same house scores identically whichever room the scanner found first")
    void scoresAreOrderIndependent()
    {
        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "connects", 4d, "reading", params("connects"))), reading());

        RegionScanner.ScanResult forward = scan(studyAndReadingRoom('D'));
        RegionScanner.ScanResult mirrored = scan(mirrorX(studyAndReadingRoom('D')));

        List<ClassificationResult> a = classifier.classify(forward.regions(), forward.adjacency());
        List<ClassificationResult> b = classifier.classify(mirrored.regions(), mirrored.adjacency());

        assertEquals(awarded(a, "soulhome:study").best().score(), awarded(b, "soulhome:study").best().score(), 1e-9);
        assertEquals(awarded(a, "soulhome:reading").best().score(), awarded(b, "soulhome:reading").best().score(), 1e-9);
    }

    @Test
    @DisplayName("the single-region overload, and the list overload without adjacency, are bond-free")
    void overloadsWithoutAdjacencyAreBondFree()
    {
        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "connects", 4d, "reading", params("connects"))), reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('D'));

        for (SoulRegion region : scan.regions())
        {
            assertTrue(classifier.classify(region).best().bondContributions().isEmpty());
        }

        ArchetypeScore listed = awarded(classifier.classify(scan.regions()), "soulhome:study").best();
        assertTrue(listed.bondContributions().isEmpty(), "nothing relates without an adjacency");
        assertFalse(listed.missingBonds().isEmpty(), "but the bond is still reported as not earned");
    }

    @Test
    @DisplayName("a bond not earned says why: no such room, or the room too far")
    void missesSayWhy()
    {
        ArchetypeClassifier classifier = classifier(
                study(new Bond("soulhome:reading", "connects", 4d, "reading", params("connects")),
                        new Bond("soulhome:ritual_chamber", "near", 2d, "magic", params("near"))),
                reading());

        RegionScanner.ScanResult scan = scan(studyAndReadingRoom('#'));
        ArchetypeScore study = awarded(classifier.classify(scan.regions(), scan.adjacency()), "soulhome:study").best();

        assertTrue(study.bondContributions().isEmpty());
        assertEquals(2, study.missingBonds().size());

        ArchetypeScore.BondContribution wall = study.missingBonds().stream()
                .filter(miss -> miss.otherArchetypeId().equals("soulhome:reading")).findFirst().orElseThrow();
        ArchetypeScore.BondContribution absent = study.missingBonds().stream()
                .filter(miss -> miss.otherArchetypeId().equals("soulhome:ritual_chamber")).findFirst().orElseThrow();

        assertEquals("shares a wall with it but there is no way through", wall.diagnostic());
        assertTrue(wall.otherRegion() >= 0, "the partner exists, it is just walled off");
        assertEquals(-1, absent.otherRegion());
        assertTrue(absent.diagnostic().contains("not installed"), absent.diagnostic());
    }

    // region layouts

    /** A 3x3x2 study lined with nine bookshelves beside a 3x3x2 room with a lectern, one shared wall. */
    private static GridVolume studyAndReadingRoom(char divider)
    {
        String[] slab = {"#########", "#########", "#########", "#########", "#########"};
        String[] rooms = {
                "#BBB#####",
                "#...#...#",
                "#..." + divider + ".L.#",
                "#...#...#",
                "#BBB#####"};
        String[] upper = {
                "#BBB#####",
                "#...#...#",
                "#...#...#",
                "#...#...#",
                "#########"};

        return GridVolume.of(slab, rooms, upper, slab);
    }

    private static GridVolume mirrorX(GridVolume volume)
    {
        final RegionBounds box = volume.bounds();
        final int sizeX = box.sizeX() - 2;
        final int sizeY = box.sizeY() - 2;
        final int sizeZ = box.sizeZ() - 2;

        String[][] layers = new String[sizeY][sizeZ];

        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                StringBuilder row = new StringBuilder();

                for (int x = sizeX - 1; x >= 0; x--)
                {
                    BlockSignature signature = volume.signatureAt(x, y, z);
                    char symbol = '.';

                    if (signature != null)
                    {
                        for (var entry : volume.palette().entrySet())
                        {
                            if (entry.getValue().equals(signature))
                            {
                                symbol = entry.getKey();
                            }
                        }
                    }

                    row.append(symbol);
                }

                layers[y][z] = row.toString();
            }
        }

        return GridVolume.of(volume.palette(), layers);
    }

    // endregion
}
