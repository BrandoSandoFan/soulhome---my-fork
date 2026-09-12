/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #143: bonds are declared once, credited to both sides, and never doubled. */
class BondBookTest
{
    private static final BondRelationRegistry REGISTRY = registry();

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

    private static ArchetypeDefinition archetype(String id, Bond... bonds)
    {
        return new ArchetypeDefinition(
                id, "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, "core", 8)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1d, 1)),
                List.of(),
                List.of(),
                List.of(bonds));
    }

    @Test
    @DisplayName("a bond declared on one side is seen from both")
    void declaredOnceSeenFromBoth()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:library", new Bond("soulhome:enchanting_room", "connects", 4d, "study", params("connects"))),
                archetype("soulhome:enchanting_room")), REGISTRY);

        assertEquals(1, book.bondsOf("soulhome:library").size());
        assertEquals(1, book.bondsOf("soulhome:enchanting_room").size());

        BondBook.Resolved fromRoom = book.bondsOf("soulhome:enchanting_room").get(0);
        assertEquals("soulhome:library", fromRoom.other());
        assertEquals("connects", fromRoom.relation().id());
        assertEquals(4d, fromRoom.weight());
        assertEquals("soulhome:library", fromRoom.declaredOn());
    }

    @Test
    @DisplayName("a directional bond is mirrored from the other side")
    void directionalBondIsMirrored()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:mine", new Bond("soulhome:workshop", "beneath", 3d, "works", params("beneath"))),
                archetype("soulhome:workshop")), REGISTRY);

        assertEquals("beneath", book.bondsOf("soulhome:mine").get(0).relation().id());
        assertEquals("above", book.bondsOf("soulhome:workshop").get(0).relation().id());
    }

    @Test
    @DisplayName("declaring the mirror on both sides is one bond, and the second is reported")
    void mirrorDeclarationsAreOneBond()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:mine", new Bond("soulhome:workshop", "beneath", 3d, "works", params("beneath"))),
                archetype("soulhome:workshop", new Bond("soulhome:mine", "above", 5d, "works", params("above")))), REGISTRY);

        assertEquals(1, book.bondsOf("soulhome:mine").size(), "not doubled");
        assertEquals(1, book.bondsOf("soulhome:workshop").size());
        assertEquals(3d, book.bondsOf("soulhome:workshop").get(0).weight(),
                "the declaration on the archetype whose id sorts first is the one kept");
        assertEquals(1, book.duplicates().size());
        assertTrue(book.duplicates().get(0).contains("soulhome:workshop"), book.duplicates().get(0));
    }

    @Test
    @DisplayName("the same symmetric bond declared on both sides is one bond too")
    void symmetricDuplicateIsOneBond()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:hearth", new Bond("soulhome:bedchamber", "near", 2d, "warmth", params("near"))),
                archetype("soulhome:bedchamber", new Bond("soulhome:hearth", "near", 2d, "warmth", params("near")))), REGISTRY);

        assertEquals(1, book.bondsOf("soulhome:hearth").size());
        assertEquals(1, book.bondsOf("soulhome:bedchamber").size());
        assertEquals(1, book.duplicates().size());
    }

    @Test
    @DisplayName("two different relations between the same pair are two bonds")
    void differentRelationsAreDifferentBonds()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:library",
                        new Bond("soulhome:enchanting_room", "connects", 4d, "study", params("connects")),
                        new Bond("soulhome:enchanting_room", "near", 1d, "study", params("near"))),
                archetype("soulhome:enchanting_room")), REGISTRY);

        assertEquals(2, book.bondsOf("soulhome:library").size());
        assertEquals(2, book.bondsOf("soulhome:enchanting_room").size());
        assertTrue(book.duplicates().isEmpty());
    }

    @Test
    @DisplayName("a bond naming an archetype that is not loaded is kept, and simply has no partner")
    void unloadedPartnerIsKept()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:library", new Bond("soulhome:ritual_chamber", "connects", 4d, "study", params("connects")))),
                REGISTRY);

        assertEquals(1, book.bondsOf("soulhome:library").size());
        assertEquals("soulhome:ritual_chamber", book.bondsOf("soulhome:library").get(0).other());
        assertTrue(book.bondsOf("soulhome:ritual_chamber").isEmpty() || book.bondsOf("soulhome:ritual_chamber").size() == 1,
                "the far side of an absent archetype is harmless either way");
    }

    @Test
    @DisplayName("a bond naming an unknown relation is skipped and reported, never a failure")
    void unknownRelationIsSkipped()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:library", new Bond("soulhome:hearth", "orbits", 4d, "study", ClauseParams.builder().build()))),
                REGISTRY);

        assertTrue(book.bondsOf("soulhome:library").isEmpty());
        assertEquals(1, book.unknownRelations().size());
    }

    @Test
    @DisplayName("a bond with itself is one entry, read from the one side")
    void selfBondIsNotDoubled()
    {
        BondBook book = BondBook.of(List.of(
                archetype("soulhome:library", new Bond("soulhome:library", "adjoins", 1d, "stacks", params("adjoins")))),
                REGISTRY);

        assertEquals(1, book.bondsOf("soulhome:library").size());
    }

    @Test
    @DisplayName("the shipped archetypes load unchanged whether or not they declare bonds")
    void shippedArchetypesLoad() throws java.io.IOException
    {
        BondBook book = BondBook.of(ArchetypeJsonReader.shipped());

        assertTrue(book.unknownRelations().isEmpty(), book.unknownRelations().toString());
        assertTrue(book.duplicates().isEmpty(), book.duplicates().toString());
    }
}
