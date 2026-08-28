/*
 * File created ~ 28 - 8 - 2026
 */

package leaf.soulhome.datagen.patchouli.categories;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #44: {@code book.json} runs the guide in Patchouli's {@code i18n} mode, which sends every
 * {@code name}, {@code description}, {@code text} and {@code title} through vanilla's
 * {@code I18n.get} - and that runs {@code String.format} over the raw string whether or not it is
 * actually a translation key. A generated page containing a lone {@code %} (a percentage buff, in
 * particular) used to throw inside vanilla and render as "Format error: ..." instead of the page
 * that was written.
 *
 * <p>This is the test the issue asked for: every field the book actually generates has to survive
 * {@code String.format} with no arguments, the same call vanilla makes.
 */
class PatchouliFormatSafetyTest
{
    @Test
    @DisplayName("every generated category and entry field survives String.format, the same call vanilla's i18n makes")
    void generatedBookTextSurvivesFormat()
    {
        List<BookStuff.Category> categories = new ArrayList<>();
        List<BookStuff.Entry> entries = new ArrayList<>();

        PatchouliBasics.collect(categories, entries);
        PatchouliMultiblocks.collect(categories, entries);

        for (BookStuff.Category category : categories)
        {
            JsonObject json = category.serialize().getAsJsonObject();
            assertFormattable("category '" + category.name + "' name", json.get("name").getAsString());
            assertFormattable("category '" + category.name + "' description", json.get("description").getAsString());
        }

        for (BookStuff.Entry entry : entries)
        {
            JsonObject json = entry.serialize("soulhome").getAsJsonObject();
            assertFormattable("entry '" + entry.name + "' name", json.get("name").getAsString());

            JsonArray pages = json.getAsJsonArray("pages");

            for (int i = 0; i < pages.size(); i++)
            {
                JsonObject page = pages.get(i).getAsJsonObject();

                if (page.has("text"))
                {
                    assertFormattable("entry '" + entry.name + "' page " + i + " text", page.get("text").getAsString());
                }

                if (page.has("title"))
                {
                    assertFormattable("entry '" + entry.name + "' page " + i + " title", page.get("title").getAsString());
                }
            }
        }
    }

    @Test
    @DisplayName("a percentage buff and a flat buff both round-trip into a page that survives String.format")
    void percentageAndFlatBuffsBothSurvive()
    {
        // alchemy_lab grants a fractional buff ("up to +45%"); hearth grants a flat one - between
        // them every archetype entry exercises PatchouliMultiblocks#magnitude's two branches
        List<BookStuff.Category> categories = new ArrayList<>();
        List<BookStuff.Entry> entries = new ArrayList<>();
        PatchouliMultiblocks.collect(categories, entries);

        boolean sawPercentSign = false;

        for (BookStuff.Entry entry : entries)
        {
            for (BookStuff.Page page : entry.pages)
            {
                if (page.text.contains("%"))
                {
                    sawPercentSign = true;
                }

                assertFormattable("entry '" + entry.name + "' page text", page.serialize().getAsJsonObject().get("text").getAsString());
            }
        }

        assertTrue(sawPercentSign,
                "this test is only meaningful if a shipped archetype still generates a literal '%' - "
                        + "if none do any more, the regression this guards against can no longer happen either");
    }

    private static void assertFormattable(String what, String text)
    {
        assertDoesNotThrow(() -> String.format(Locale.ROOT, text),
                () -> what + " does not survive String.format: \"" + text + "\"");
    }
}
