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
                    new BookStuff.TextPage("Hey, thanks for checking out this mod! It came about from my love of the $(item)Spectre Key$(0) from $(thing)RandomThings$(0). $(p)The idea of having a safe area in a different dimension that you could go to and come back from was very appealing. "),
                    new BookStuff.TextPage("This current iteration of SoulHome is very basic, with a low barrier of entry on purpose. $(p)There's some exciting stuff coming in future versions though, so I hope you'll stick around!"),
                    new BookStuff.TextPage("If you still aren't sure where to start, this book (as well as advancements) should help you find what the next step is. The book is set up to unlock new entries with every advancement completed.$(p) $(p)For now though, here's a tip:"),
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
        soulkeyEntry.sortnum = 3;
        soulkeyEntry.advancement = "soulhome:main/obtained_soul_key";
        soulkeyEntry.turnin = "soulhome:main/obtained_soul_key";
        soulkeyEntry.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("Hey, well done! You've managed to obtain the centerpiece to this mod. Now you just need to enter your soul by holding [$(k:use)]. You'll see some particles start appearing at your feet, an every widening circle showing the area of effect."),
                        new BookStuff.TextPage("If you hold [$(k:use)] for the full duration, you and all other entities within the circle will be transported to your soul. This is how you'd bring friends and livestock into your soul."),
                        new BookStuff.CraftingPage("All you need is a little bit of iron and an ender pearl", "soulhome:soulkey").setTitle("SoulKey"),

                };
        entries.add(soulkeyEntry);

        BookStuff.Entry personalSoulKey = new BookStuff.Entry("personal_soul_key", basics, "soulhome:personal_soulkey");
        personalSoulKey.sortnum = 3;
        personalSoulKey.advancement = "soulhome:main/obtained_soul_key";
        personalSoulKey.turnin = "soulhome:main/obtained_soul_key";
        personalSoulKey.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("What if you have friends that you want to have access to your soul. Sounds dangerous to me, but hey, no judgement. $(p)$(p)Just like the other, hold [$(k:use)] for the full duration, you and all other entities within the circle will be transported to the soul that the key is set for."),
                        new BookStuff.CraftingPage("Similar to the standard key, except you use an ender eye.", "soulhome:personal_soulkey").setTitle("SoulKey"),
                };
        entries.add(personalSoulKey);

        BookStuff.Entry enteredSoul = new BookStuff.Entry("soul", basics, basics.icon);
        enteredSoul.sortnum = 4;
        enteredSoul.turnin = "soulhome:main/entered_soul_dimension";
        enteredSoul.advancement = "soulhome:main/obtained_soul_key";
        enteredSoul.pages = new BookStuff.Page[]
                {
                        new BookStuff.TextPage("Woo! Welcome to your soul. $(p)Kinda empty, isn't it? I wonder what that says about you, huh. $(p)So fill it. Build rooms in here and you'll carry what they mean out there - see $(l:soulhome:multiblocks/rooms)Soul Rooms$(/l)."),
                };
        entries.add(enteredSoul);


    }
}
