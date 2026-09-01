/*
 * File created ~ 1 - 9 - 2026
 */

package leaf.soulhome.commands.subcommands;

import com.mojang.brigadier.Command;
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
 * soulhome (#80) keeps on top of it.
 *
 * <p>Rule 5 of the Ascent epic: scarcity must be legible. This is Phase 1 of that epic - the box
 * exists, but the climb that raises it (#82-#84) does not yet - so there is no essence, no
 * willpower threshold and no pillar to report progress against. Every player is rank 0, and this
 * says so plainly rather than inventing numbers for a mechanism that is not there yet.
 */
public class AscentCommand
{
    private AscentCommand()
    {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register()
    {
        return Commands.literal("ascent").executes(AscentCommand::show);
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

        // rank 0 always, for now - #84 is what saves and raises it, later in the same epic
        final int rank = 0;
        final SoulBounds bounds = SoulHomeConfig.soulBounds(rank);

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_HEADER)
                .withStyle(ChatFormatting.AQUA));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_RANK, rankName(rank))
                .withStyle(ChatFormatting.WHITE));

        player.sendSystemMessage(Component.translatable(
                        Constants.StringKeys.ASCENT_BOX, bounds.floorY(), bounds.ceilingY(), bounds.vergeHalfExtent())
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_BUILD_LAYERS, bounds.buildLayers())
                .withStyle(ChatFormatting.GRAY));

        SoulHomeBuffData.get(soulhome).legacyBox().ifPresent(legacy -> player.sendSystemMessage(
                Component.translatable(Constants.StringKeys.ASCENT_LEGACY, describe(legacy))
                        .withStyle(ChatFormatting.YELLOW)));

        player.sendSystemMessage(Component.translatable(Constants.StringKeys.ASCENT_NOT_YET)
                .withStyle(ChatFormatting.DARK_GRAY));

        return Command.SINGLE_SUCCESS;
    }

    /** Rank 0 has no numeral yet - I through V will, once #84 lands. */
    private static String rankName(int rank)
    {
        return rank <= 0 ? "0 (unascended)" : String.valueOf(rank);
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
}
