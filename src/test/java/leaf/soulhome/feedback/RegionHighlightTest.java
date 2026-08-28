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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #46: an empty region has every archetype score 0.0, so {@code ClassificationResult.best} is
 * only the alphabetically-first archetype id, not a name the client should ever be told is this
 * region's title - see {@link ClassificationResult}'s own javadoc on {@code best}. Confirms
 * {@link RegionHighlight#of} stops that tie-break winner from leaking to the Soul Lens overlay.
 */
class RegionHighlightTest
{
    @Test
    @DisplayName("an UNCLASSIFIED result carries no archetype name and no tier")
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
    }

    @Test
    @DisplayName("a CLASSIFIED result carries the awarded archetype's name and tier")
    void classifiedCarriesItsName()
    {
        ArchetypeScore awarded = score("soulhome:library", 42d, 2);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.CLASSIFIED, awarded, null, List.of(awarded));

        RegionHighlight highlight = RegionHighlight.of(result);

        assertEquals("soulhome:library", highlight.archetypeId());
        assertEquals("archetype.soulhome.library", highlight.displayName());
        assertEquals(2, highlight.tier());
    }

    @Test
    @DisplayName("an AMBIGUOUS result names the top candidate rather than reading as empty")
    void ambiguousKeepsTheTopCandidateName()
    {
        ArchetypeScore best = score("soulhome:library", 30d, 1);
        ArchetypeScore runnerUp = score("soulhome:enchanting_room", 29d, 1);

        ClassificationResult result = new ClassificationResult(
                plainRegion(), ClassificationResult.Status.AMBIGUOUS, best, runnerUp, List.of(best, runnerUp));

        RegionHighlight highlight = RegionHighlight.of(result);

        assertEquals("archetype.soulhome.library", highlight.displayName());
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
