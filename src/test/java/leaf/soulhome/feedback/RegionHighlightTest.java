/*
 * File created ~ 28 - 8 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.BlockCounts;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.RegionType;
import leaf.soulhome.structures.core.SoulRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #46: an empty region has every archetype score 0.0, so {@code ClassificationResult.best} is
 * only the alphabetically-first archetype id, not a name the client should ever be told is this
 * region's title - see {@link ClassificationResult}'s own javadoc on {@code best}. Confirms
 * {@link RegionHighlight#of} stops that tie-break winner from leaking to the Soul Lens overlay,
 * and that a genuine near miss (AMBIGUOUS, with a positive score) is carried as its own field
 * rather than as the region's title.
 */
class RegionHighlightTest
{
    @Test
    @DisplayName("an UNCLASSIFIED result carries no archetype name, no tier and no near miss")
    void unclassifiedCarriesNoName()
    {
        ArchetypeScore tieBreakWinner = score("soulhome:alchemy_lab", 0d, 0);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.UNCLASSIFIED, tieBreakWinner, null, List.of(tieBreakWinner));

        RegionHighlight highlight = RegionHighlight.of(result);

        assertEquals("", highlight.archetypeId());
        assertEquals("", highlight.displayName());
        assertEquals(0, highlight.tier());
        assertTrue(highlight.displayName().isBlank(), "a blank name is what makes the renderer fall back to \"Not anything yet\"");
        assertEquals("", highlight.nearMissName());
        assertFalse(highlight.hasNearMiss(), "every archetype scored 0.0, so there is no closest candidate worth naming");
    }

    @Test
    @DisplayName("a CLASSIFIED result carries the awarded archetype's name and tier, and no near miss")
    void classifiedCarriesItsName()
    {
        ArchetypeScore awarded = score("soulhome:library", 42d, 2);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.CLASSIFIED, awarded, null, List.of(awarded));

        RegionHighlight highlight = RegionHighlight.of(result);

        assertEquals("soulhome:library", highlight.archetypeId());
        assertEquals("archetype.soulhome.library", highlight.displayName());
        assertEquals(2, highlight.tier());
        assertEquals("", highlight.nearMissName());
        assertFalse(highlight.hasNearMiss(), "an awarded region has nothing to call a near miss");
    }

    @Test
    @DisplayName("an AMBIGUOUS result carries no title, but names the top candidate as a near miss")
    void ambiguousCarriesTheTopCandidateAsANearMissOnly()
    {
        ArchetypeScore best = score("soulhome:library", 30d, 1);
        ArchetypeScore runnerUp = score("soulhome:enchanting_room", 29d, 1);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.AMBIGUOUS, best, runnerUp, List.of(best, runnerUp));

        RegionHighlight highlight = RegionHighlight.of(result);

        assertEquals("", highlight.archetypeId(), "AMBIGUOUS is not an award, so it carries no archetype id");
        assertEquals("", highlight.displayName(), "the near miss must never be sent as this region's title (#46)");
        assertEquals(0, highlight.tier());
        assertEquals("archetype.soulhome.library", highlight.nearMissName());
        assertTrue(highlight.hasNearMiss());
    }

    private static ArchetypeScore score(String archetypeId, double value, int tier)
    {
        return new ArchetypeScore(
                archetypeId, "archetype." + archetypeId.replace(':', '.'), value, value, 1d, 1d, tier,
                OptionalDouble.empty(), List.of(), List.of(), List.of(), null, List.of(), List.of(), false);
    }

    private static SoulRegion plainRegion()
    {
        return SoulRegion.create(
                RegionType.ENCLOSED, new RegionBounds(0, 0, 0, 3, 3, 3), BlockCounts.empty(), BlockCounts.empty(), 27);
    }
}
