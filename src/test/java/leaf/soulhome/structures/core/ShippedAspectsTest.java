/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aspects the mod actually ships - #174 of the Aspects epic (#171), read from the archetype
 * JSON rather than from a copy of it.
 *
 * <p>The bar #174 sets is that a player would plausibly build the room that way <i>without knowing
 * aspects existed</i>. That is not a property a test can assert, so what is kept here instead is a
 * reference build per aspect: an ordinary room of its kind, built the way someone would build that
 * kind of room, landing on the aspect that matches it. If a balance pass makes the scriptorium need
 * an unnatural rank of lecterns to reach, one of these stops passing.
 *
 * <p>Three archetypes have aspects, deliberately: 29 archetypes with two aspects each is 58 buffs
 * to balance and a book nobody finishes.
 *
 * <ul>
 *   <li><b>library</b> - an archive of shelves and storage, or a scriptorium of lecterns laid out
 *       to write at, under light. People build both, and they look different.</li>
 *   <li><b>hearth</b> - a fireside of seating round the fire, or a kitchen of smoker, cauldron and
 *       stores. #48 already rewrote this room to reward a furnished fireplace over a netherrack
 *       bonfire, so every block a kitchen leans on was already scored: rule 2 was satisfied for
 *       free and no room's signals had to be widened for any of the three.</li>
 *   <li><b>mine</b> - a working shaft of rails, ladders and light, or a lode where what was dug up
 *       is massed in blocks. Digging, versus what you dug.</li>
 * </ul>
 */
class ShippedAspectsTest
{
    @Test
    @DisplayName("a library of shelves and stores is an archive - and would have been, before aspects existed")
    void shelvesAndStoresMakeAnArchive() throws IOException
    {
        assertEquals("archive", aspectOf("soulhome:library", contents(
                TestBlocks.BOOKSHELF, 28,
                TestBlocks.BARREL, 6,
                TestBlocks.CHAIR, 2,
                TestBlocks.CANDLE, 4)));
    }

    @Test
    @DisplayName("a library of lecterns in a lit row is a scriptorium")
    void lecternsInARowMakeAScriptorium() throws IOException
    {
        // a reading room turned over to writing: the shelves a library must have, and four desks
        // set out in a line with a candle at each. Nothing here is placed for the sake of the
        // aspect - it is what a scriptorium looks like
        GridVolume scriptorium = GridVolume.of(
                new String[]{
                        "#########",
                        "#########",
                        "#########",
                        "#########",
                        "#########"},
                new String[]{
                        "#########",
                        "#BBBBBBB#",
                        "#.......#",
                        "#L.L.L.L#",
                        "#########"},
                new String[]{
                        "#########",
                        "#BBBBBBB#",
                        "#c.c.c.c#",
                        "#.......#",
                        "#########"},
                new String[]{
                        "#########",
                        "#########",
                        "#########",
                        "#########",
                        "#########"});

        assertEquals("scriptorium", aspectOf("soulhome:library", scriptorium));
    }

    @Test
    @DisplayName("a hearth with seating round the fire is a fireside")
    void seatingRoundTheFireMakesAFireside() throws IOException
    {
        assertEquals("fireplace", aspectOf("soulhome:hearth", contents(
                TestBlocks.FURNACE, 1,
                TestBlocks.CHAIR, 4,
                TestBlocks.CARPET, 4,
                TestBlocks.CANDLE, 3)));
    }

    @Test
    @DisplayName("a hearth with a smoker, a cauldron and stores is a kitchen")
    void aSmokerAndStoresMakeAKitchen() throws IOException
    {
        assertEquals("kitchen", aspectOf("soulhome:hearth", contents(
                TestBlocks.FURNACE, 1,
                TestBlocks.SMOKER, 1,
                TestBlocks.CAULDRON, 1,
                TestBlocks.BARREL, 4,
                TestBlocks.CANDLE, 2)));
    }

    @Test
    @DisplayName("a mine of rails, ladders and lights is a working shaft")
    void railsAndLaddersMakeAShaft() throws IOException
    {
        assertEquals("shaft", aspectOf("soulhome:mine", contents(
                TestBlocks.ORE, 8,
                TestBlocks.RAIL, 12,
                TestBlocks.LADDER, 6,
                TestBlocks.TORCH, 8)));
    }

    @Test
    @DisplayName("a mine where what was dug up is massed in blocks is a lode")
    void massedStorageBlocksMakeALode() throws IOException
    {
        assertEquals("vein", aspectOf("soulhome:mine", contents(
                TestBlocks.ORE, 12,
                TestBlocks.IRON_BLOCK, 5,
                TestBlocks.GOLD_BLOCK, 3,
                TestBlocks.TORCH, 4)));
    }

    @Test
    @DisplayName("no shipped default aspect changes what its room pays: the default's payout is the archetype's own")
    void defaultsPayExactlyTodaysBuffs() throws IOException
    {
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            for (Aspect aspect : archetype.aspects())
            {
                if (!aspect.isDefault())
                {
                    continue;
                }

                assertEquals(archetype.buffs(), archetype.buffsFor(aspect.id()),
                        archetype.id() + "'s default aspect must pay what the room always paid");
            }
        }
    }

    @Test
    @DisplayName("every alternative aspect pays a buff type the mod knows how to grant")
    void alternativesPaySomethingReal() throws IOException
    {
        // an aspect that pays nothing a player would want is a dead branch; an aspect that pays a
        // buff type nothing applies is a room that classifies, takes the aspect, and does nothing
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            for (Aspect aspect : archetype.aspects())
            {
                for (ArchetypeDefinition.BuffSpec spec : aspect.buffs())
                {
                    assertTrue(SoulBuffTypes.BUILT_IN.contains(spec.type()),
                            archetype.id() + " aspect '" + aspect.id() + "' pays unknown '" + spec.type() + "'");
                    assertTrue(spec.max() > 0d,
                            archetype.id() + " aspect '" + aspect.id() + "' pays a ceiling of nothing");
                }
            }
        }
    }

    /** The aspect a region of these contents takes of this shipped archetype. */
    private static String aspectOf(String archetypeId, SoulRegion region) throws IOException
    {
        List<ArchetypeDefinition> shipped = ArchetypeJsonReader.shipped();
        ArchetypeDefinition archetype = ArchetypeJsonReader.byId(shipped, archetypeId);

        AspectSelection selection = AspectSelector.select(region, archetype, ScoringSettings.DEFAULTS);

        assertTrue(selection != null, archetypeId + " declares no aspects");

        return selection.takenId();
    }

    /** As above, scanning an actual build so an aspect's own arrangement has real geometry to read. */
    private static String aspectOf(String archetypeId, GridVolume volume) throws IOException
    {
        List<ArchetypeDefinition> shipped = ArchetypeJsonReader.shipped();

        Predicate<BlockSignature> signals = ArchetypeSignals.openClusterFilterFor(shipped);
        Predicate<BlockSignature> geometry = ArchetypeSignals.geometryFilterFor(shipped);

        List<SoulRegion> regions = RegionScanner.scan(volume, signals, geometry, ScanSettings.DEFAULTS);

        assertEquals(1, regions.size(), "the reference build should scan as one room: " + regions);

        return aspectOf(archetypeId, regions.get(0));
    }

    /** A room holding exactly these blocks, for an aspect that reads contents rather than layout. */
    private static SoulRegion contents(Object... blocksAndCounts)
    {
        BlockCounts.Builder builder = BlockCounts.builder();

        for (int i = 0; i < blocksAndCounts.length; i += 2)
        {
            builder.add((BlockSignature) blocksAndCounts[i], (Integer) blocksAndCounts[i + 1]);
        }

        return SoulRegion.create(
                RegionType.ENCLOSED,
                new RegionBounds(0, 0, 0, 7, 4, 7),
                BlockCounts.empty(),
                builder.build(),
                96);
    }
}
