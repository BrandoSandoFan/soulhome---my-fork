/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.SoulHomeBuffData;
import leaf.soulhome.structures.StructureScanService;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulBounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /soulhome ascent} - the box a soulhome is bounded by (#78/#79), and what a legacy
 * soulhome (#80) keeps on top of it. {@code /soulhome ascent set} is the operator escape hatch
 * from #84: the actual climb - essence, willpower, the pillar (#82/#83) - is a later stage of the
 * same epic, and debugging a five-rank progression without a way to jump straight to a rank means
 * five real ascensions per test run once that mechanism exists.
 *
 * <p>Rule 5 of the Ascent epic: scarcity must be legible.
 */
public class AscentCommand
{
    private AscentCommand()
    {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.literal("ascent")
                .executes(AscentCommand::show)
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("rank", IntegerArgumentType.integer(0))
                                .executes(AscentCommand::set)));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();

        if (!SoulHomeConfig.enforceBounds())
        {
            reply(player, Constants.StringKeys.ASCENT_DISABLED, ChatFormatting.RED);
            return 0;
        }

        final ServerLevel soulhome = StructureScanService.soulhomeOf(player);

        if (soulhome == null)
        {
            reply(player, Constants.StringKeys.ASCENT_NO_SOULHOME, ChatFormatting.RED);
            return 0;
        }

        final int rank = SoulHomeBuffData.get(soulhome).ascensionRank();
        final SoulBounds bounds = SoulHomeConfig.soulBounds(rank);

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_HEADER)
                .withStyle(ChatFormatting.AQUA));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_RANK, SoulBounds.rankLabel(rank))
                .withStyle(ChatFormatting.WHITE));

        player.sendSystemMessage(Component.translatable(
                        Constants.StringKeys.ASCENT_BOX, bounds.floorY(), bounds.ceilingY(), bounds.vergeHalfExtent())
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_BUILD_LAYERS, bounds.buildLayers())
                .withStyle(ChatFormatting.GRAY));

        SoulHomeBuffData.get(soulhome).legacyBox().ifPresent(legacy -> player.sendSystemMessage(
                Component.translatable(Constants.StringKeys.ASCENT_LEGACY, describe(legacy))
                        .withStyle(ChatFormatting.YELLOW)));

        if (rank >= SoulHomeConfig.maxRank())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_MAXED)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        else
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_NOT_YET)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /soulhome ascent set <rank>} - operator-only, per #84's acceptance criteria. Sets the
     * rank directly and resyncs the player's client immediately, the same way the (not yet built)
     * ascension ritual would: nothing about this bypasses the box or buff plumbing, it only skips
     * the pillar, the willpower threshold and the essence cost.
     */
    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final int requested = IntegerArgumentType.getInteger(context, "rank");
        final int maxRank = SoulHomeConfig.maxRank();

        if (requested > maxRank)
        {
            reply(player, Constants.StringKeys.ASCENT_SET_OUT_OF_RANGE, ChatFormatting.RED, maxRank);
            return 0;
        }

        final ServerLevel soulhome = StructureScanService.soulhomeOf(player);

        if (soulhome == null)
        {
            reply(player, Constants.StringKeys.ASCENT_NO_SOULHOME, ChatFormatting.RED);
            return 0;
        }

        SoulHomeBuffData.get(soulhome).setAscensionRank(requested);
        // resends the box (and everything else refresh already keeps in step) at the new rank,
        // rather than leaving the client showing the box for the rank it had a moment ago
        StructureScanService.refresh(player);

        reply(player, Constants.StringKeys.ASCENT_SET_SUCCESS, ChatFormatting.AQUA, SoulBounds.rankLabel(requested));

        return Command.SINGLE_SUCCESS;
    }

    private static String describe(RegionBounds box)
    {
        return "x " + box.minX() + ".." + box.maxX()
                + ", y " + box.minY() + ".." + box.maxY()
                + ", z " + box.minZ() + ".." + box.maxZ();
    }

    private static void reply(ServerPlayer player, String key, ChatFormatting style)
    {
        player.sendSystemMessage(Component.translatable(key).withStyle(style));
    }

    private static void reply(ServerPlayer player, String key, ChatFormatting style, Object... args)
    {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(style));
    }
}
