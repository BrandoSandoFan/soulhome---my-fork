/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blend a soul's rooms make of it (#165) - and, mostly, the things it must refuse to do:
 * assign a soul a kind, average two committed halves into the middle, or let a datapack's typo
 * decide anything.
 */
class SoulCharacterTest
{
    private static ArchetypeDefinition archetype(String id, Map<String, Double> character)
    {
        return new ArchetypeDefinition(
                id, "archetype." + id, List.of(RegionType.ENCLOSED), 1, List.of(), List.of(), List.of(),
                List.of(new ArchetypeDefinition.Tier(1, 1)), List.of(), List.of(), List.of(), List.of(),
                character);
    }

    private static Map<String, ArchetypeDefinition> world()
    {
        Map<String, ArchetypeDefinition> byId = new LinkedHashMap<>();

        byId.put("soulhome:hearth", archetype("soulhome:hearth", Map.of("warm", 1.5)));
        byId.put("soulhome:cold_storage", archetype("soulhome:cold_storage", Map.of("cold", 1.5)));
        byId.put("soulhome:workshop", archetype("soulhome:workshop", Map.of("wrought", 1.0)));
        byId.put("soulhome:library", archetype("soulhome:library", Map.of()));

        return byId;
    }

    private static AwardedRoom room(String archetypeId, double score)
    {
        return new AwardedRoom(archetypeId, 1, score);
    }

    @Test
    void anUnbuiltSoulIsNeutral()
    {
        assertSame(SoulCharacter.EMPTY, SoulCharacter.of(List.of(), world()));
        assertEquals(0d, SoulCharacter.EMPTY.depth());
        assertTrue(SoulCharacter.EMPTY.isEmpty());
    }

    @Test
    void anArchetypeThatDeclaresNothingContributesNothing()
    {
        final SoulCharacter character = SoulCharacter.of(
                List.of(room("soulhome:library", 60d), room("soulhome:library", 40d)), world());

        assertTrue(character.isEmpty(), "a library says nothing about a soul, and must not default to saying something");
        assertEquals(0d, character.depth());
    }

    @Test
    void anUnknownTraitIsDroppedRatherThanGuessedAt()
    {
        Map<String, ArchetypeDefinition> byId = new LinkedHashMap<>();
        byId.put("pack:sauna", archetype("pack:sauna", Map.of("warm", 1.0, "humid", 4.0)));

        final SoulCharacter character = SoulCharacter.of(List.of(room("pack:sauna", 40d)), byId);

        assertEquals(40d, character.pull(SoulTrait.WARM), 1e-9);
        assertEquals(40d, character.totalPull(), 1e-9,
                "a trait from a later version of the mod contributes nothing, and takes nothing with it");
    }

    @Test
    void aWellScoredRoomSaysMoreThanABarelyQualifyingOne()
    {
        final double fine = SoulCharacter.of(List.of(room("soulhome:hearth", 80d)), world()).depth();
        final double scraped = SoulCharacter.of(List.of(room("soulhome:hearth", 8d)), world()).depth();

        assertTrue(fine > scraped);
        assertTrue(scraped > 0d, "a room that only just qualified still says something");
        assertTrue(fine < 1d, "and nothing ever finishes the job - depth saturates rather than capping");
    }

    @Test
    void oneKindOfRoomLeansAllTheWayWithoutBeingContested()
    {
        final SoulCharacter character = SoulCharacter.of(
                List.of(room("soulhome:hearth", 50d), room("soulhome:hearth", 30d)), world());

        assertEquals(1d, character.lean(SoulAxis.THERMAL), 1e-9);
        assertEquals(0d, character.tension(SoulAxis.THERMAL), 1e-9);
        assertEquals(1d, character.share(SoulAxis.THERMAL), 1e-9);
    }

    /**
     * The failure mode the issue thread named by name: a soul with a great deal of both halves must
     * not read as a soul with nothing in particular. The leaning is the same as an empty soul's;
     * everything that tells them apart is on {@link SoulCharacter#tension}.
     */
    @Test
    void bothPolesAtOnceIsContestedRatherThanAverage()
    {
        final SoulCharacter both = SoulCharacter.of(
                List.of(room("soulhome:hearth", 60d), room("soulhome:cold_storage", 60d)), world());

        assertEquals(0d, both.lean(SoulAxis.THERMAL), 1e-6);
        assertEquals(1d, both.tension(SoulAxis.THERMAL), 1e-6);

        assertEquals(0d, SoulCharacter.EMPTY.lean(SoulAxis.THERMAL), 1e-9);
        assertEquals(0d, SoulCharacter.EMPTY.tension(SoulAxis.THERMAL), 1e-9,
                "an axis nothing is built on is not contested - that is what separates it from one that is");

        assertTrue(both.depth() > 0.4d, "and a soul with four fine rooms in it is plainly built in");
    }

    @Test
    void anUntouchedAxisSaysNothingAboutTheSoul()
    {
        final SoulCharacter character = SoulCharacter.of(List.of(room("soulhome:hearth", 50d)), world());

        assertEquals(0d, character.share(SoulAxis.ESSENCE), 1e-9);
        assertEquals(0d, character.share(SoulAxis.VITALITY), 1e-9);
        assertEquals(0d, character.presence(SoulAxis.VITALITY), 1e-9);
    }

    @Test
    void axesAreWeightedByTheirShareOfTheWholeSoul()
    {
        final SoulCharacter character = SoulCharacter.of(
                List.of(room("soulhome:hearth", 40d), room("soulhome:workshop", 20d)), world());

        // hearth pulls 1.5 * 40 = 60 warm; workshop pulls 1.0 * 20 = 20 wrought
        assertEquals(60d / 80d, character.share(SoulAxis.THERMAL), 1e-9);
        assertEquals(20d / 80d, character.share(SoulAxis.ESSENCE), 1e-9);
    }

    @Test
    void theOrderRoomsWereScannedInChangesNothing()
    {
        List<AwardedRoom> rooms = new ArrayList<>(List.of(
                room("soulhome:hearth", 31d), room("soulhome:cold_storage", 47d),
                room("soulhome:workshop", 12d), room("soulhome:library", 90d)));

        final SoulCharacter first = SoulCharacter.of(rooms, world());

        Collections.reverse(rooms);

        final SoulCharacter reversed = SoulCharacter.of(rooms, world());

        assertEquals(first, reversed);
    }

    @Test
    void aRoomWhoseArchetypeHasGoneContributesNothing()
    {
        final SoulCharacter character = SoulCharacter.of(
                List.of(room("pack:removed_last_week", 60d), room("soulhome:hearth", 20d)), world());

        assertEquals(30d, character.totalPull(), 1e-9);
        assertNotEquals(SoulCharacter.EMPTY, character);
    }
}
