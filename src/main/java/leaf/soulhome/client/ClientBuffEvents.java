/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.ClientSoulAbilities;
import leaf.soulhome.buffs.ClientSoulBuffs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drops the client's copy of its buffs when it leaves a world.
 *
 * <p>Every server that has this mod sends a fresh copy on login, so most of the time this changes
 * nothing. The case it exists for is the one that does not: a client that goes from a soulhome
 * server to one without the mod would otherwise keep predicting the buffs it used to have, and a
 * block-breaking speed the server has never heard of is a block that snaps back.
 *
 * <p>Client-only, and kept out of the buffs package for that reason - {@code ClientSoulBuffs}
 * itself is loaded on dedicated servers, and must not name a class that is not there.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientBuffEvents
{
    private ClientBuffEvents()
    {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientSoulBuffs.clear();

        // the same reasoning for the ability HUD (#87): a stale charge count drawn over a server
        // that has never heard of this mod is worse than an empty corner
        ClientSoulAbilities.clear();
    }
}
