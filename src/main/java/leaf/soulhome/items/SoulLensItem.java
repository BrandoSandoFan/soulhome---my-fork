/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.items;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.RegionHighlight;
import leaf.soulhome.feedback.SoulReport;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSoulBuffsMessage;
import leaf.soulhome.network.SyncSoulRegionsMessage;
import leaf.soulhome.properties.PropTypes;
import leaf.soulhome.structures.SoulAnalysis;
import leaf.soulhome.structures.StructureScanService;
import leaf.soulhome.structures.core.ClassificationResult;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.TextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * Shows a player what the classifier can see.
 *
 * <p>A command can answer the same questions, but only for someone who already knows to ask it. An
 * item is in the creative tab, in the book, and in the hand of anyone who crafted one - and using
 * it draws the region boundaries in the world, which is the one thing text cannot do. Region
 * detection is otherwise entirely invisible, and "the mod thinks these two rooms are one room" is
 * the single most common reason a good-looking build does not classify.
 *
 * <p>Used inside a soul, it outlines every region and explains the one you are standing in. Used
 * anywhere else, it lists the buffs you are carrying and the rooms that earned them - the same
 * question a player has when they are out in the world wondering whether any of this is working.
 */
public class SoulLensItem extends BaseItem
{
    public SoulLensItem()
    {
        super(PropTypes.Items.ONE.get().rarity(Rarity.UNCOMMON));
    }

    /**
     * The tooltip carries the player's current buffs.
     *
     * <p>Buffs are earned in the soul and spent in the world, so the question "is any of this
     * actually doing anything" is asked a long way from anywhere that could answer it. Hanging the
     * answer off an item in the inventory means it is always to hand, without a command and
     * without going home.
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Level worldIn, List<Component> tooltip, TooltipFlag flagIn)
    {
        tooltip.add(TextHelper.createTranslatedText(Constants.StringKeys.LENS_HIGHLIGHTED)
                .withStyle(ChatFormatting.GRAY));

        //the client's own copy, kept up to date by the server; display only
        tooltip.addAll(SoulReport.buffSummary(SyncSoulBuffsMessage.ClientSoulBuffs.get()));
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn)
    {
        final ItemStack stack = playerIn.getItemInHand(handIn);

        if (!(playerIn instanceof ServerPlayer player))
        {
            // the swing and the sound are the client's half; everything else needs the server
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
        }

        if (!SoulHomeConfig.enabled())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANALYSE_DISABLED)
                    .withStyle(ChatFormatting.RED));
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
        }

        final ServerLevel level = player.serverLevel();

        if (DimensionHelper.soulOwner(level).isEmpty())
        {
            //outside a soul there is nothing to outline, so the lens answers the other question
            showBuffs(player);
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f);

        if (StructureScanService.isScanning(level))
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ANALYSE_SCANNING)
                    .withStyle(ChatFormatting.GRAY));
        }

        final BlockPos asked = player.blockPosition();

        StructureScanService.analyse(level, analysis -> show(player, level, analysis, asked));

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    private static void show(ServerPlayer player, ServerLevel level, SoulAnalysis analysis, BlockPos asked)
    {
        if (!player.isAlive() || player.hasDisconnected())
        {
            return;
        }

        Network.sendTo(
                new SyncSoulRegionsMessage(
                        level.dimension().location().toString(), RegionHighlight.of(analysis.results())),
                player);

        if (analysis.isEmpty())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.LENS_NOTHING_TO_SHOW)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        final Optional<ClassificationResult> standingIn =
                analysis.regionAt(asked.getX(), asked.getY(), asked.getZ());

        // standing inside a region: explain that one in full. Standing outside every region -
        // which is itself worth knowing - list them all so the outlines have names
        final List<Component> lines = standingIn.isPresent()
                ? SoulReport.region(standingIn.get(), 1)
                : SoulReport.analysis(analysis);

        for (Component line : lines)
        {
            player.sendSystemMessage(line);
        }
    }

    private static void showBuffs(ServerPlayer player)
    {
        for (Component line : SoulReport.buffs(StructureScanService.explainBuffs(player)))
        {
            player.sendSystemMessage(line);
        }
    }
}
