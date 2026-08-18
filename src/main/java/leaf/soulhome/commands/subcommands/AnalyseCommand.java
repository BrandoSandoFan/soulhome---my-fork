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
import leaf.soulhome.structures.SoulAnalysis;
import leaf.soulhome.structures.StructureScanService;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * {@code /soulhome analyse} - what the classifier made of your soul, and why.
 *
 * <p>Open to any player rather than gated behind an operator permission, unlike the teleport
 * subcommand next to it. It reads a soul the player is already standing in or already owns, it
 * changes nothing, and a player who cannot ask why their room did not count is back to the dead
 * end this whole feature exists to avoid.
 *
 * <p>The scan runs off-thread and answers through a callback, so this command replies twice: once
 * to say it is looking, and once with the result. Sending the result to the player directly rather
 * than through the command source matters - by the time it arrives, the command has long returned.
 */
public class AnalyseCommand
{
    private AnalyseCommand()
    {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.literal("analyse")
                .then(Commands.literal("here")
                        .executes(context -> analyse(context, true)))
                .executes(context -> analyse(context, false));
    }

    private static int analyse(CommandContext<CommandSourceStack> context, boolean hereOnly) throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();

        if (!SoulHomeConfig.enabled())
        {
            reply(player, Constants.StringKeys.ANALYSE_DISABLED, ChatFormatting.RED);
            return 0;
        }

        final ServerLevel target = targetSoulhome(player);

        if (target == null)
        {
            reply(player, Constants.StringKeys.ANALYSE_NO_SOULHOME, ChatFormatting.RED);
            return 0;
        }

        if (hereOnly && player.serverLevel() != target)
        {
            // "here" is only meaningful while standing in the soul being analysed
            reply(player, Constants.StringKeys.ANALYSE_NOT_HERE, ChatFormatting.RED);
            return 0;
        }

        if (StructureScanService.isScanning(target))
        {
            reply(player, Constants.StringKeys.ANALYSE_SCANNING, ChatFormatting.GRAY);
        }

        // read now: the callback runs a moment later, and a player who walked away in between
        // should still be told about the room they were standing in when they asked
        final BlockPos asked = player.blockPosition();

        StructureScanService.analyse(target, analysis -> report(player, analysis, hereOnly, asked));

        return Command.SINGLE_SUCCESS;
    }

    private static void report(ServerPlayer player, SoulAnalysis analysis, boolean hereOnly, BlockPos asked)
    {
        if (!player.isAlive() || player.hasDisconnected())
        {
            return;
        }

        for (Component line : hereOnly ? here(analysis, asked) : SoulReport.analysis(analysis))
        {
            player.sendSystemMessage(line);
        }
    }

    private static List<Component> here(SoulAnalysis analysis, BlockPos asked)
    {
        final Optional<ClassificationResult> region =
                analysis.regionAt(asked.getX(), asked.getY(), asked.getZ());

        if (region.isEmpty())
        {
            return List.of(Component.translatable(Constants.StringKeys.ANALYSE_NO_REGION_HERE)
                    .withStyle(ChatFormatting.GRAY));
        }

        return SoulReport.region(region.get(), 1);
    }

    /**
     * The soul to analyse: the one being stood in, or failing that the player's own.
     *
     * <p>Standing in someone else's soul and asking about it is allowed on purpose - they let you
     * in, you can see the blocks, and helping a friend work out why their library will not classify
     * is a good reason to be there.
     *
     * @return null when the player has no soulhome yet and is not standing in one
     */
    private static ServerLevel targetSoulhome(ServerPlayer player)
    {
        final ServerLevel current = player.serverLevel();

        if (DimensionHelper.soulOwner(current).isPresent())
        {
            return current;
        }

        return StructureScanService.soulhomeOf(player);
    }

    private static void reply(ServerPlayer player, String key, ChatFormatting style)
    {
        player.sendSystemMessage(Component.translatable(key).withStyle(style));
    }
}
