/*
 * File created ~ 26 - 8 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.ArchetypeScore;
import leaf.soulhome.structures.core.BlockCounts;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.RegionGeometry;
import leaf.soulhome.structures.core.RegionType;
import leaf.soulhome.structures.core.SoulRegion;
import leaf.soulhome.structures.core.TestBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arrangement half of the report (#33): the clause tree {@code ArchetypeClassifier} already
 * evaluated has to survive translation into something a player can act on, without the report
 * inventing a single number of its own - every assertion here checks a rendered line against a
 * field already sitting on the fixture, never against something recomputed.
 */
class SoulReportTest
{
    @Test
    @DisplayName("a credited form renders its name and score, and one line per clause it flattens")
    void hitFormFlattensItsClauses()
    {
        ArchetypeScore.ClauseEvaluation hitLeaf =
                leaf("surrounds", "'seating' surrounds 'fire'", 1.0d, "");
        ArchetypeScore.ClauseEvaluation missLeaf =
                leaf("at_range", "light is near the fire", 0.0d, "no light within 6 of the fire");
        ArchetypeScore.ClauseEvaluation root =
                new ArchetypeScore.ClauseEvaluation("all", "combined", 0.5d, "", List.of(hitLeaf, missLeaf), -1);

        ArchetypeScore.StructureContribution gathering =
                new ArchetypeScore.StructureContribution("gathering", "arrangement", 5d, 0.46d, 2.3d, root);

        List<Component> lines = SoulReport.region(classified(gathering, List.of(), false, plainRegion()), 1);

        TranslatableContents formLine = findByKey(lines, Constants.StringKeys.REGION_STRUCTURE).orElseThrow();
        assertEquals("gathering", formLine.getArgs()[0]);
        assertEquals("2.3", formLine.getArgs()[1]);

        TranslatableContents hit = findByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_HIT).orElseThrow();
        assertEquals("'seating' surrounds 'fire'", hit.getArgs()[0], "a fully-satisfied clause needs no diagnostic tacked on");

        TranslatableContents miss = findByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_MISS).orElseThrow();
        assertEquals("light is near the fire - no light within 6 of the fire", miss.getArgs()[0]);
    }

    @Test
    @DisplayName("an any that scored names only its winning alternative")
    void anyRendersOnlyTheWinner()
    {
        ArchetypeScore.ClauseEvaluation under = leaf("above", "ice runs under the rails", 0d, "not under the rails");
        ArchetypeScore.ClauseEvaluation around = leaf("surrounds", "ice rings the rails", 0.9d, "");

        ArchetypeScore.ClauseEvaluation any =
                new ArchetypeScore.ClauseEvaluation("any", "combined", 0.9d, "", List.of(under, around), 1);

        ArchetypeScore.StructureContribution circuit =
                new ArchetypeScore.StructureContribution("circuit", "structure", 8d, 0.9d, 7.2d, any);

        List<Component> lines = SoulReport.region(classified(circuit, List.of(), false, plainRegion()), 1);

        assertEquals(1, countByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_HIT) + countByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_MISS),
                "only the winning alternative should render, not the one that lost");

        TranslatableContents winner = findByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_HIT).orElseThrow();
        assertEquals("ice rings the rails", winner.getArgs()[0]);
    }

    @Test
    @DisplayName("an any that scored zero lists every alternative it tried")
    void anyRendersAllAlternativesWhenNoneMatched()
    {
        ArchetypeScore.ClauseEvaluation under = leaf("above", "ice runs under the rails", 0d, "not under the rails");
        ArchetypeScore.ClauseEvaluation around = leaf("surrounds", "ice rings the rails", 0d, "not around the rails");
        ArchetypeScore.ClauseEvaluation inside = leaf("inside", "ice fills the rails", 0d, "not inside the rails");

        ArchetypeScore.ClauseEvaluation any =
                new ArchetypeScore.ClauseEvaluation("any", "combined", 0d, "", List.of(under, around, inside), -1);

        ArchetypeScore.StructureContribution circuit =
                new ArchetypeScore.StructureContribution("circuit", "structure", 8d, 0d, 0d, any);

        List<Component> lines = SoulReport.region(classified(null, List.of(circuit), false, plainRegion()), 1);

        assertEquals(3, countByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_MISS),
                "a dead-end any should read as three ways forward, not one");
    }

    @Test
    @DisplayName("a clause naming an uninstalled mod's type renders as skipped, not failed")
    void unknownClauseRendersAsSkipped()
    {
        ArchetypeScore.ClauseEvaluation unknown =
                new ArchetypeScore.ClauseEvaluation("unknown:othermod:their_clause", "", 0d, "", List.of(), -1);

        ArchetypeScore.StructureContribution form =
                new ArchetypeScore.StructureContribution("gathering", "arrangement", 5d, 0d, 0d, unknown);

        List<Component> lines = SoulReport.region(classified(null, List.of(form), false, plainRegion()), 1);

        assertEquals(0, countByKey(lines, Constants.StringKeys.REGION_STRUCTURE_CLAUSE_MISS),
                "a skipped clause must never be counted among the failed ones");

        TranslatableContents skipped = findByKey(lines, Constants.StringKeys.REGION_STRUCTURE_SKIPPED).orElseThrow();
        assertEquals("othermod:their_clause", skipped.getArgs()[0]);
    }

    @Test
    @DisplayName("structural credit held at the cap says so, and only then")
    void cappedNoticeAppearsOnlyWhenCapped()
    {
        ArchetypeScore.ClauseEvaluation hitLeaf = leaf("surrounds", "seating rings the fire", 1d, "");
        ArchetypeScore.StructureContribution form =
                new ArchetypeScore.StructureContribution("gathering", "arrangement", 5d, 1d, 5d, hitLeaf);

        List<Component> uncapped = SoulReport.region(classified(form, List.of(), false, plainRegion()), 1);
        assertTrue(findByKey(uncapped, Constants.StringKeys.REGION_STRUCTURE_CAPPED).isEmpty());

        List<Component> capped = SoulReport.region(classified(form, List.of(), true, plainRegion()), 1);
        assertTrue(findByKey(capped, Constants.StringKeys.REGION_STRUCTURE_CAPPED).isPresent());
    }

    @Test
    @DisplayName("a truncated geometry says so, rather than reading as an unexplained miss")
    void truncatedGeometryIsExplained()
    {
        ArchetypeScore.ClauseEvaluation hitLeaf = leaf("surrounds", "seating rings the fire", 1d, "");
        ArchetypeScore.StructureContribution form =
                new ArchetypeScore.StructureContribution("gathering", "arrangement", 5d, 1d, 5d, hitLeaf);

        List<Component> untruncated = SoulReport.region(classified(form, List.of(), false, plainRegion()), 1);
        assertTrue(findByKey(untruncated, Constants.StringKeys.REGION_STRUCTURE_TRUNCATED).isEmpty());

        List<Component> truncated = SoulReport.region(classified(form, List.of(), false, truncatedRegion()), 1);
        assertTrue(findByKey(truncated, Constants.StringKeys.REGION_STRUCTURE_TRUNCATED).isPresent());
    }

    @Test
    @DisplayName("an archetype with no forms at all shows no arrangement section")
    void noFormsMeansNoSection()
    {
        List<Component> lines = SoulReport.region(classified(null, List.of(), false, plainRegion()), 1);

        assertTrue(findByKey(lines, Constants.StringKeys.REGION_STRUCTURE_HEADER).isEmpty());
    }

    // region helpers

    private static ArchetypeScore.ClauseEvaluation leaf(String typeId, String description, double confidence, String diagnostic)
    {
        return new ArchetypeScore.ClauseEvaluation(typeId, description, confidence, diagnostic, List.of(), -1);
    }

    private static ClassificationResult classified(
            ArchetypeScore.StructureContribution hit,
            List<ArchetypeScore.StructureContribution> misses,
            boolean capped,
            SoulRegion region)
    {
        ArchetypeScore best = new ArchetypeScore(
                "soulhome:test", "archetype.soulhome.test", 10d, 10d, 1d, 1d, 1,
                OptionalDouble.empty(), List.of(), List.of(), List.of(), null,
                hit == null ? List.of() : List.of(hit), misses, capped);

        return new ClassificationResult(region, ClassificationResult.Status.CLASSIFIED, best, null, List.of(best));
    }

    private static SoulRegion plainRegion()
    {
        return SoulRegion.create(
                RegionType.ENCLOSED, new RegionBounds(0, 0, 0, 3, 3, 3), BlockCounts.empty(), BlockCounts.empty(), 27);
    }

    private static SoulRegion truncatedRegion()
    {
        RegionGeometry geometry = RegionGeometry.builder(0).add(0, 0, 0, TestBlocks.BOOKSHELF).build();

        return SoulRegion.create(
                RegionType.ENCLOSED, new RegionBounds(0, 0, 0, 3, 3, 3),
                BlockCounts.empty(), BlockCounts.empty(), 27, geometry);
    }

    private static List<TranslatableContents> allTranslatables(List<Component> lines)
    {
        List<TranslatableContents> found = new ArrayList<>();

        for (Component line : lines)
        {
            collectTranslatables(line, found);
        }

        return found;
    }

    private static void collectTranslatables(Component component, List<TranslatableContents> found)
    {
        if (component.getContents() instanceof TranslatableContents translatable)
        {
            found.add(translatable);
        }

        for (Component sibling : component.getSiblings())
        {
            collectTranslatables(sibling, found);
        }
    }

    private static Optional<TranslatableContents> findByKey(List<Component> lines, String key)
    {
        return allTranslatables(lines).stream().filter(t -> t.getKey().equals(key)).findFirst();
    }

    private static long countByKey(List<Component> lines, String key)
    {
        return allTranslatables(lines).stream().filter(t -> t.getKey().equals(key)).count();
    }

    // endregion
}
