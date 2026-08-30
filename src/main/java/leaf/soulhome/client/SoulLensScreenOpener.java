/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.client.gui.SoulLensBuffsScreen;
import leaf.soulhome.client.gui.SoulLensScreen;
import leaf.soulhome.feedback.LensBuffReport;
import leaf.soulhome.feedback.LensRegionReport;
import leaf.soulhome.network.SyncSoulLensBuffsMessage;
import leaf.soulhome.network.SyncSoulLensReportMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Opens the Soul Lens screen (#50) the moment a fresh report or buff breakdown arrives from the
 * server, so using the lens reads as opening a screen rather than as two chat-adjacent packets.
 *
 * <p>The message classes stay Minecraft-free plain data holders - see
 * {@link SyncSoulLensReportMessage.ClientLensReport} - so a dedicated server loading them never
 * touches {@link Minecraft}. This is the client-only half that actually reacts.
 *
 * <p>Only opens over an empty screen, so a report that lands while a player has something else
 * open - the game menu, another mod's GUI - does not steal focus from it.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulLensScreenOpener
{
    private SoulLensScreenOpener()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        final List<LensRegionReport> regions = SyncSoulLensReportMessage.ClientLensReport.consumeIfNew();

        if (regions != null && minecraft.screen == null)
        {
            minecraft.setScreen(new SoulLensScreen(regions, SyncSoulLensReportMessage.ClientLensReport.standingIn()));
        }

        final List<LensBuffReport> buffs = SyncSoulLensBuffsMessage.ClientLensBuffs.consumeIfNew();

        if (buffs != null && minecraft.screen == null)
        {
            minecraft.setScreen(new SoulLensBuffsScreen(buffs));
        }
    }
}
