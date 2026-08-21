/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Folds structural evidence into {@link ArchetypeClassifier#score}: #28 of the structural
 * considerations epic (#25). Every archetype here uses a {@link FakeClause} of a controlled
 * confidence in place of a real shape/relation clause - #28 ships no clause types of its own, so
 * there is nothing real to evaluate against yet, exactly as #27 shipped none either.
 */
class StructuralScoringTest
{
    private static final BlockMatcher BOOKSHELVES = BlockMatcher.ofTags("minecraft:bookshelves");

    @Test
    @DisplayName("a room with a form at confidence 1.0 outscores the identical room with the form absent")
    void perfectFormOutscoresNoForm()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());
        SoulRegion region = regionWithBookshelves(8);

        // a small form weight, well under the structural share cap even at full confidence - large
        // enough to move the score, not so large that capping swamps the difference between forms
        double withForm = classifier.score(region, archetype(1.0, "circuit", 1.0)).score();
        double withoutForm = classifier.score(region, archetypeWithoutForm()).score();

        assertTrue(withForm > withoutForm);
    }

    @Test
    @DisplayName("the same room with the form at 0.5 lands between the two - grading is continuous")
    void halfFormLandsBetween()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());
        SoulRegion region = regionWithBookshelves(8);

        double none = classifier.score(region, archetypeWithoutForm()).score();
        double half = classifier.score(region, archetype(0.5, "circuit", 1.0)).score();
        double full = classifier.score(region, archetype(1.0, "circuit", 1.0)).score();

        assertTrue(none < half, "none=" + none + " half=" + half);
        assertTrue(half < full, "half=" + half + " full=" + full);
    }

    @Test
    @DisplayName("structural credit is capped: many perfect forms cannot out-score a well-appointed room")
    void structuralShareIsCapped()
    {
        List<Form> tenPerfectForms = new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++)
        {
            // same role for every form on purpose - this test is about the structural share cap,
            // not the (separately-tested) diversity bonus, so distinct roles would confound it
            tenPerfectForms.add(form("form" + i, 1.0, "structure", 1.0));
        }

        ArchetypeDefinition sparse = new ArchetypeDefinition(
                "soulhome:sparse", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BOOKSHELVES, 1d, "core", 64)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)), List.of(),
                tenPerfectForms);

        ArchetypeDefinition wellAppointed = archetypeWithoutForm();

        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());

        // one lonely bookshelf for the sparse room's ten "perfect" forms; a proper room for the other
        double sparseScore = classifier.score(regionWithBookshelves(1), sparse).score();
        double wellAppointedScore = classifier.score(regionWithBookshelves(16), wellAppointed).score();

        assertTrue(sparseScore < wellAppointedScore,
                "ten perfect forms over almost nothing (" + sparseScore + ") must not beat a real room ("
                        + wellAppointedScore + ")");
    }

    @Test
    @DisplayName("a region with no signal blocks and a perfect form scores 0")
    void perfectFormOverNothingScoresZero()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());
        SoulRegion empty = regionWithBookshelves(0);

        ArchetypeScore score = classifier.score(empty, archetype(1.0, "circuit", 8.0));

        assertEquals(0d, score.score(), 1e-9);
    }

    @Test
    @DisplayName("forms below structuralRoleThreshold do not move the diversity multiplier")
    void belowThresholdDoesNotFeedDiversity()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());
        SoulRegion region = regionWithBookshelves(8);

        double belowThreshold = ScoringSettings.DEFAULTS.structuralRoleThreshold() - 0.05d;
        ArchetypeScore below = classifier.score(region, archetype(belowThreshold, "extra-role", 8.0));

        assertEquals(1.0, below.diversityMultiplier(), 1e-9,
                "a single signal role plus a below-threshold structural role should still be diversity 1.0");

        double aboveThreshold = ScoringSettings.DEFAULTS.structuralRoleThreshold() + 0.05d;
        ArchetypeScore above = classifier.score(region, archetype(aboveThreshold, "extra-role", 8.0));

        assertTrue(above.diversityMultiplier() > below.diversityMultiplier(),
                "an above-threshold structural role naming a new role should raise the diversity multiplier");
    }

    @Test
    @DisplayName("gated archetypes do not evaluate forms")
    void gatedArchetypesSkipForms()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());

        // min_volume of 1000 guarantees this region is gated
        ArchetypeDefinition gated = new ArchetypeDefinition(
                "soulhome:gated", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1000,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BOOKSHELVES, 1d, "core", 64)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)), List.of(),
                List.of(form("circuit", 1.0, "circuit", 8.0)));

        ArchetypeScore score = classifier.score(regionWithBookshelves(8), gated);

        assertTrue(score.isGated());
        assertEquals(0d, score.score(), 1e-9);
        assertEquals(List.of(), score.structuralContributions());
        assertEquals(List.of(), score.missingStructures());
    }

    @Test
    @DisplayName("a form scoring exactly zero is reported in missingStructures, not structuralContributions")
    void zeroConfidenceFormIsReportedAsMissing()
    {
        ArchetypeClassifier classifier = new ArchetypeClassifier(List.of());
        ArchetypeScore score = classifier.score(regionWithBookshelves(8), archetype(0.0, "circuit", 8.0));

        assertEquals(1, score.missingStructures().size());
        assertEquals(List.of(), score.structuralContributions());
    }

    private static ArchetypeDefinition archetype(double formConfidence, String formRole, double formWeight)
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BOOKSHELVES, 1d, "core", 64)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)), List.of(),
                List.of(form("circuit", formConfidence, formRole, formWeight)));
    }

    private static ArchetypeDefinition archetypeWithoutForm()
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(new ArchetypeDefinition.Signal(BOOKSHELVES, 1d, "core", 64)),
                List.of(), List.of(new ArchetypeDefinition.Tier(0.1d, 1)), List.of(),
                List.of());
    }

    private static Form form(String name, double confidence, String role, double weight)
    {
        return new Form(
                name, weight, role,
                Map.of("x", BOOKSHELVES),
                FakeClause.of(confidence),
                Set.of("x"));
    }

    private static SoulRegion regionWithBookshelves(int count)
    {
        BlockCounts.Builder contents = BlockCounts.builder();
        contents.add(TestBlocks.BOOKSHELF, count);

        return SoulRegion.create(
                RegionType.ENCLOSED,
                new RegionBounds(0, 0, 0, 4, 4, 4),
                BlockCounts.empty(),
                contents.build(),
                27);
    }
}
