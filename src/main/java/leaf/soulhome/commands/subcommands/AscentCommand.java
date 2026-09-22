/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.SoulHomeBuffData;
import leaf.soulhome.structures.StructureScanService;
import leaf.soulhome.structures.TerrainGrowthService;
import leaf.soulhome.structures.core.RegionBounds;
import leaf.soulhome.structures.core.SoulBounds;
import leaf.soulhome.structures.core.TerrainGrowthSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * {@code /soulhome ascent} - the box a soulhome is bounded by (#78/#79), and what a legacy
 * soulhome (#80) keeps on top of it. {@code /soulhome ascent set} is the operator escape hatch
 * from #84: the actual climb - essence, willpower, the pillar (#82/#83) - is a later stage of the
 * same epic, and debugging a nine-rank progression without a way to jump straight to a rank means
 * nine real ascensions per test run once that mechanism exists.
 *
 * <p>{@code /soulhome ascent willpower} is #192's other half of that same escape hatch: rank is a
 * field {@code set} can just write, but willpower is computed off whatever rooms are currently
 * awarded, so there is nothing to write to directly. It parks an override on the soulhome's saved
 * data instead - see {@link SoulHomeBuffData#setWillpowerOverride} - and {@code reset} drops it.
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
                                .executes(AscentCommand::set)))
                .then(Commands.literal("willpower")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                .executes(AscentCommand::setWillpower))
                        .then(Commands.literal("reset")
                                .executes(AscentCommand::resetWillpower)));
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

        final SoulHomeBuffData data = SoulHomeBuffData.get(soulhome);
        final int rank = data.ascensionRank();
        final SoulBounds bounds = SoulHomeConfig.soulBounds(rank, data.islandFloorY());

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_HEADER)
                .withStyle(ChatFormatting.AQUA));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_RANK, SoulBounds.rankLabel(rank))
                .withStyle(ChatFormatting.WHITE));

        player.sendSystemMessage(Component.translatable(
                        Constants.StringKeys.ASCENT_BOX, bounds.floorY(), bounds.ceilingY(), bounds.vergeHalfExtent())
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_BUILD_LAYERS, bounds.buildLayers())
                .withStyle(ChatFormatting.GRAY));

        reportGround(player, soulhome, bounds, rank);

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
        // and grows the ground the new rank is owed (#158), the same as a real ascension would.
        // Setting a rank down grows nothing and takes nothing away: ground already grown stays.
        TerrainGrowthService.rankChanged(soulhome);

        reply(player, Constants.StringKeys.ASCENT_SET_SUCCESS, ChatFormatting.AQUA, SoulBounds.rankLabel(requested));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /soulhome ascent willpower <value>} - operator-only, per #192. Rank has a field
     * {@link #set} can just overwrite; willpower does not, since it is
     * {@link SoulHomeBuffData#totalScore()} read fresh off whatever rooms are currently awarded -
     * so this parks an override on the soul's saved data instead, letting the ascension ritual and
     * the residue tap be tested at a chosen figure without building the rooms to earn it for real.
     */
    private static int setWillpower(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final double requested = DoubleArgumentType.getDouble(context, "value");

        final ServerLevel soulhome = StructureScanService.soulhomeOf(player);

        if (soulhome == null)
        {
            reply(player, Constants.StringKeys.ASCENT_NO_SOULHOME, ChatFormatting.RED);
            return 0;
        }

        SoulHomeBuffData.get(soulhome).setWillpowerOverride(requested);

        reply(player, Constants.StringKeys.ASCENT_WILLPOWER_SET_SUCCESS, ChatFormatting.AQUA, formatScore(requested));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /soulhome ascent willpower reset} - drops the override {@link #setWillpower} left
     * behind. Nothing about the soul's rooms ever moved; this only decides which number
     * {@link SoulHomeBuffData#totalScore()} reports.
     */
    private static int resetWillpower(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final ServerLevel soulhome = StructureScanService.soulhomeOf(player);

        if (soulhome == null)
        {
            reply(player, Constants.StringKeys.ASCENT_NO_SOULHOME, ChatFormatting.RED);
            return 0;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(soulhome);
        data.clearWillpowerOverride();

        reply(player, Constants.StringKeys.ASCENT_WILLPOWER_RESET_SUCCESS, ChatFormatting.AQUA, formatScore(data.totalScore()));

        return Command.SINGLE_SUCCESS;
    }

    private static String formatScore(double value)
    {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Ground, beside walls (#162). These are different numbers on purpose and the whole epic is
     * invisible unless a player is told so: a soul whose walls are at 104 and whose ground reaches
     * 78 has open void inside its own box, and a player who has not been told that is deliberate
     * reads it as growth having failed.
     */
    private static void reportGround(ServerPlayer player, ServerLevel soulhome, SoulBounds bounds, int rank)
    {
        final TerrainGrowthSettings growth = SoulHomeConfig.terrainGrowthSettings();

        if (!growth.enabled())
        {
            reply(player, Constants.StringKeys.ASCENT_GROUND_OFF, ChatFormatting.DARK_GRAY);
            return;
        }

        final int reach = TerrainGrowthService.groundReach(soulhome, bounds);

        player.sendSystemMessage(Component.translatable(
                        Constants.StringKeys.ASCENT_GROUND, reach, bounds.vergeHalfExtent())
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable(
                        Constants.StringKeys.ASCENT_GROUND_VERGE, Math.max(0, bounds.vergeHalfExtent() - reach))
                .withStyle(ChatFormatting.DARK_GRAY));

        TerrainGrowthService.progress(soulhome.dimension()).ifPresent(progress -> player.sendSystemMessage(
                Component.translatable(Constants.StringKeys.ASCENT_GROUND_GROWING, (int) Math.round(progress * 100))
                        .withStyle(ChatFormatting.AQUA)));

        if (rank < SoulHomeConfig.maxRank())
        {
            final int next = growth.groundLimit(rank + 1, SoulHomeConfig.soulBounds(rank + 1).vergeHalfExtent());

            player.sendSystemMessage(Component.translatable(
                            Constants.StringKeys.ASCENT_GROUND_NEXT, Math.max(0, next - reach), next)
                    .withStyle(ChatFormatting.GRAY));
        }
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
