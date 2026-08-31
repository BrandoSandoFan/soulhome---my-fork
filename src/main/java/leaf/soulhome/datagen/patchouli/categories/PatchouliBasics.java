/*
 * File created ~ 14 - 7 - 2021 ~ Leaf
 */

package leaf.soulhome.datagen.patchouli.categories;

import leaf.soulhome.datagen.patchouli.categories.data.BookStuff;

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
                        new BookStuff.TextPage("Hold [$(k:use)] for the full duration and you, along with everything else inside the circle, are carried into your soul. This is how you'd bring friends and livestock along."),
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
                        new BookStuff.TextPage("Want to let someone else in? A Bound Soulkey is set to a particular soul rather than your own, so you can hand it to a friend.$(p)Just like the standard key, hold [$(k:use)] for the full duration and everything within the circle travels to the soul the key is bound to."),
                        new BookStuff.CraftingPage("Similar to the standard key, except you use an ender eye.", "soulhome:personal_soulkey").setTitle("Bound Soulkey"),
                };
        entries.add(personalSoulKey);

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
}
