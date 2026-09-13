/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The load-time rules aspects are held to - #172 of the Aspects epic (#171).
 *
 * <p>The one that matters most is rule 2: every block an aspect reads is a block the room already
 * scores. Left to authors to remember, it would be forgotten, and the result is not a crash but
 * something worse - a player choosing between a better room and the buff they wanted, which is the
 * exact tension the rule exists to remove.
 */
class AspectValidationTest
{
    private static final BlockMatcher BOOKSHELVES = BlockMatcher.ofTags("soulhome:bookshelves");
    private static final BlockMatcher LECTERN = BlockMatcher.ofBlocks("minecraft:lectern");
    private static final BlockMatcher ANVIL = BlockMatcher.ofBlocks("minecraft:anvil");

    @Test
    @DisplayName("an aspect leaning on a block the archetype does not score fails to load, naming archetype, aspect and matcher")
    void leaningOnAnUnscoredBlockIsMalformed()
    {
        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("smithy", false, List.of(new Aspect.Lean(ANVIL, 2d)), List.of(), List.of()));

        List<String> errors = archetype.validationErrors();

        assertEquals(1, errors.size(), "expected exactly one complaint, got " + errors);
        assertTrue(errors.get(0).contains("smithy"), errors.get(0));
        assertTrue(errors.get(0).contains("minecraft:anvil"), errors.get(0));
        assertTrue(errors.get(0).contains("without counting toward the room"), errors.get(0));
    }

    @Test
    @DisplayName("a tag an aspect leans on must be one the archetype scores too")
    void leaningOnAnUnscoredTagIsMalformed()
    {
        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("forge", false,
                        List.of(new Aspect.Lean(BlockMatcher.ofTags("soulhome:smithing"), 2d)),
                        List.of(), List.of()));

        assertTrue(archetype.validationErrors().stream()
                        .anyMatch(error -> error.contains("#soulhome:smithing")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("an aspect form's elements are held to the same rule - only the arrangement is the aspect's own")
    void aspectFormElementsMustBeSignalsToo()
    {
        Form rows = new Form(
                "rows", 1d, "structure", Map.of("x", ANVIL), FakeClause.of(1d), Set.of("x"));

        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("smithy", false, List.of(new Aspect.Lean(BOOKSHELVES, 1d)), List.of(rows), List.of()));

        assertTrue(archetype.validationErrors().stream()
                        .anyMatch(error -> error.contains("minecraft:anvil")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("an aspect form over blocks the room already scores is fine - the rows are the aspect's, the lecterns are the room's")
    void aspectFormOverScoredBlocksIsValid()
    {
        Form rows = new Form(
                "rows", 1d, "structure", Map.of("x", LECTERN), FakeClause.of(1d), Set.of("x"));

        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("scriptorium", false, List.of(new Aspect.Lean(LECTERN, 2d)), List.of(rows), List.of()));

        assertEquals(List.of(), archetype.validationErrors());
    }

    @Test
    @DisplayName("zero defaults is malformed")
    void zeroDefaultsIsMalformed()
    {
        ArchetypeDefinition archetype = withAspects(
                aspect("archive", false, List.of(new Aspect.Lean(BOOKSHELVES, 1d)), List.of(), List.of()),
                aspect("scriptorium", false, List.of(new Aspect.Lean(LECTERN, 1d)), List.of(), List.of()));

        assertTrue(archetype.validationErrors().stream().anyMatch(error -> error.contains("none is marked 'default'")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("two defaults is malformed")
    void twoDefaultsIsMalformed()
    {
        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("scriptorium", true, List.of(new Aspect.Lean(LECTERN, 1d)), List.of(), List.of()));

        assertTrue(archetype.validationErrors().stream().anyMatch(error -> error.contains("2 are marked 'default'")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("a single aspect is malformed - there is nothing to choose between")
    void oneAspectIsMalformed()
    {
        ArchetypeDefinition archetype = withAspects(defaultAspect("archive", BOOKSHELVES));

        assertTrue(archetype.validationErrors().stream().anyMatch(error -> error.contains("only one is declared")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("two aspects sharing an id is malformed")
    void duplicateIdsAreMalformed()
    {
        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("archive", false, List.of(new Aspect.Lean(LECTERN, 1d)), List.of(), List.of()));

        assertTrue(archetype.validationErrors().stream().anyMatch(error -> error.contains("duplicate aspect id")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("an aspect with nothing to say for itself is malformed, the default included")
    void anAspectWithNoEvidenceIsMalformed()
    {
        ArchetypeDefinition archetype = withAspects(
                aspect("archive", true, List.of(), List.of(), List.of()),
                aspect("scriptorium", false, List.of(new Aspect.Lean(LECTERN, 1d)), List.of(), List.of()));

        assertTrue(archetype.validationErrors().stream()
                        .anyMatch(error -> error.contains("neither 'leans' nor 'structures'")),
                archetype.validationErrors().toString());
    }

    @Test
    @DisplayName("a non-default aspect with no buffs of its own is a warning, not a rejection")
    void anAspectThatPaysTheSameIsOnlyAWarning()
    {
        ArchetypeDefinition archetype = withAspects(
                defaultAspect("archive", BOOKSHELVES),
                aspect("scriptorium", false, List.of(new Aspect.Lean(LECTERN, 1d)), List.of(), List.of()));

        assertEquals(List.of(), archetype.validationErrors());
        assertFalse(archetype.validationWarnings().isEmpty());
    }

    @Test
    @DisplayName("every shipped archetype still validates, aspects and all")
    void shippedArchetypesAreValid() throws IOException
    {
        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            assertEquals(List.of(), archetype.validationErrors(), archetype.id() + " is invalid");
        }
    }

    @Test
    @DisplayName("the shipped aspects each name a payout, and exactly one default apiece")
    void shippedAspectsAreWellFormed() throws IOException
    {
        List<String> withAspects = new ArrayList<>();

        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            if (archetype.aspects().isEmpty())
            {
                continue;
            }

            withAspects.add(archetype.id());

            assertEquals(1, archetype.aspects().stream().filter(Aspect::isDefault).count(),
                    archetype.id() + " must have exactly one default aspect");

            // the default pays the archetype's own buffs and declares none of its own; every other
            // one has to pay something different or taking it means nothing to a player
            for (Aspect aspect : archetype.aspects())
            {
                assertEquals(!aspect.isDefault(), aspect.paysItsOwn(),
                        archetype.id() + " aspect '" + aspect.id() + "'");
            }
        }

        assertEquals(List.of("soulhome:hearth", "soulhome:library", "soulhome:mine"), withAspects,
                "the shipped set of aspects is deliberately small (#174) - add a test case here when it grows");
    }

    private static ArchetypeDefinition withAspects(Aspect... aspects)
    {
        return new ArchetypeDefinition(
                "soulhome:test", "archetype.soulhome.test", List.of(RegionType.ENCLOSED), 1,
                List.of(),
                List.of(
                        new ArchetypeDefinition.Signal(BOOKSHELVES, 3d, "core", 32),
                        new ArchetypeDefinition.Signal(LECTERN, 5d, "core", 4)),
                List.of(),
                List.of(new ArchetypeDefinition.Tier(0.1d, 1)),
                List.of(new ArchetypeDefinition.BuffSpec("soulhome:xp_gain", 0.1d, 0.3d)),
                List.of(),
                List.of(),
                List.of(aspects));
    }

    private static Aspect defaultAspect(String id, BlockMatcher leansOn)
    {
        return aspect(id, true, List.of(new Aspect.Lean(leansOn, 1d)), List.of(), List.of());
    }

    private static Aspect aspect(
            String id, boolean isDefault, List<Aspect.Lean> leans, List<Form> structures,
            List<ArchetypeDefinition.BuffSpec> buffs)
    {
        return new Aspect(id, "aspect.soulhome.test." + id, isDefault, leans, structures, buffs);
    }
}
