/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.Aspect;
import leaf.soulhome.structures.core.BlockMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code aspects} through the codecs - #172 of the Aspects epic (#171). Sits outside
 * {@code leaf.soulhome.structures.core} for the same reason {@link FormCodecsTest} does:
 * DataFixerUpper is only on the game's classpath, and this file needs it.
 *
 * <p>The load-time rules themselves are pinned in {@code AspectValidationTest}, which runs
 * Minecraft-free. What is checked here is that the format reaches the definition and back intact,
 * including over the wire - the lens and the book name aspects, and the client has no datapack to
 * read them from.
 */
class AspectCodecsTest
{
    @Test
    @DisplayName("an archetype with no 'aspects' key round-trips exactly as it does today")
    void noAspectsRoundTrips()
    {
        ArchetypeDefinition original = new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(), 1,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertTrue(roundTrip(original).aspects().isEmpty());
    }

    @Test
    @DisplayName("aspects round-trip with their leans, their payout and which one is the default")
    void aspectsRoundTrip()
    {
        ArchetypeDefinition roundTripped = roundTrip(withAspects());

        assertEquals(2, roundTripped.aspects().size());

        final Aspect archive = roundTripped.aspects().get(0);
        assertEquals("archive", archive.id());
        assertEquals("aspect.soulhome.test.archive", archive.displayName());
        assertTrue(archive.isDefault());
        assertFalse(archive.paysItsOwn());
        assertEquals(1, archive.leans().size());
        assertEquals(32, archive.leans().get(0).cap());

        final Aspect scriptorium = roundTripped.aspects().get(1);
        assertFalse(scriptorium.isDefault());
        assertEquals(
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:enchantment_power", 1d, 3d)),
                scriptorium.buffs());

        // and the default's payout is still the archetype's own, which is how no build loses anything
        assertEquals(roundTripped.buffs(), roundTripped.buffsFor("archive"));
        assertEquals(scriptorium.buffs(), roundTripped.buffsFor("scriptorium"));
    }

    @Test
    @DisplayName("a lean's 'cap' defaults the way a signal's does")
    void leanCapDefaults()
    {
        JsonObject json = JsonParser.parseString("""
                {
                  "id": "archive",
                  "display_name": "aspect.soulhome.test.archive",
                  "default": true,
                  "leans": [ { "match": { "tag": "soulhome:bookshelves" }, "weight": 1.0 } ]
                }
                """).getAsJsonObject();

        Aspect aspect = ArchetypeCodecs.ASPECT.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(ArchetypeDefinition.DEFAULT_CAP, aspect.leans().get(0).cap());
        assertTrue(aspect.structures().isEmpty());
        assertTrue(aspect.buffs().isEmpty());
    }

    @Test
    @DisplayName("aspects reach the client: the over-the-wire format carries them too")
    void aspectsReachTheClient()
    {
        ArchetypeDefinition overTheWire = ArchetypeCodecs.ARCHETYPE_WITH_ID
                .parse(JsonOps.INSTANCE, ArchetypeCodecs.ARCHETYPE_WITH_ID
                        .encodeStart(JsonOps.INSTANCE, withAspects()).result().orElseThrow())
                .result()
                .orElseThrow();

        assertEquals(withAspects().aspects(), overTheWire.aspects());
    }

    @Test
    @DisplayName("an aspect's own structures round-trip through the same form grammar the archetype's do")
    void aspectStructuresRoundTrip()
    {
        JsonObject json = JsonParser.parseString("""
                {
                  "id": "scriptorium",
                  "display_name": "aspect.soulhome.test.scriptorium",
                  "leans": [ { "match": { "block": "minecraft:lectern" }, "weight": 2.0 } ],
                  "structures": [
                    {
                      "name": "writing_desks",
                      "weight": 4.0,
                      "elements": { "lecterns": { "block": "minecraft:lectern" } },
                      "shape": "soulhome:spacing",
                      "of": "lecterns",
                      "min_count": 3
                    }
                  ]
                }
                """).getAsJsonObject();

        BuiltinFormClauses.registerAll();

        Aspect aspect = ArchetypeCodecs.ASPECT.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(1, aspect.structures().size());
        assertEquals("writing_desks", aspect.structures().get(0).name());
        assertEquals(4d, aspect.structures().get(0).weight(), 1e-9);
    }

    private static ArchetypeDefinition roundTrip(ArchetypeDefinition original)
    {
        return ArchetypeCodecs.ARCHETYPE_WITH_ID
                .parse(JsonOps.INSTANCE, ArchetypeCodecs.ARCHETYPE_WITH_ID
                        .encodeStart(JsonOps.INSTANCE, original).result().orElseThrow())
                .result()
                .orElseThrow();
    }

    private static ArchetypeDefinition withAspects()
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(
                        BlockMatcher.ofTags("soulhome:bookshelves"), 3d, "core", 32)),
                List.of(), List.of(),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:xp_gain", 0.1d, 0.3d)),
                List.of(), List.of(),
                List.of(
                        new Aspect("archive", "aspect.soulhome.test.archive", true,
                                List.of(new Aspect.Lean(BlockMatcher.ofTags("soulhome:bookshelves"), 1d, 32)),
                                List.of(), List.of()),
                        new Aspect("scriptorium", "aspect.soulhome.test.scriptorium", false,
                                List.of(new Aspect.Lean(BlockMatcher.ofBlocks("minecraft:lectern"), 2d, 4)),
                                List.of(),
                                List.of(new ArchetypeDefinition.BuffSpec("soulhome:enchantment_power", 1d, 3d)))));
    }
}
