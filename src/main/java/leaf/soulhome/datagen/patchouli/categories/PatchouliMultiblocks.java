/*
 * File created ~ 14 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.patchouli.categories;


import leaf.soulhome.datagen.patchouli.categories.data.ArchetypeDocs;
import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import leaf.soulhome.datagen.patchouli.categories.data.FormDocs;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.Form;
import leaf.soulhome.structures.core.RegionType;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.utils.StringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The rooms you can build inside your soul.
 *
 * <p>Every entry past the introduction is written from the archetype JSON at generation time, so
 * the book cannot tell a player something the classifier does not actually do. When a weight or a
 * tier threshold changes, the page changes with it.
 *
 * <p>The introduction earns its place: the single most likely way for this feature to be
 * misunderstood is a player assuming these pages are schematics to copy. They are not, and the
 * book has to say so before it says anything else, or the whole reason the system is fuzzy is
 * lost.
 */
public class PatchouliMultiblocks
{
    /** Everything here is readable once you have been inside your own soul. */
    private static final String ADVANCEMENT = "soulhome:main/entered_soul_dimension";

    /** How many signals a page lists before it stops being a hint and starts being a checklist. */
    private static final int LISTED_SIGNALS = 5;

    public static void collect(List<BookStuff.Category> categories, List<BookStuff.Entry> entries)
    {
        BookStuff.Category multiblocks = new BookStuff.Category(
                "multiblocks",
                "Rooms you can build within your soul that will grant you power. Not blueprints - build your own.",
                "soulhome:soul_lens");

        //the file keeps its old name so nothing that points at it has to move, but a category
        //called "Multiblocks" would tell a reader the exact opposite of how this works
        multiblocks.setDisplayTitle("soul rooms");
        multiblocks.sortnum = 1;

        categories.add(multiblocks);

        entries.add(introduction(multiblocks));

        int sortnum = 0;

        for (ArchetypeDefinition archetype : ArchetypeDocs.shipped())
        {
            entries.add(entryFor(multiblocks, archetype, sortnum++));
        }
    }

    private static BookStuff.Entry introduction(BookStuff.Category category)
    {
        BookStuff.Entry rooms = new BookStuff.Entry("rooms", category, "soulhome:soul_lens");

        rooms.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage(
                                "Build a room in your soul and the world outside starts to treat you differently. A library sharpens what you learn; a farm makes every meal go further.$(p)There is no blueprint. Nothing here is a structure to copy block for block."),
                        new BookStuff.TextPage(
                                "Your soul is looked over now and then, and each enclosed room - and each dense patch of open ground - is judged on what it is made of. Two libraries that look nothing alike can both be libraries.$(p)Variety counts for more than volume. A hundred bookshelves in a box is not a library; shelves, a lectern, somewhere to sit and something to read by is."),
                        new BookStuff.TextPage(
                                "Some rooms also notice how what they hold is placed, not only what it is - shelves gathered around a reading nook count for more than the same shelves scattered flat.$(p)This is never required, only rewarded, and there is still no one right layout. A page that says how a room can be arranged is telling you one way among several, not the way."),
                        new BookStuff.TextPage(
                                "If a room does not count, ask. $(l)/soulhome analyse$() tells you what was seen in every room, what was missing, and what it nearly was.$(p)The $(item)Soul Lens$(0) does the same, and draws the edges of what it found - which is worth seeing, because a wall you thought was there might not be.")
                                .setTitle("When it does not work"),
                }; // end pages

        rooms.priority = true;
        rooms.sortnum = -10;
        rooms.advancement = ADVANCEMENT;
        rooms.read_by_default = true;

        return rooms;
    }

    /**
     * One archetype's page, written from its definition: what it insists on, what it rewards, and
     * what it gives back.
     */
    private static BookStuff.Entry entryFor(BookStuff.Category category, ArchetypeDefinition archetype, int sortnum)
    {
        final String path = ArchetypeDocs.pathOf(archetype);

        BookStuff.Entry entry = new BookStuff.Entry(path, category, iconFor(archetype, category));

        List<BookStuff.Page> pages = new ArrayList<>();
        pages.add(new BookStuff.TextPage(whereItGoes(archetype) + rewards(archetype)));
        pages.add(new BookStuff.TextPage(mustHave(archetype) + looksFor(archetype)).setTitle("What counts"));
        arrangementPage(archetype).ifPresent(pages::add);

        entry.sortnum = sortnum;
        entry.advancement = ADVANCEMENT;
        entry.pages = pages.toArray(BookStuff.Page[]::new);

        return entry;
    }

    /**
     * The "how you arrange it" page, present only for an archetype that ships at least one
     * {@link Form} - #35 of the structural considerations epic (#25). Kept separate from
     * {@link #entryFor} so a test can check the presence/absence rule without rendering a whole
     * entry.
     */
    static Optional<BookStuff.Page> arrangementPage(ArchetypeDefinition archetype)
    {
        if (archetype.structures().isEmpty())
        {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder(
                "None of this is required, but building it well earns more than the blocks alone:");

        for (Form form : archetype.structures())
        {
            text.append("$(li)").append(FormDocs.describe(form));
        }

        return Optional.of(new BookStuff.TextPage(text.toString()).setTitle("How you arrange it"));
    }

    private static String whereItGoes(ArchetypeDefinition archetype)
    {
        final boolean enclosed = archetype.regionTypes().contains(RegionType.ENCLOSED);
        final boolean open = archetype.regionTypes().contains(RegionType.OPEN);

        final String where;

        if (enclosed && open)
        {
            where = "Works as a room or as open ground.";
        }
        else if (open)
        {
            where = "Wants open ground rather than a sealed room.";
        }
        else
        {
            where = "Wants a room: walls, a floor and a ceiling, with no way out to the sky.";
        }

        return where + " At least " + archetype.minVolume() + " blocks of space.$(p)";
    }

    private static String rewards(ArchetypeDefinition archetype)
    {
        if (archetype.buffs().isEmpty())
        {
            return "Grants nothing on its own.";
        }

        StringBuilder text = new StringBuilder("Grants:");

        for (ArchetypeDefinition.BuffSpec buff : archetype.buffs())
        {
            text.append("$(li)")
                    .append(describeBuff(buff.type()))
                    .append(", growing with the tier, up to ")
                    .append(magnitude(buff.type(), buff.max()))
                    .append('.');
        }

        if (!archetype.tiers().isEmpty())
        {
            text.append("$(p)Tiers begin at a score of ")
                    .append(trim(archetype.tiers().get(0).minScore()))
                    .append('.');
        }

        return text.toString();
    }

    private static String mustHave(ArchetypeDefinition archetype)
    {
        if (archetype.requirements().isEmpty())
        {
            return "Nothing is strictly required.$(p)";
        }

        StringBuilder text = new StringBuilder("Will not count without:");

        for (ArchetypeDefinition.Requirement requirement : archetype.requirements())
        {
            text.append("$(li)")
                    .append(requirement.minCount())
                    .append(" of ")
                    .append(readable(requirement.match().describe()));
        }

        return text.append("$(p)").toString();
    }

    private static String looksFor(ArchetypeDefinition archetype)
    {
        StringBuilder text = new StringBuilder("Rewards, most first:");

        List<ArchetypeDefinition.Signal> signals = new ArrayList<>(archetype.signals());
        signals.sort((left, right) -> Double.compare(right.weight(), left.weight()));

        int listed = 0;

        for (ArchetypeDefinition.Signal signal : signals)
        {
            if (listed++ >= LISTED_SIGNALS)
            {
                break;
            }

            text.append("$(li)").append(readable(signal.match().describe()));
        }

        if (!archetype.detractors().isEmpty())
        {
            text.append("$(p)Counts against it:");

            for (ArchetypeDefinition.Signal detractor : archetype.detractors())
            {
                text.append("$(li)").append(readable(detractor.match().describe()));
            }
        }

        return text.toString();
    }

    /**
     * The first plain block an archetype names, which is a better icon than the category's own -
     * and is chosen from the data rather than being another thing to keep in step by hand.
     */
    private static String iconFor(ArchetypeDefinition archetype, BookStuff.Category category)
    {
        for (ArchetypeDefinition.Signal signal : archetype.signals())
        {
            if (!signal.match().blocks().isEmpty())
            {
                return signal.match().blocks().get(0);
            }
        }

        return category.icon;
    }

    /** {@code soulhome:xp_gain} to "experience gain", for prose rather than for a log line. */
    private static String describeBuff(String buffType)
    {
        final int separator = buffType.indexOf(':');
        final String path = separator < 0 ? buffType : buffType.substring(separator + 1);

        return path.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /**
     * Ids as a player should read them: {@code #minecraft:bookshelves} becomes "any bookshelves",
     * {@code minecraft:lectern} becomes "Lectern".
     */
    private static String readable(String description)
    {
        StringBuilder readable = new StringBuilder();

        for (String part : description.split(" or "))
        {
            if (readable.length() > 0)
            {
                readable.append(" or ");
            }

            final boolean tag = part.startsWith("#");
            final String trimmed = tag ? part.substring(1) : part;
            final int separator = trimmed.indexOf(':');
            final String path = separator < 0 ? trimmed : trimmed.substring(separator + 1);

            readable.append(tag ? "any " + path.replace('_', ' ') : StringHelper.fixCapitalisation(path));
        }

        return readable.toString();
    }

    /**
     * A magnitude with its unit. Which unit that is comes from {@link SoulBuffTypes}, the same
     * place the chat report asks, so the book and the game never disagree about whether 6 means
     * six levels or six hundred percent.
     */
    private static String magnitude(String buffType, double value)
    {
        return SoulBuffTypes.isFraction(buffType)
                ? "+" + trim(value * 100d) + "%"
                : "+" + trim(value);
    }

    /** Drops a trailing {@code .0} so a whole number reads as one. */
    private static String trim(double value)
    {
        //a hair of tolerance, since 0.30 * 100 is not exactly 30 in binary
        return Math.abs(value - Math.rint(value)) < 1.0e-6d
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
