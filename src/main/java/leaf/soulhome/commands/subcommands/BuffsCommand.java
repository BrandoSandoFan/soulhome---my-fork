/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.SoulReport;
import leaf.soulhome.structures.StructureScanService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /soulhome buffs} - what you are currently carrying, and which room gave it to you.
 *
 * <p>Buffs are earned in the soul and spent in the world, which means the place a player is
 * standing when they wonder about them is almost never the place that explains them. This answers
 * from anywhere, without a scan: it reads the soulhome's last saved results.
 */
public class BuffsCommand
{
    private BuffsCommand()
    {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.literal("buffs").executes(BuffsCommand::show);
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();

        if (!SoulHomeConfig.enabled())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANALYSE_DISABLED)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        for (Component line : SoulReport.buffs(StructureScanService.explainBuffs(player)))
        {
            player.sendSystemMessage(line);
        }

        return Command.SINGLE_SUCCESS;
    }
}
