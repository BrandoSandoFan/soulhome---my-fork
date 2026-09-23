/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.network;

import leaf.soulhome.client.ClientAmbience;
import leaf.soulhome.client.ClientSuppression;
import leaf.soulhome.client.GazeNoticeOverlay;
import leaf.soulhome.structures.core.SoulFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;

public class ClientPacketHandler
{
    /** The ambience gets out of the way of this mod's own audio (#212) - see {@link AmbienceHoldMessage}. */
    public static void ambienceHold(SoulFeedback kind, int ticks)
    {
        ClientAmbience.hold(kind, ticks);
    }

    /** Someone is looking into this player's soul (#189) - see {@link GazeNoticeMessage}. */
    public static void gazeNotice(double obviousness, int lingerTicks, boolean prickle)
    {
        GazeNoticeOverlay.onNotice(obviousness, lingerTicks, prickle);
    }

    /** How this player perceives another's ascension, as suppression (#188) - see {@link SyncSuppressionMessage}. */
    public static void syncSuppression(SyncSuppressionMessage packet)
    {
        ClientSuppression.set(packet);
    }

    public static void syncDimensionList(SyncDimensionListMessage packet)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        ResourceKey<Level> key = packet.getId();
        if (player == null || key == null)
        {
            return;
        }
        Set<ResourceKey<Level>> worlds = player.connection.levels();
        if (packet.getAdd())
        {
            worlds.add(key);
        }
        else
        {
            worlds.remove(key);
        }
    }
}
