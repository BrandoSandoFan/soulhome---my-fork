/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import leaf.soulhome.structures.core.Form;

/**
 * Turns one {@link Form}'s clause tree into a player-facing sentence for the book, from
 * {@link leaf.soulhome.structures.core.FormClause#describe()} alone - never a switch over clause
 * types here, so a new clause type only needs its own {@code describe()} written, not this file
 * touched too. See #35 of the structural considerations epic (#25).
 *
 * <p>An {@code any} node's alternatives survive as "or" inside the same sentence, and an
 * {@code all} node's children join with "and" - both straight from
 * {@link leaf.soulhome.structures.core.AnyClause#describe()} and
 * {@link leaf.soulhome.structures.core.AllClause#describe()} - so the book can say "run them over
 * ice, or ring the track with it" instead of listing every alternative as if each were required.
 * Structure is optional evidence, never a gate (rule 1 of #25); nothing here says otherwise.
 */
public final class FormDocs
{
    private FormDocs()
    {
    }

    /**
     * One sentence describing how a form wants its elements arranged.
     *
     * @throws IllegalStateException if the form's clause tree has nothing to say - the same
     *                                fail-fast {@link ArchetypeDocs} uses for a datapack bug that
     *                                would otherwise ship as a silent gap in the book
     */
    public static String describe(Form form)
    {
        final String raw = form.root() == null ? "" : form.root().describe();

        if (raw == null || raw.isBlank())
        {
            throw new IllegalStateException(
                    "Form '" + form.name() + "' has a clause that cannot describe itself, so the "
                            + "book would silently say nothing about how to arrange it");
        }

        final String withoutQuotes = raw.replace("'", "");
        final String sentence = Character.toUpperCase(withoutQuotes.charAt(0)) + withoutQuotes.substring(1);

        return sentence.endsWith(".") ? sentence : sentence + ".";
    }
}
