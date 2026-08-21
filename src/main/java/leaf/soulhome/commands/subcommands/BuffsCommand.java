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
 *
 * <p>And re-applies them on the way past, so that what this prints is what the player is carrying
 * rather than merely what they ought to be.
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

        // Re-assert before reporting. This reads the soulhome's saved rooms and hands the player
        // what they compute to, which is the same input the report below is built from - so the
        // two cannot disagree, and a player whose buffs have somehow come adrift from their rooms
        // gets them back by asking about them. No scan: this touches no blocks.
        StructureScanService.refresh(player);

        for (Component line : SoulReport.buffs(StructureScanService.explainBuffs(player)))
        {
            player.sendSystemMessage(line);
        }

        return Command.SINGLE_SUCCESS;
    }
}
