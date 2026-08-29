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
 * #47: a tier threshold above what any build could ever score is not a stretch goal, it is a
 * silent lie - and, because {@link ArchetypeDefinition.BuffSpec#magnitudeAt} ramps the buff
 * against the top of the tier ladder, an unreachable top tier quietly caps every buff in the mod
 * well under its advertised maximum too. This is the regression test {@code ArchetypeCeiling}'s
 * class javadoc promises: it fails the moment a shipped archetype's own thresholds outrun what the
 * classifier could ever award it.
 */
class ArchetypeCeilingTest
{
    /** Tier 3 must sit far enough under the ceiling that nailing every form is not also required. */
    private static final double TOP_TIER_MARGIN = 1.1d;

    @Test
    @DisplayName("every shipped archetype's top tier is below its own score ceiling, with margin")
    void topTierIsReachable() throws IOException
    {
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            if (archetype.tiers().isEmpty())
            {
                continue;
            }

            final double ceiling = ArchetypeCeiling.of(archetype, ScoringSettings.DEFAULTS);
            final double topTier = archetype.tiers().get(archetype.tiers().size() - 1).minScore();

            assertTrue(
                    ceiling >= topTier * TOP_TIER_MARGIN,
                    archetype.id() + ": ceiling " + ceiling + " does not clear its top tier " + topTier
                            + " by the required " + TOP_TIER_MARGIN + "x margin");
        }
    }

    @Test
    @DisplayName("every shipped archetype's tier 2 sits around half its ceiling, not nearly all of it")
    void tier2IsNotAMaxedBuild() throws IOException
    {
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            if (archetype.tiers().size() < 2)
            {
                continue;
            }

            final double ceiling = ArchetypeCeiling.of(archetype, ScoringSettings.DEFAULTS);
            final double secondTier = archetype.tiers().get(1).minScore();

            assertTrue(
                    secondTier <= ceiling * 0.6d,
                    archetype.id() + ": tier 2 (" + secondTier + ") asks for more than 60% of the ceiling ("
                            + ceiling + "), which is close enough to a maxed-out build");
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
