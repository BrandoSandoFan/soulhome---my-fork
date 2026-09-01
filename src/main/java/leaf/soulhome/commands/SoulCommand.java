/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 *
 * Special thank you to the New Tardis Mod team.
 * That mod taught me how to correctly add new commands, among other things!
 * https://tardis-mod.com/books/home/page/links#bkmrk-source
 */

package leaf.soulhome.commands;

import com.mojang.brigadier.CommandDispatcher;
import leaf.soulhome.SoulHome;
import leaf.soulhome.commands.subcommands.AnalyseCommand;
import leaf.soulhome.commands.subcommands.BuffsCommand;
import leaf.soulhome.commands.subcommands.SoulHomeCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class SoulCommand
{

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(SoulHome.MODID)
                .then(SoulHomeCommand.register(dispatcher))
                //unlike 'home', these two only read, and are open to every player: a fuzzy
                //classifier nobody but an operator can interrogate is a fuzzy classifier nobody
                //can build for
                .then(AnalyseCommand.register())
                .then(BuffsCommand.register())
        );
    }
}
