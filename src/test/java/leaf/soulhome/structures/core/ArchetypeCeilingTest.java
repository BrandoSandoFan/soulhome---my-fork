/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #47/#107: a tier threshold above what any build could ever score is not a stretch goal, it is a
 * silent lie - and, because {@link ArchetypeDefinition.BuffSpec#magnitudeAt} ramps the buff
 * against the top of the tier ladder, an unreachable top tier quietly caps every buff in the mod
 * well under its advertised maximum too. This is the regression test {@code ArchetypeCeiling}'s
 * class javadoc promises: it fails the moment a shipped archetype's own thresholds outrun what the
 * classifier could ever award it.
 *
 * <p>#107 found the opposite failure hiding behind the same number: an upper bound alone never
 * catches a tier that is too <i>cheap</i>. Checked against the shipped archetypes, that failure
 * wasn't random - the newer, active-ability rooms paid out their full buff for an easy build while
 * the older, passive-buff rooms demanded one nearly perfect for the same relative payout. Both
 * tests below now check a band, not just a ceiling.
 */
class ArchetypeCeilingTest
{
    /** Tier 3 must land in this fraction of the ceiling - reachable, but not for a mediocre build. */
    private static final double TIER_3_BAND_LOW = 0.72d;
    private static final double TIER_3_BAND_HIGH = 0.85d;

    /** Tier 2 must land in this fraction of the ceiling - a solid effort, not a near-maxed build. */
    private static final double TIER_2_BAND_LOW = 0.40d;
    private static final double TIER_2_BAND_HIGH = 0.60d;

    @Test
    @DisplayName("every shipped archetype's top tier sits in the intended reachability band, not just under the ceiling")
    void topTierIsInBand() throws IOException
    {
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            if (archetype.tiers().isEmpty())
            {
                continue;
            }

            final double ceiling = ArchetypeCeiling.of(archetype, ScoringSettings.DEFAULTS);
            final double topTier = archetype.tiers().get(archetype.tiers().size() - 1).minScore();
            final double ratio = topTier / ceiling;

            assertTrue(
                    ratio >= TIER_3_BAND_LOW && ratio <= TIER_3_BAND_HIGH,
                    archetype.id() + ": tier 3 (" + topTier + ") is " + ratio + " of its ceiling ("
                            + ceiling + "), outside the intended [" + TIER_3_BAND_LOW + ", " + TIER_3_BAND_HIGH
                            + "] band - either unreachable without nailing every form, or so cheap the room"
                            + " pays out its full buff for a mediocre build");
        }
    }

    @Test
    @DisplayName("every shipped archetype's tier 2 sits around half its ceiling, not nearly all of it or barely any")
    void tier2IsInBand() throws IOException
    {
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            if (archetype.tiers().size() < 2)
            {
                continue;
            }

            final double ceiling = ArchetypeCeiling.of(archetype, ScoringSettings.DEFAULTS);
            final double secondTier = archetype.tiers().get(1).minScore();
            final double ratio = secondTier / ceiling;

            assertTrue(
                    ratio >= TIER_2_BAND_LOW && ratio <= TIER_2_BAND_HIGH,
                    archetype.id() + ": tier 2 (" + secondTier + ") is " + ratio + " of its ceiling ("
                            + ceiling + "), outside the intended [" + TIER_2_BAND_LOW + ", " + TIER_2_BAND_HIGH
                            + "] band");
        }
    }

    @Test
    @DisplayName("a mistuned threshold above the ceiling fails, rather than shipping")
    void mistunedThresholdIsCaught()
    {
        ArchetypeDefinition unreachable = new ArchetypeDefinition(
                "soulhome:test_unreachable", "archetype.soulhome.test", List.of(), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BlockMatcher.ofBlocks("minecraft:torch"), 1.0, "light", 4)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(1.0, 1), new ArchetypeDefinition.Tier(1000.0, 3)),
                List.of(),
                List.of());

        final double ceiling = ArchetypeCeiling.of(unreachable, ScoringSettings.DEFAULTS);

        assertTrue(ceiling < 1000.0, "the fixture should not accidentally be reachable");
    }
}
