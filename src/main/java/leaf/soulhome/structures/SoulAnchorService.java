/*
 * File created ~ 14 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.AscensionReport;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSoulAnchorMessage;
import leaf.soulhome.utils.DimensionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The one thing that knows what the Soul Anchor's screen is made of: the climb (#83) and the
 * loadout (#154), built together and sent as one packet.
 *
 * <p>The anchor used to answer a right-click by printing its ascension summary to chat and silently
 * converting whatever residue had banked. Both are on the screen now, and the conversion is a button
 * rather than a side effect of clicking the block - a player who wanted to know how close they were
 * should not have had to spend their residue to find out, and chat is not visible at all while the
 * screen the same click opens is in front of them.
 *
 * <p><b>Nothing here trusts the client.</b> A conversion arrives as "I would like my residue", and
 * this decides whose soul is standing under the player, whether it is theirs, and whether they are
 * anywhere near its anchor - the same asymmetry {@link AttunementService} is written with.
 */
public final class SoulAnchorService
{
    /**
     * How far from the anchor a conversion may be asked for. The screen is opened by clicking the
     * block and a player cannot walk with it open, so this is never felt by a legitimate client -
     * it only stops a modified one from tapping its residue from wherever it happens to be standing.
     */
    private static final double CONVERSION_REACH = 8d;

    private SoulAnchorService()
    {
    }

    /**
     * Open the anchor's screen for this player, on the soulhome they are standing in.
     *
     * <p>A visitor gets the report and cannot change it: it is somebody else's soul, the binding
     * they would want to change is the one on their own, and the residue is not theirs to spend.
     * Their own buffs and their own attunement are untouched by standing here - see
     * {@code StructureScanService#refresh}, which always reads the player's own soulhome rather than
     * the level they happen to be in.
     */
    public static void open(ServerLevel soulhome, ServerPlayer player)
    {
        final boolean owner = isOwner(soulhome, player);

        send(soulhome, player, owner, 0);

        if (!owner && SoulHomeConfig.attunementEnabled())
        {
            player.sendSystemMessage(Component.translatable(Constants.StringKeys.ATTUNE_NOT_YOURS)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Redraw the screen a player already has open on their own soulhome - see {@link AttunementService#set}. */
    public static void refresh(ServerLevel soulhome, ServerPlayer player)
    {
        send(soulhome, player, true, 0);
    }

    /**
     * Convert as much of this soulhome's banked residue into Essence I as the config rate allows,
     * asked for by the button on the anchor's screen.
     *
     * <p>Refusals are silent rather than a chat line, because every one of them is something a
     * legitimate client cannot do: the button is not drawn for a visitor, is not drawn when there is
     * nothing banked, and cannot be reached at all from outside a soulhome. A refusal here means the
     * client made the request up, and the answer to that is the fresh report below - the screen ends
     * up showing exactly what actually happened, which is nothing.
     */
    public static void collectResidue(ServerPlayer player)
    {
        if (!(player.level() instanceof ServerLevel level) || !DimensionHelper.isInSoulDimension(player))
        {
            return;
        }

        if (!isOwner(level, player))
        {
            return;
        }

        final Optional<BlockPos> anchor = SoulHomeBuffData.get(level).anchorPos();

        if (anchor.isEmpty() || !player.blockPosition().closerThan(anchor.get(), CONVERSION_REACH))
        {
            return;
        }

        send(level, player, true, AscensionRitualService.convertResidue(level, player));
    }

    private static void send(ServerLevel soulhome, ServerPlayer player, boolean owner, int collected)
    {
        final AscensionReport ascension = AscensionRitualService.statusFor(soulhome, player, owner)
                .withCollected(collected);

        Network.sendTo(new SyncSoulAnchorMessage(ascension, AttunementService.reportFor(soulhome, owner)), player);
    }

    private static boolean isOwner(ServerLevel soulhome, ServerPlayer player)
    {
        return DimensionHelper.soulOwner(soulhome)
                .map(player.getUUID()::equals)
                .orElse(false);
    }
}
