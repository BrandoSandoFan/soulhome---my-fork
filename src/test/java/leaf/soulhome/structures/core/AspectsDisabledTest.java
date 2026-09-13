/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code aspects.enabled = false} means the mod as it was before the Aspects epic - #176.
 *
 * <p>"There is a config switch" and "the switch actually restores the old behaviour everywhere" are
 * different claims and only the second one is worth anything, so these tests are about the second.
 * Every surface that could mention an aspect reads it off {@link ArchetypeScore#aspect} or
 * {@link AwardedRoom#aspectId}, and with the switch off both are null, which is why there is no
 * "is it enabled" check anywhere in the reporting code and no way for one to be forgotten.
 */
class AspectsDisabledTest
{
    private static final ScoringSettings OFF = ScoringSettings.DEFAULTS.withoutAspects();
    private static final String XP = "soulhome:xp_gain";
    private static final String ENCHANTING = "soulhome:enchantment_power";

    @Test
    @DisplayName("with the switch off an archetype declaring aspects still loads, still classifies, and takes none")
    void anArchetypeWithAspectsStillWorks()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of(library()), OFF);
        ClassificationResult result = classifier.classify(libraryRoom());

        assertEquals(ClassificationResult.Status.CLASSIFIED, result.status());
        assertNull(result.best().aspect(), "no aspect is selected at all, not merely ignored later");
        assertNull(result.best().aspectId());
    }

    @Test
    @DisplayName("the room scores exactly what it scores with aspects on - selection never touched the score either way")
    void theScoreIsTheSameOnOrOff()
    {
        SoulRegion room = libraryRoom();

        double on = new ArchetypeClassifier(List.of(library()), ScoringSettings.DEFAULTS).classify(room).best().score();
        double off = new ArchetypeClassifier(List.of(library()), OFF).classify(room).best().score();

        assertEquals(on, off, 1e-9);
    }

    @Test
    @DisplayName("nothing about an aspect is written to the save")
    void noAspectStateIsSaved()
    {
        List<AwardedRoom> awarded = AwardedRoom.from(
                new ArchetypeClassifier(List.of(library()), OFF).classify(List.of(libraryRoom())));

        assertEquals(1, awarded.size());
        assertFalse(awarded.get(0).hasAspect());
        assertNull(awarded.get(0).aspectId());
    }

    @Test
    @DisplayName("a room that would have taken the alternative grants the archetype's own buff instead")
    void everyRoomGrantsItsArchetypesOwnBuffs()
    {
        // a room stacked with lecterns: plainly a scriptorium, and with the switch off that is not
        // a thing this soul knows about
        SoulRegion scriptorium = region(16, 4);

        List<AwardedRoom> off = AwardedRoom.from(
                new ArchetypeClassifier(List.of(library()), OFF).classify(List.of(scriptorium)));
        List<AwardedRoom> on = AwardedRoom.from(
                new ArchetypeClassifier(List.of(library()), ScoringSettings.DEFAULTS).classify(List.of(scriptorium)));

        SoulBuffSet withSwitchOff = BuffCalculator.computeFromAwarded(off, List.of(library()), BuffSettings.DEFAULTS);
        SoulBuffSet withSwitchOn = BuffCalculator.computeFromAwarded(on, List.of(library()), BuffSettings.DEFAULTS);

        assertTrue(withSwitchOff.magnitude(XP) > 0d);
        assertEquals(0d, withSwitchOff.magnitude(ENCHANTING), 1e-9);

        assertTrue(withSwitchOn.magnitude(ENCHANTING) > 0d, "with it on, the same room is a scriptorium");
        assertEquals(0d, withSwitchOn.magnitude(XP), 1e-9);
    }

    @Test
    @DisplayName("a save written with the switch on is read correctly when it is turned off: the next scan decides, never the save")
    void aSavedAspectIsNotTrustedWhenTheSwitchIsOff()
    {
        // the save says scriptorium; the config says aspects are off. The scan is what settles it,
        // and a scan with the switch off produces a room with no aspect at all
        List<AwardedRoom> fromTheSave = List.of(new AwardedRoom("soulhome:library", 2, 40d, "scriptorium"));
        List<AwardedRoom> fromTheScan = AwardedRoom.from(
                new ArchetypeClassifier(List.of(library()), OFF).classify(List.of(region(16, 4))));

        assertTrue(fromTheSave.get(0).hasAspect());
        assertFalse(fromTheScan.get(0).hasAspect());

        assertTrue(BuffCalculator.computeFromAwarded(fromTheScan, List.of(library()), BuffSettings.DEFAULTS)
                .magnitude(XP) > 0d);
    }

    @Test
    @DisplayName("a region's identity hash is the same with the switch either way, so toggling it does not invalidate every cached scan")
    void identityHashIsUntouched()
    {
        // the hash is computed from what is in a region and how it sits, which is a question about
        // the build. Nothing about an aspect - a reading of that build - may reach it
        SoulRegion room = libraryRoom();
        final long before = room.identityHash();

        new ArchetypeClassifier(List.of(library()), ScoringSettings.DEFAULTS).classify(room);
        new ArchetypeClassifier(List.of(library()), OFF).classify(room);

        assertEquals(before, room.identityHash());
    }

    @Test
    @DisplayName("with the switch off, no shipped room reports an aspect anywhere")
    void noShippedRoomReportsAnAspect() throws IOException
    {
        List<ArchetypeDefinition> shipped = ArchetypeJsonReader.shipped();
        ArchetypeClassifier classifier = new ArchetypeClassifier(shipped, OFF);

        List<String> reported = new ArrayList<>();

        for (ArchetypeScore score : classifier.classify(libraryRoom()).allScores())
        {
            if (score.hasAspect())
            {
                reported.add(score.archetypeId());
            }
        }

        assertEquals(List.of(), reported);
    }

    private static SoulRegion libraryRoom()
    {
        return region(24, 2);
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

    private static ArchetypeDefinition library()
    {
        return new ArchetypeDefinition(
                "soulhome:library", "archetype.soulhome.library", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(
                        new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 3d, "core", 32),
                        new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:lectern"), 5d, "core", 4)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(12d, 1), new ArchetypeDefinition.Tier(60d, 2)),
                List.of(new ArchetypeDefinition.BuffSpec(XP, 0.1d, 0.3d)),
                List.of(), List.of(),
                List.of(
                        new Aspect("archive", "aspect.soulhome.library.archive", true,
                                List.of(new Aspect.Lean(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, 32)),
                                List.of(), List.of()),
                        new Aspect("scriptorium", "aspect.soulhome.library.scriptorium", false,
                                List.of(new Aspect.Lean(BlockMatcher.ofBlocks("minecraft:lectern"), 4d, 4)),
                                List.of(), List.of(new ArchetypeDefinition.BuffSpec(ENCHANTING, 1d, 3d)))));
    }
}
