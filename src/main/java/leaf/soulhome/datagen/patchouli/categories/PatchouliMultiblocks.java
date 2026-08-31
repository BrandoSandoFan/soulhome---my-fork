/*
 * File created ~ 14 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.patchouli.categories;


import leaf.soulhome.SoulHome;
import leaf.soulhome.datagen.patchouli.categories.data.ArchetypeDocs;
import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import leaf.soulhome.datagen.patchouli.categories.data.FormDocs;
import leaf.soulhome.datagen.patchouli.categories.data.TagDocs;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.BlockMatcher;
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
    /**
     * Most entries are readable as soon as you have been inside your own soul - the whole point
     * of a guide is to say what to build before you have built it. {@link #entryFor} does not use
     * this for an archetype {@link #needsAnotherMod} rules out.
     */
    private static final String ADVANCEMENT = "soulhome:main/entered_soul_dimension";

    /** How many signals a page lists before it stops being a hint and starts being a checklist. */
    private static final int LISTED_SIGNALS = 5;

    /**
     * Entries of a tag's glossary listing that fit on one Patchouli text page, once the header on
     * the first page and the closing note on the last are allowed for.
     */
    private static final int GLOSSARY_ENTRIES_PER_PAGE = 10;

    /**
     * Pages one tag's listing may take before it stops being a glossary and becomes a catalogue.
     * Long enough for every tag this mod ships today; a tag a pack has poured a hundred blocks
     * into gets a count of what was left out instead.
     */
    private static final int GLOSSARY_MAX_PAGES = 4;

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
        entries.add(tagsGlossary(multiblocks));

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
                                "Out in the open, a build gathers itself by reaching a few blocks through clear ground, and it stops at solid blocks. Two builds too close together read as one thing - holding both their blocks, and scoring as neither.$(p)So if a farm and a track are being read as one, leave a few blocks between them, or build a wall of full blocks. A fence or a low wall will not do it: those are parts of a build, not the edge of one.")
                                .setTitle("Where one thing ends"),
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
    static BookStuff.Entry entryFor(BookStuff.Category category, ArchetypeDefinition archetype, int sortnum)
    {
        final String path = ArchetypeDocs.pathOf(archetype);

        BookStuff.Entry entry = new BookStuff.Entry(path, category, iconFor(archetype, category));

        List<BookStuff.Page> pages = new ArrayList<>();
        pages.add(new BookStuff.TextPage(whereItGoes(archetype) + rewards(archetype)));
        pages.add(new BookStuff.TextPage(mustHave(archetype) + looksFor(archetype)).setTitle("What counts"));
        arrangementPage(archetype).ifPresent(pages::add);

        entry.sortnum = sortnum;
        //an archetype only another mod can ever satisfy is gated behind classifying one, which an
        //install without that mod can never do - see needsAnotherMod
        entry.advancement = needsAnotherMod(archetype) ? "soulhome:main/" + path : ADVANCEMENT;
        entry.pages = pages.toArray(BookStuff.Page[]::new);

        return entry;
    }

    /**
     * True if at least one of the archetype's hard requirements can never be met without another
     * mod - the three rooms written for Iron's Spells and Create (see {@code CLAUDE.md}) are the
     * shipped examples, but nothing here names them: this reads the same block and tag data the
     * classifier itself reads, so a datapack's own compat archetype is covered for free.
     *
     * <p>Such an archetype can never be classified on an install that lacks the mod, so its
     * {@code soulhome:main/&lt;archetype&gt;} advancement - built for every archetype in
     * {@code MainAdvancements}, whether or not the mod exists - can never be earned either. Gating
     * the entry behind that advancement instead of the usual "you have entered your soul" one is
     * what actually keeps the page from ever appearing in a book that mod cannot see; naming this
     * one is the piece that was missing before, and every entry silently used the same advancement.
     */
    static boolean needsAnotherMod(ArchetypeDefinition archetype)
    {
        for (ArchetypeDefinition.Requirement requirement : archetype.requirements())
        {
            if (unsatisfiableWithoutAnotherMod(requirement.match()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * A requirement is unsatisfiable without another mod only if <i>every</i> block or tag it would
     * accept needs one - a matcher that also takes a vanilla block, or a tag with even one vanilla
     * member, can still be met on its own.
     */
    private static boolean unsatisfiableWithoutAnotherMod(BlockMatcher matcher)
    {
        for (String block : matcher.blocks())
        {
            if (block.startsWith("minecraft:"))
            {
                return false;
            }
        }

        for (String tag : matcher.tags())
        {
            if (!isModOnlyTag(tag))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * True for one of this mod's own tags that, today, has nothing but entries {@link TagDocs}
     * marks optional - {@code soulhome:machinery} is entirely Create's blocks, for instance. A
     * vanilla or Forge tag is never treated as mod-only here: this mod does not ship its contents,
     * so there is no local way to know, and assuming the worst would hide an entry that should not
     * be hidden.
     */
    private static boolean isModOnlyTag(String tagId)
    {
        for (TagDocs.Tag tag : TagDocs.shipped())
        {
            if (tag.id().equals(tagId))
            {
                return tag.values().isEmpty() && !tag.optionalValues().isEmpty();
            }
        }

        return false;
    }

    /**
     * What each {@code soulhome:} category actually holds - #49. Every room page names its
     * rewards as a bare category ("any lighting", "any reagents") and, until this entry existed,
     * nowhere in the book or the game said what was in one. One page per tag, anchored so
     * {@link #readable} can link every mention straight to it; written from the tag files
     * themselves so a new block added to {@code soulhome:lighting} shows up here for free.
     *
     * <p>Only the {@code soulhome:} tags get a page. A room page also names vanilla and Forge
     * tags ({@code minecraft:crops}, {@code minecraft:rails}...), but this mod does not ship
     * their contents, so there is nothing local to write a page from - see {@link TagDocs}.
     */
    static BookStuff.Entry tagsGlossary(BookStuff.Category category)
    {
        BookStuff.Entry entry = new BookStuff.Entry("tags", category, category.icon);
        entry.setDisplayTitle("what the categories mean");
        entry.advancement = ADVANCEMENT;
        entry.sortnum = -9;

        List<BookStuff.Page> pages = new ArrayList<>();

        pages.add(new BookStuff.TextPage(
                "Room pages reward \"any\" of a category rather than one specific block, so a build using any mod's version of it still counts.$(p)This is what each of this mod's own categories actually holds. A page here for every one a room page mentions.")
                .setTitle("What counts as what"));

        for (TagDocs.Tag tag : TagDocs.shipped())
        {
            tagPages(tag, pages);
        }

        entry.pages = pages.toArray(BookStuff.Page[]::new);

        return entry;
    }

    /**
     * One tag's glossary pages, in reading order. The first carries the anchor {@link #readable}
     * links to.
     *
     * <p>Plural because a tag can be long: {@code soulhome:machinery} holds every machine part
     * Create ships, and a Patchouli text page that overruns simply stops drawing - the reader
     * gets a list that ends mid-sentence with no sign that anything is missing. Splitting is the
     * only option that keeps the page honest, since the whole point of the entry is to say what
     * actually counts.
     */
    private static void tagPages(TagDocs.Tag tag, List<BookStuff.Page> pages)
    {
        List<String> lines = new ArrayList<>();

        for (String value : tag.values())
        {
            lines.add("$(li)" + readable(value));
        }

        if (!tag.optionalValues().isEmpty())
        {
            //listed apart rather than mixed in: an entry that needs a mod the player may not have
            //is not the same promise as one that always counts, and a glossary that blurs the two
            //sends someone hunting for a block their game does not contain
            lines.add("$(p)And, with the mod that adds them installed:");

            for (String value : tag.optionalValues())
            {
                lines.add("$(li)" + readable(value));
            }
        }

        //the header costs the first page two of its lines, and a truncation note costs the last
        //page one more
        final int capacity = GLOSSARY_ENTRIES_PER_PAGE * GLOSSARY_MAX_PAGES - 2;

        if (lines.size() > capacity)
        {
            final int kept = capacity - 1;
            final long dropped = lines.subList(kept, lines.size()).stream()
                    .filter(line -> line.startsWith("$(li)"))
                    .count();

            lines = new ArrayList<>(lines.subList(0, kept));
            lines.add("$(p)...and " + dropped + " more.");
        }

        final String title = StringHelper.fixCapitalisation(tag.path());

        int index = 0;
        boolean first = true;

        while (index < lines.size())
        {
            StringBuilder text = new StringBuilder();

            //the id header costs a couple of the page's lines, so the first page holds fewer
            int room = first ? GLOSSARY_ENTRIES_PER_PAGE - 2 : GLOSSARY_ENTRIES_PER_PAGE;

            if (first)
            {
                text.append("$(item)").append(tag.id()).append("$(0)$(p)");
            }

            for (; index < lines.size() && room > 0; index++, room--)
            {
                text.append(lines.get(index));
            }

            if (index == lines.size())
            {
                //the closing note belongs with the last of the list, not alone on a page of its own
                text.append("$(p)Data packs and other mods can add to this list - nothing here replaces it.");
            }

            BookStuff.Page page = new BookStuff.TextPage(text.toString()).setTitle(title);

            if (first)
            {
                page.anchor = tag.path();
                first = false;
            }

            pages.add(page);
        }
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
                    .append('.')
                    .append(caveat(buff.type()));
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
     * Vanilla blocks are preferred over a modded one; see the comment inside.
     */
    private static String iconFor(ArchetypeDefinition archetype, BookStuff.Category category)
    {
        String fallback = null;

        for (ArchetypeDefinition.Signal signal : archetype.signals())
        {
            for (String block : signal.match().blocks())
            {
                //a room written for another mod names that mod's blocks first, and Patchouli
                //cannot draw an item the game in front of the reader does not have. Prefer a
                //vanilla block, which every install owns; for the rooms that name none, the
                //archetype's own first block is still better than the category's generic icon
                if (block.startsWith("minecraft:"))
                {
                    return block;
                }

                if (fallback == null)
                {
                    fallback = block;
                }
            }
        }

        return fallback == null ? category.icon : fallback;
    }

    /**
     * A per-buff qualifier for the rare buff whose scope a flat "up to +N%" would misstate - see
     * #52 and #53. Kept as one small exception rather than a general mechanism, since it is the
     * only buff type that is not simply "applies to everything, more with a higher tier".
     */
    private static String caveat(String buffType)
    {
        return SoulBuffTypes.POTION_DURATION.equals(buffType)
                ? " Only what you drink, splash on yourself or stand in the lingering cloud of - never a potion thrown at someone else. A beneficial effect runs that much longer; a harmful one runs that much shorter; a neutral effect is untouched."
                : "";
    }

    /** {@code soulhome:xp_gain} to "experience gain", for prose rather than for a log line. */
    private static String describeBuff(String buffType)
    {
        final int separator = buffType.indexOf(':');
        final String path = separator < 0 ? buffType : buffType.substring(separator + 1);

        return path.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /**
     * Ids as a player should read them: {@code #soulhome:bookshelves} becomes "any bookshelves",
     * {@code minecraft:lectern} becomes "Lectern". A tag this mod defines - and therefore has a
     * glossary page for, see {@link #tagsGlossary} - links straight to that page (#49); a vanilla
     * or Forge tag, such as {@code #minecraft:crops}, stays plain text, since there is no local
     * page to send a reader to.
     */
    static String readable(String description)
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
            final String namespace = separator < 0 ? "" : trimmed.substring(0, separator);
            final String path = separator < 0 ? trimmed : trimmed.substring(separator + 1);

            if (!tag)
            {
                readable.append(StringHelper.fixCapitalisation(path));
                continue;
            }

            final String label = "any " + path.replace('_', ' ');

            readable.append(SoulHome.MODID.equals(namespace)
                    ? "$(l:" + SoulHome.MODID + ":multiblocks/tags#" + path + ")" + label + "$(/l)"
                    : label);
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
