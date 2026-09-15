/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character declared by the rooms the mod actually ships (#165), read out of the real JSON.
 *
 * <p>The point of reading the shipped files rather than a fixture is that the two things most
 * likely to go wrong here are a typo in a trait name and a soul that leans somewhere nobody
 * intended, and neither is visible in a Java copy of the data.
 */
class ShippedCharacterTest
{
    private static List<ArchetypeDefinition> shipped() throws IOException
    {
        return ArchetypeJsonReader.shipped();
    }

    @Test
    void everyTraitTheShippedRoomsNameIsOneTheModKnows() throws IOException
    {
        List<String> unknown = new ArrayList<>();

        for (ArchetypeDefinition archetype : shipped())
        {
            for (String trait : archetype.character().keySet())
            {
                if (SoulTrait.byId(trait) == null)
                {
                    unknown.add(archetype.id() + ": " + trait);
                }
            }
        }

        assertEquals(List.of(), unknown, "a shipped room naming a trait nothing reads is a typo, not a feature");
    }

    @Test
    void everyShippedCharacterWeightIsPositiveAndSane() throws IOException
    {
        for (ArchetypeDefinition archetype : shipped())
        {
            assertEquals(List.of(), archetype.validationErrors(), archetype.id());

            for (Map.Entry<String, Double> pull : archetype.character().entrySet())
            {
                assertTrue(pull.getValue() > 0d && pull.getValue() <= 2d,
                        archetype.id() + " pulls " + pull.getKey() + " by " + pull.getValue()
                                + ", which is outside the range the shipped set is balanced over");
            }
        }
    }

    @Test
    void theShippedSetSaysSomethingOnEveryAxis() throws IOException
    {
        for (SoulAxis axis : SoulAxis.values())
        {
            for (SoulTrait trait : axis.traits())
            {
                final boolean claimed = shipped().stream()
                        .anyMatch(archetype -> archetype.characterPulls().containsKey(trait));

                assertTrue(claimed, "no shipped room pulls toward " + trait
                        + ", so half of " + axis + " is unreachable without a datapack");
            }
        }
    }

    @Test
    void aRoomThatSaysNothingIsARealCaseInTheShippedSet() throws IOException
    {
        final ArchetypeDefinition library = ArchetypeJsonReader.byId(shipped(), "soulhome:library");

        assertTrue(library.character().isEmpty(),
                "a library says nothing about what kind of place a soul is, and the zero-declaration"
                        + " path is worth having a shipped example of");
    }

    /**
     * The soul a player most plausibly has early on - a hearth, a library, a farm - must not read
     * as strongly anything. The colouring is supposed to arrive over the course of building a
     * soulhome, and a system that is at full strength on the third room is a system with no range.
     */
    @Test
    void anEarlySoulhomeIsOnlyFaintlyColoured() throws IOException
    {
        final Map<String, ArchetypeDefinition> byId = byId(shipped());

        final SoulCharacter early = SoulCharacter.of(List.of(
                new AwardedRoom("soulhome:hearth", 1, 20d),
                new AwardedRoom("soulhome:library", 1, 24d),
                new AwardedRoom("soulhome:farm", 1, 18d)), byId);

        assertTrue(early.depth() < 0.4d, "an early soulhome reads at " + early.depth());

        final List<AwardedRoom> established = new ArrayList<>();

        for (String id : List.of("soulhome:hearth", "soulhome:mead_hall", "soulhome:workshop",
                "soulhome:mine", "soulhome:farm", "soulhome:greenhouse", "soulhome:shrine"))
        {
            established.add(new AwardedRoom(id, 3, 70d));
        }

        assertTrue(SoulCharacter.of(established, byId).depth() > 0.75d,
                "and a soulhome somebody has lived in reads plainly");
    }

    @Test
    void aSoulOfForgesAndFreezersIsContestedRatherThanBlank() throws IOException
    {
        final Map<String, ArchetypeDefinition> byId = byId(shipped());

        final SoulCharacter both = SoulCharacter.of(List.of(
                new AwardedRoom("soulhome:hearth", 3, 60d),
                new AwardedRoom("soulhome:mead_hall", 3, 60d),
                new AwardedRoom("soulhome:cold_storage", 3, 60d),
                new AwardedRoom("soulhome:aquarium", 3, 60d)), byId);

        assertTrue(Math.abs(both.lean(SoulAxis.THERMAL)) < 0.25d);
        assertTrue(both.tension(SoulAxis.THERMAL) > 0.7d);
        assertFalse(both.isEmpty());
    }

    private static Map<String, ArchetypeDefinition> byId(List<ArchetypeDefinition> definitions)
    {
        return definitions.stream().collect(
                java.util.stream.Collectors.toMap(ArchetypeDefinition::id, archetype -> archetype));
    }
}
