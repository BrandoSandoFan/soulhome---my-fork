/*
 * File created ~ 27 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories.data;

import leaf.soulhome.structures.core.BlockMatcher;
import leaf.soulhome.structures.core.Form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

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
 *
 * <p><b>The sentence names elements, so something has to say what an element is.</b> A clause
 * describes itself as "'mast' runs in a line" - {@code mast} being the key on the left of the
 * form's {@code elements} map, and the matcher on the right the only thing that says which blocks
 * that means. Printing the sentence alone told a reader that a mast should run in a line while
 * leaving them to guess what a mast was; {@link #legend} is the other half.
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
        final String raw = raw(form);
        final String sentence = readable(raw);

        return sentence.endsWith(".") ? sentence : sentence + ".";
    }

    /**
     * What each element the sentence names is actually made of, in the order the sentence
     * introduces them, as "Mast: any conductive or any structural".
     *
     * <p>Ordered by first mention rather than by the map's own iteration order, which is a
     * {@code Map.copyOf} and therefore deliberately unstable between runs - a legend that
     * reshuffled itself every time data generation ran would show up as noise in every diff. An
     * element declared but never referenced is a datapack oddity rather than an error, and is
     * listed last.
     *
     * @param namer renders one matcher's description the way the surrounding page does - the book
     *              wants "any conductive" linked to its glossary page, and only the book knows how
     *              to write that link
     */
    public static List<String> legend(Form form, UnaryOperator<String> namer)
    {
        final String raw = raw(form);

        List<Map.Entry<String, BlockMatcher>> elements = new ArrayList<>(form.elements().entrySet());

        elements.sort(Comparator
                .comparingInt((Map.Entry<String, BlockMatcher> element) -> mention(raw, element.getKey()))
                .thenComparing(Map.Entry::getKey));

        List<String> legend = new ArrayList<>(elements.size());

        for (Map.Entry<String, BlockMatcher> element : elements)
        {
            legend.add(label(element.getKey()) + ": " + namer.apply(element.getValue().describe()));
        }

        return legend;
    }

    /** Where an element is first named in the raw clause tree, or last if it never is. */
    private static int mention(String raw, String element)
    {
        final int at = raw.indexOf("'" + element + "'");
        return at < 0 ? Integer.MAX_VALUE : at;
    }

    /** {@code way_up} to "Way up" - a JSON key is not a word, and the book should not print one. */
    public static String label(String element)
    {
        final String spaced = element.replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String raw(Form form)
    {
        final String raw = form.root() == null ? "" : form.root().describe();

        if (raw == null || raw.isBlank())
        {
            throw new IllegalStateException(
                    "Form '" + form.name() + "' has a clause that cannot describe itself, so the "
                            + "book would silently say nothing about how to arrange it");
        }

        return raw;
    }

    /**
     * The clause tree's own wording as prose: quotes off, underscores out, first letter up. An
     * element key is the only underscored text a clause ever emits, so replacing them here is
     * safe and saves every clause type having to remember to.
     */
    private static String readable(String raw)
    {
        final String words = raw.replace("'", "").replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
