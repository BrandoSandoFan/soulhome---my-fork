/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.AttunementReport;
import leaf.soulhome.structures.core.AttunementBook;
import leaf.soulhome.structures.core.AttunementSettings;
import leaf.soulhome.structures.core.RoomBinding;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's side of attunement (#154): the one place a binding is decided.
 *
 * <p>What it decided is pushed back out through {@link SoulAnchorService}, which owns the whole of
 * the anchor screen's payload - a loadout is only half of what that screen shows, and a redraw that
 * carried only this half would leave the climb beside it saying whatever it said last time.
 *
 * <p><b>Nothing here trusts the client.</b> A binding arrives as "I would like room 7 bound"; this
 * decides whether room 7 is in the sender's own soulhome, whether that soulhome has a free slot of
 * the right kind, and whether attunement is switched on at all. The screen's copy of all of that
 * exists to be drawn, and for no other purpose - the same asymmetry {@code SoulAbilities} is written
 * with, for the same reason.
 *
 * <p><b>Free, instant, and applied at once.</b> There is no cost and no cooldown: the same rooms are
 * available either way, so a charge would only tax a player for experimenting. And buffs are
 * re-applied on the spot rather than at the next scan - #6 and the whole "my buffs vanished" class
 * of fault are the cautionary tale, and a player who binds a room and finds it does nothing until
 * something unrelated happens has met that fault again wearing a different hat.
 */
public final class AttunementService
{
    private AttunementService()
    {
    }

    /**
     * Bind or unbind one room of this player's own soulhome, and push the result back.
     *
     * @param roomId a number that arrived over the wire; treated as a claim to be checked rather
     *               than as an instruction
     */
    public static void set(ServerPlayer player, int roomId, boolean bind)
    {
        final AttunementSettings settings = SoulHomeConfig.attunementSettings();

        if (!SoulHomeConfig.enabled() || !settings.enabled())
        {
            return;
        }

        final ServerLevel soulhome = StructureScanService.soulhomeOf(player);

        if (soulhome == null)
        {
            return;
        }

        final SoulHomeBuffData data = SoulHomeBuffData.get(soulhome);
        final List<RoomBinding> bindings = new ArrayList<>(data.attunements());

        final AttunementBook.BindResult result = AttunementBook.apply(
                bindings, data.awardedRooms(), ArchetypeManager.byId(), settings,
                data.ascensionRank(), roomId, bind);

        switch (result)
        {
            case BOUND -> say(player, Constants.StringKeys.ATTUNE_BOUND, ChatFormatting.GREEN);
            case UNBOUND -> say(player, Constants.StringKeys.ATTUNE_UNBOUND, ChatFormatting.GRAY);
            case NO_SLOTS -> say(player, Constants.StringKeys.ATTUNE_NO_SLOTS, ChatFormatting.RED);
            default ->
            {
                // a forged or stale id, or a click on something already in the state it asked for:
                // nothing was written, so there is nothing to say and nothing to re-push
            }
        }

        if (result != AttunementBook.BindResult.BOUND && result != AttunementBook.BindResult.UNBOUND)
        {
            return;
        }

        data.setAttunement(bindings, data.nextRoomId());

        // buffs first, then the screen: a screen that redrew before the buffs moved would be telling
        // the player something that was not yet true
        StructureScanService.refresh(player);

        SoulAnchorService.refresh(soulhome, player);
    }

    /**
     * This soulhome's loadout as the anchor's screen draws it - built here rather than in
     * {@link SoulAnchorService} so the one place that decides what a binding does is also the one
     * place that says what the bindings are.
     *
     * @param owner whether the player being told is the soul's own owner, and so may change any of
     *              this. A visitor may look (#154)
     */
    public static AttunementReport reportFor(ServerLevel soulhome, boolean owner)
    {
        final SoulHomeBuffData data = SoulHomeBuffData.get(soulhome);

        return AttunementReport.of(
                data.awardedRooms(), data.attunements(), ArchetypeManager.byId(),
                SoulHomeConfig.attunementSettings(), SoulHomeConfig.buffSettings(),
                data.ascensionRank(), owner);
    }

    private static void say(ServerPlayer player, String key, ChatFormatting style)
    {
        player.sendSystemMessage(Component.translatable(key).withStyle(style));
    }
}
