/*
 * File created ~ 14 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.patchouli.categories;

import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;
import leaf.soulhome.structures.core.MeditationSettings;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.structures.core.TerrainGrowthSettings;

import java.util.ArrayList;
import java.util.List;

public class PatchouliBasics
{
    public static void collect(List<BookStuff.Category> categories, List<BookStuff.Entry> entries)
    {
        BookStuff.Category basics = new BookStuff.Category(
                "basics",
                "An introduction to the mod, serving as a tutorial.",
                "soulhome:guide");

        basics.sortnum = 0;

        categories.add(basics);

        BookStuff.Entry welcomeEntry = new BookStuff.Entry("welcome", basics, basics.icon);
        welcomeEntry.pages = new BookStuff.Page[]
                {
                    new BookStuff.TextPage("Your soul is a private dimension of your own - somewhere you can go, and always come back from.$(p)Nothing you build there is just for show. What you make of it changes what you can do out here."),
                    new BookStuff.TextPage("There is no long setup. Craft a $(item)SoulKey$(0), step through it, and everything else grows from what you build inside."),
                    new BookStuff.TextPage("If you still aren't sure where to start, this book (as well as advancements) should help you find what the next step is. The book is set up to unlock new entries with every advancement completed.$(p)For now though, here's a tip:"),
                    new BookStuff.CraftingPage("All you need is a little bit of iron and an ender pearl", "soulhome:soulkey").setTitle("SoulKey"),
                };
        welcomeEntry.priority = true;
        welcomeEntry.sortnum = -10;
        entries.add(welcomeEntry);

        BookStuff.Entry bookEntry = new BookStuff.Entry("guide", basics, basics.icon);
        bookEntry.sortnum = 2;
        bookEntry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("Your guide to everything in the mod! The heart of it is this: what you build inside your soul changes what you can do outside of it."),
                        new BookStuff.TextPage("Grow a farm in there and every meal goes further. Build an armoury and your sword bites harder.$(p)There are no blueprints to copy - build what you think a farm looks like, and it is judged on its own merits, including how it is arranged. There is still no single right layout. See $(l:soulhome:multiblocks/rooms)Soul Rooms$(/l)."),
                };
        entries.add(bookEntry);

        BookStuff.Entry soulkeyEntry = new BookStuff.Entry("soul_key", basics, "soulhome:soulkey");
        soulkeyEntry.setDisplayTitle("SoulKey");
        soulkeyEntry.sortnum = 3;
        soulkeyEntry.advancement = "soulhome:main/obtained_soul_key";
        soulkeyEntry.turnin = "soulhome:main/obtained_soul_key";
        soulkeyEntry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("Well done - you've got the centrepiece of this mod. Hold [$(k:use)] and you'll see particles gathering at your feet in an ever-widening circle, showing the area that is about to travel with you."),
                        new BookStuff.TextPage("Hold [$(k:use)] for the full " + seconds(MeditationSettings.DEFAULT_KEY_CHANNEL_TICKS) + " seconds and you, along with everything else inside the circle, are carried into your soul. This is how you'd bring friends and livestock along.$(p)You do not take your body with you. It stays sitting where you stood - see $(l:soulhome:basics/vessel)The Body You Leave$(/l)."),
                        new BookStuff.TextPage("The key works anywhere, with nothing built for it - which is why it is the slow way in, and why the body it leaves takes a hit as hard as you would.$(p)At home, a $(l:soulhome:basics/meditation)Meditation Cushion$(/l) is quicker, and the body it leaves is sturdier. The key is for when you are nowhere near one.")
                                .setTitle("The Long Way In"),
                        new BookStuff.CraftingPage("All you need is a little bit of iron and an ender pearl", "soulhome:soulkey").setTitle("SoulKey"),

                };
        entries.add(soulkeyEntry);

        BookStuff.Entry personalSoulKey = new BookStuff.Entry("personal_soul_key", basics, "soulhome:personal_soulkey");
        personalSoulKey.setDisplayTitle("Bound Soulkey");
        personalSoulKey.sortnum = 4;
        personalSoulKey.advancement = "soulhome:main/obtained_soul_key";
        personalSoulKey.turnin = "soulhome:main/obtained_soul_key";
        personalSoulKey.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("Want to let someone else in? A Bound Soulkey is set to a particular soul rather than your own, so you can hand it to a friend.$(p)Just like the standard key, hold [$(k:use)] for the full duration and everything within the circle travels to the soul the key is bound to - but walking into a soul that is not your own asks something of the visitor. See $(l:soulhome:basics/guests)Guests$(/l)."),
                        new BookStuff.CraftingPage("Similar to the standard key, except you use an ender eye.", "soulhome:personal_soulkey").setTitle("Bound Soulkey"),
                };
        entries.add(personalSoulKey);

        entries.add(theGround(basics));
        entries.add(meditation(basics));
        entries.add(theBody(basics));
        entries.add(guests(basics));
        entries.add(suppression(basics));

        BookStuff.Entry enteredSoul = new BookStuff.Entry("soul", basics, basics.icon);
        enteredSoul.sortnum = 5;
        enteredSoul.turnin = "soulhome:main/entered_soul_dimension";
        enteredSoul.advancement = "soulhome:main/obtained_soul_key";
        enteredSoul.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("Welcome to your soul. $(p)Kinda empty, isn't it? So fill it. Build rooms in here and you'll carry what they mean out there - see $(l:soulhome:multiblocks/rooms)Soul Rooms$(/l)."),
                };
        entries.add(enteredSoul);


    }

    /**
     * Meditation (#183): the better way in, and the reason a player builds a cushion rather than
     * carrying a key everywhere. Numbers from the {@code DEFAULT_} constants, like every other page.
     */
    private static BookStuff.Entry meditation(BookStuff.Category basics)
    {
        BookStuff.Entry entry = new BookStuff.Entry("meditation", basics, "soulhome:meditation_cushion");
        entry.setDisplayTitle("Meditation");
        entry.sortnum = 7;
        entry.advancement = "soulhome:main/obtained_soul_key";
        entry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage(
                                "The good way into your soul is to sit down somewhere you chose and go.$(p)Stand on or beside a $(item)Meditation Cushion$(0) and hold [$(k:key.soulhome.meditate)]. After about "
                                        + seconds(MeditationSettings.DEFAULT_CUSHION_CHANNEL_TICKS) + " seconds you are in your soul - faster than any key, and the body you leave behind takes only half of any hit it is dealt.")
                                .setTitle("Meditation"),
                        new BookStuff.TextPage(
                                "Meditation needs a cushion. That is the point of it: a cushion is where you decided to leave your body, and a cushion in a locked room is a body nobody can reach.$(p)Move, or be hit, and the channel stops. Nobody steps out of a fight by meditating.$(p)The same bind brings you back from anywhere in your soul, with no cushion needed."),
                        new BookStuff.CraftingPage("Wool and string - nothing you would miss.", "soulhome:meditation_cushion").setTitle("Meditation Cushion"),
                };

        return entry;
    }

    /**
     * The Soul Vessel (#182) and what it now means (#185, #186). The page a player most needs to have
     * read before the first time it matters, so it is open as soon as they have been inside once.
     */
    private static BookStuff.Entry theBody(BookStuff.Category basics)
    {
        BookStuff.Entry entry = new BookStuff.Entry("vessel", basics, "minecraft:armor_stand");
        entry.setDisplayTitle("The Body You Leave");
        entry.sortnum = 8;
        entry.advancement = "soulhome:main/entered_soul_dimension";
        entry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage(
                                "Whenever you enter your soul - by cushion, by key, or by looking into someone else's - your body stays sitting where you were.$(p)$(bold)It is real.$(0) Strike it and the blow lands on you, wherever you are: your armour, your enchantments and your potions still apply, because it is still you being hit.")
                                .setTitle("The Body You Leave"),
                        new BookStuff.TextPage(
                                "It does not wait politely in an unloaded field, either. Your body keeps the ground it sits on awake, so anything that wanders by can find it.$(p)If it is killed, you die - and everything you carried spills out where your body fell, in the world, not in your soul where nobody could follow. Your compass points there."),
                        new BookStuff.TextPage(
                                "A body left by a cushion takes half of every hit. One left by a key takes it all.$(p)If your body is disturbed some other way - struck from existence, or taken while you were logged out - you are drawn back into it at once, alive, wherever it stood.$(p)Choose where you sit down.")
                                .setTitle("Where You Leave It"),
                };

        return entry;
    }

    /**
     * Guest passage (#184). There are no Ascent pages to put it beside yet, so it stands with the
     * other ways in, and names the rank from the config default rather than a hard-coded numeral.
     */
    private static BookStuff.Entry guests(BookStuff.Category basics)
    {
        final String rank = SoulBounds.rankLabel(SoulBounds.DEFAULT_GUEST_RANK_REQUIRED);

        BookStuff.Entry entry = new BookStuff.Entry("guests", basics, "minecraft:iron_door");
        entry.setDisplayTitle("Guests");
        entry.sortnum = 9;
        entry.advancement = "soulhome:main/entered_soul_dimension";
        entry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage(
                                "Walking into a soul that is not your own, in the flesh, is something only a soul that has climbed a long way can do: rank "
                                        + rank + " of the Ascent, of your own soul - never the one you are visiting.$(p)Use a Bound Soulkey below that and nothing happens. Stand beside someone using their key and you are left behind.")
                                .setTitle("Guests"),
                        new BookStuff.TextPage(
                                "A guest leaves a body behind like anyone else, back where they used the key, and it is theirs: die in a borrowed soul and your things spill where you left yourself, not at your host's feet.$(p)Looking into a soul is a different thing, with no rank needed - that is the observatory's."),
                };

        return entry;
    }

    /**
     * Suppression (#188). Gated behind the advancement for first perceiving it, so the page does not
     * spoil something a player has never seen - and so a server that switched it off never shows it.
     */
    private static BookStuff.Entry suppression(BookStuff.Category basics)
    {
        BookStuff.Entry entry = new BookStuff.Entry("suppression", basics, "minecraft:amethyst_cluster");
        entry.setDisplayTitle("Suppression");
        entry.sortnum = 10;
        entry.advancement = "soulhome:main/suppression";
        entry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage(
                                "A soul that has climbed presses on the air around it. Once you have built a room of your own, you can feel it: a warp around a player who has ascended, following them, and a low hum.$(p)The further they have climbed, the larger and stronger it is, and the further off you notice it.")
                                .setTitle("Suppression"),
                        new BookStuff.TextPage(
                                "How well you can read it is yours. To a soul that has not climbed, a great one is a formless smear that throws off your aim. As you ascend, the same field settles into rings, one for every rank they hold, and a low beat for each that you can count.$(p)A strong soul you can read is one you can aim through. A strong soul you cannot is simply in the way."),
                        new BookStuff.TextPage(
                                "You only feel what you can see. A wall hides a soul as well as it hides a face.$(p)If the warp is uncomfortable to look at, turn it off in $(bold)soulhome-client.toml$(0) - the rings and the hum still tell you everything it would have."),
                };

        return entry;
    }

    /** Whole seconds for a tick count, for prose. */
    private static String seconds(int ticks)
    {
        return Integer.toString(Math.round(ticks / 20f));
    }

    /**
     * Terrain growth (#158/#162). Written from the {@code DEFAULT_} constants the config spec reads
     * its own defaults from, so the book and a fresh install agree by construction rather than by
     * anyone remembering to update prose.
     *
     * <p>The page that has to exist is the third one. A player whose walls are at 120 and whose
     * ground reaches 90 has open void inside their own box, and unless they have been told that is
     * deliberate they will report it as a bug - or, worse, conclude their ascension broke something.
     *
     * <p>Gated on having entered a soul at all rather than on having ascended: the mod ships no
     * ascension advancement, and a page about what the climb grants is one a player wants to read
     * before they climb rather than after.
     */
    private static BookStuff.Entry theGround(BookStuff.Category basics)
    {
        final int vergeAtMax = SoulBounds.forRank(SoulBounds.MAX_RANK).vergeHalfExtent();
        final int groundAtMax = TerrainGrowthSettings.DEFAULTS.groundLimit(SoulBounds.MAX_RANK, vergeAtMax);
        final int groundPerStep = SoulBounds.DEFAULT_OUTWARD_STEP * TerrainGrowthSettings.DEFAULT_GROUND_PER_RANK;
        final int vergePerStep = SoulBounds.DEFAULT_OUTWARD_STEP * SoulBounds.DEFAULT_VERGE_PER_RANK;

        BookStuff.Entry entry = new BookStuff.Entry("the_ground", basics, "minecraft:grass_block");
        entry.setDisplayTitle("ground, and the verge");
        entry.sortnum = 6;
        entry.advancement = "soulhome:main/entered_soul_dimension";
        entry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage(
                                "Every rank of ascent raises your soul's ceiling. Ranks " + outwardRanks()
                                        + " also widen its walls and grow the island, so the room you just earned is somewhere "
                                        + "to stand.$(p)The new ground follows "
                                        + "the coast you already have, and is made of your own soul's blocks - a snowy soul grows "
                                        + "snow."),
                        new BookStuff.TextPage(
                                "$(bold)Nothing you built is ever built over.$(0)$(p)Ground only appears where there was "
                                        + "nothing at all, and it keeps well clear of anything you placed. A bridge you threw out "
                                        + "into the void before you ascended stays exactly where it is, and the island grows "
                                        + "around it rather than through it."),
                        new BookStuff.TextPage(
                                "$(bold)The void at the edge is meant to be there.$(0)$(p)Your walls always reach further "
                                        + "than your ground does - at the highest rank they stand " + vergeAtMax
                                        + " blocks out while the ground reaches about " + groundAtMax + ".$(p)That gap is yours to "
                                        + "build into. If you want a floating hall over open sky, the sky is already waiting."),
                        new BookStuff.TextPage(
                                "Each widening pushes the walls out " + vergePerStep + " blocks and adds roughly "
                                        + groundPerStep + " blocks of coast, up to that limit. A soul that "
                                        + "climbed before any of this existed catches up all at once, the next time you walk into it."
                                        + "$(p)Type $(bold)/soulhome ascent$(0) to see how far your ground reaches, how far your walls "
                                        + "do, and which rank widens your soul next."),
                };

        return entry;
    }

    /**
     * The ranks that widen a soul at the defaults, as the book spells them - "III, VI and IX".
     * Read off {@link SoulBounds#outwardRank} rather than written out, so a changed step changes the
     * page with it.
     */
    private static String outwardRanks()
    {
        final List<String> ranks = new ArrayList<>();

        for (int rank = 1; rank <= SoulBounds.MAX_RANK; rank++)
        {
            if (SoulBounds.nextOutwardRank(rank - 1, SoulBounds.MAX_RANK, SoulBounds.DEFAULT_OUTWARD_STEP) == rank)
            {
                ranks.add(SoulBounds.rankLabel(rank));
            }
        }

        if (ranks.size() < 2)
        {
            return String.join("", ranks);
        }

        return String.join(", ", ranks.subList(0, ranks.size() - 1)) + " and " + ranks.get(ranks.size() - 1);
    }
}
