/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an aspect actually changes about a player's buffs - the Aspects epic (#171), at the far end
 * of the pipeline.
 *
 * <p>Exactly one thing: which buff spec the room is paid out of. The score, the repeat falloff, the
 * archetype multiplier, rank and every cap are the ones that were already there.
 */
class AspectPayoutTest
{
    private static final String XP = "soulhome:xp_gain";
    private static final String ENCHANTING = "soulhome:enchantment_power";

    @Test
    @DisplayName("the default aspect pays the archetype's own buffs, exactly as a room with no aspects does")
    void theDefaultPaysWhatTheRoomAlwaysPaid()
    {
        SoulBuffSet withAspects = buffs(new AwardedRoom("soulhome:library", 2, 40d, "archive"));
        SoulBuffSet asBefore = BuffCalculator.computeFromAwarded(
                List.of(new AwardedRoom("soulhome:library", 2, 40d)),
                List.of(withoutAspects()),
                BuffSettings.DEFAULTS);

        assertEquals(asBefore.magnitude(XP), withAspects.magnitude(XP), 1e-9);
        assertEquals(0d, withAspects.magnitude(ENCHANTING), 1e-9);
    }

    @Test
    @DisplayName("a room that took the alternative is paid that aspect's buff instead, at the same magnitude")
    void theAlternativePaysItsOwn()
    {
        SoulBuffSet archive = buffs(new AwardedRoom("soulhome:library", 2, 40d, "archive"));
        SoulBuffSet scriptorium = buffs(new AwardedRoom("soulhome:library", 2, 40d, "scriptorium"));

        assertTrue(scriptorium.magnitude(ENCHANTING) > 0d);
        assertEquals(0d, scriptorium.magnitude(XP), 1e-9, "the scriptorium pays instead of, not as well as");
        assertEquals(0d, archive.magnitude(ENCHANTING), 1e-9);

        // the same room, the same score: the two buffs stand at the same fraction of their own
        // ceilings, which is what "the aspect selects the buff and never scales it" looks like
        // once the magnitudes have been through two differently-scaled ceilings
        assertEquals(
                archive.magnitude(XP) / 0.3d,
                scriptorium.magnitude(ENCHANTING) / 3d,
                1e-9);
    }

    @Test
    @DisplayName("two rooms of one archetype under different aspects still fall off against each other")
    void repeatFalloffIsAboutTheArchetype()
    {
        // the falloff asks how many libraries you have, not what each is for, so the second room
        // is halved whichever aspect it took
        SoulBuffSet sameAspect = buffs(
                new AwardedRoom("soulhome:library", 2, 40d, "archive"),
                new AwardedRoom("soulhome:library", 2, 40d, "archive"));

        SoulBuffSet split = buffs(
                new AwardedRoom("soulhome:library", 2, 40d, "archive"),
                new AwardedRoom("soulhome:library", 2, 40d, "scriptorium"));

        final double first = buffs(new AwardedRoom("soulhome:library", 2, 40d, "archive")).magnitude(XP);

        assertEquals(first, split.magnitude(XP), 1e-9, "the best room counts fully whatever the second one is");
        assertTrue(sameAspect.magnitude(XP) > split.magnitude(XP),
                "a second archive adds to the first; a scriptorium pays into its own buff instead");
        assertTrue(split.magnitude(ENCHANTING) > 0d);
        assertTrue(split.magnitude(ENCHANTING) < buffs(
                        new AwardedRoom("soulhome:library", 2, 40d, "scriptorium")).magnitude(ENCHANTING),
                "the second room is still the second room, and still falls off");
    }

    @Test
    @DisplayName("an aspect id the archetype no longer declares falls back to the archetype's own buffs")
    void anUnknownAspectFallsBackRatherThanPayingNothing()
    {
        // what a save written against a datapack that has since dropped the aspect hands back. The
        // room keeps granting what its kind of room grants, which is the same answer the switch
        // being off gives, and is never "this room grants nothing now"
        SoulBuffSet buffs = buffs(new AwardedRoom("soulhome:library", 2, 40d, "annexe"));

        assertTrue(buffs.magnitude(XP) > 0d);
        assertEquals(0d, buffs.magnitude(ENCHANTING), 1e-9);
    }

    @Test
    @DisplayName("the buff breakdown names which aspect a buff came from, and counts only the rooms that paid it")
    void theBreakdownNamesTheAspect()
    {
        BuffBreakdown breakdown = BuffCalculator.explain(
                List.of(
                        new AwardedRoom("soulhome:library", 2, 40d, "archive"),
                        new AwardedRoom("soulhome:library", 2, 30d, "scriptorium")),
                List.of(library()),
                BuffSettings.DEFAULTS);

        List<BuffBreakdown.Source> xp = breakdown.sourcesOf(XP);
        assertEquals(1, xp.size());
        assertEquals("archive", xp.get(0).aspectId());
        assertEquals("aspect.soulhome.library.archive", xp.get(0).aspectName());
        assertEquals(1, xp.get(0).rooms(), "the scriptorium paid a different buff and must not be counted here");

        List<BuffBreakdown.Source> enchanting = breakdown.sourcesOf(ENCHANTING);
        assertEquals(1, enchanting.size());
        assertEquals("scriptorium", enchanting.get(0).aspectId());
    }

    @Test
    @DisplayName("a room of an archetype with no aspects reports no aspect on its source, as it always did")
    void noAspectMeansNoAspectOnTheSource()
    {
        BuffBreakdown breakdown = BuffCalculator.explain(
                List.of(new AwardedRoom("soulhome:library", 2, 40d)),
                List.of(withoutAspects()),
                BuffSettings.DEFAULTS);

        BuffBreakdown.Source source = breakdown.sourcesOf(XP).get(0);

        assertNull(source.aspectId());
        assertTrue(!source.hasAspect());
    }

    private static SoulBuffSet buffs(AwardedRoom... rooms)
    {
        return BuffCalculator.computeFromAwarded(List.of(rooms), List.of(library()), BuffSettings.DEFAULTS);
    }

    /** A library with the two aspects #172 uses as its worked example. */
    private static ArchetypeDefinition library()
    {
        return new ArchetypeDefinition(
                "soulhome:library", "archetype.soulhome.library", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 3d, "core", 32)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(12d, 1), new ArchetypeDefinition.Tier(60d, 2)),
                List.of(new ArchetypeDefinition.BuffSpec(XP, 0.1d, 0.3d)),
                List.of(), List.of(),
                List.of(
                        new Aspect("archive", "aspect.soulhome.library.archive", true,
                                List.of(new Aspect.Lean(BlockMatcher.ofTags("soulhome:bookshelves"), 1d)),
                                List.of(), List.of()),
                        new Aspect("scriptorium", "aspect.soulhome.library.scriptorium", false,
                                List.of(new Aspect.Lean(BlockMatcher.ofTags("soulhome:bookshelves"), 1d)),
                                List.of(), List.of(new ArchetypeDefinition.BuffSpec(ENCHANTING, 1d, 3d)))));
    }

    /** The same library from before the epic, for the comparisons that have to come out identical. */
    private static ArchetypeDefinition withoutAspects()
    {
        ArchetypeDefinition library = library();

        return new ArchetypeDefinition(
                library.id(), library.displayName(), library.regionTypes(), library.minVolume(),
                library.requirements(), library.signals(), library.detractors(), library.tiers(),
                library.buffs(), library.structures(), library.bonds());
    }
}
