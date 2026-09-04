/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The book writes "up to +N" from an archetype's declared {@code max}, and the game hands out
 * {@code capFor(type)} - so the two disagreeing is the book lying about a number a player is
 * building towards.
 *
 * <p>This is the regression test for the defect that made every active ability useless: the eight
 * actives were added to {@code SoulBuffTypes.NON_FRACTION} but not to
 * {@link BuffSettings#DEFAULT_TYPE_CAPS}, so each inherited {@code globalMaxMagnitude} - a
 * fraction's 1.0 - and Aegis granted half a heart of absorption against a declared twelve.
 * Nothing failed; the numbers simply stopped moving.
 */
class BuffCapReachabilityTest
{
    @Test
    @DisplayName("every buff a shipped archetype declares can actually reach the magnitude it promises")
    void declaredMaximaAreReachable() throws IOException
    {
        final BuffSettings settings = BuffSettings.DEFAULTS;

        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            for (ArchetypeDefinition.BuffSpec buff : archetype.buffs())
            {
                final double cap = settings.capFor(buff.type());

                assertTrue(
                        cap >= buff.max(),
                        archetype.id() + " promises " + buff.type() + " up to " + buff.max()
                                + ", but the ceiling for that type is " + cap
                                + " - add it to BuffSettings.DEFAULT_TYPE_CAPS");
            }
        }
    }

    @Test
    @DisplayName("a type absent from the config's own list still gets its real ceiling, not the fraction default")
    void anOlderConfigStillGetsTheBuiltInCeilings()
    {
        // exactly the shape of a config file written before a buff type existed: the list it holds
        // is whatever was current when it was generated, and nothing ever rewrites it
        final BuffSettings olderConfig = new BuffSettings(
                0.5d, 3, 1.0d, java.util.Map.of(), java.util.Map.of(SoulBuffTypes.REACH, 2.0d),
                BuffSettings.DEFAULT_ENTRY_FRACTION, BuffSettings.DEFAULT_RAMP_EXPONENT);

        assertTrue(
                olderConfig.capFor(SoulBuffTypes.AEGIS) >= 12.0d,
                "an unlisted active must fall back to its built-in ceiling rather than to the fraction default");

        assertTrue(
                olderConfig.capFor("soulhome:not_a_buff") == 1.0d,
                "a type nothing knows about still falls through to global_max_magnitude");
    }
}
