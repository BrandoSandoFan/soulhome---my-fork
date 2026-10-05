/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.network.SyncSuppressionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What this client has been told it may perceive (#188): for each suppressed player it tracks, the
 * signature the server worked out from their rank and this player's own. Display only, like every
 * other client-side copy in the mod - and deliberately without either raw rank in it.
 *
 * <p>{@link #visible} is the one question every channel asks, so that the ring aura, the warp and
 * the drone can never disagree about who is in view. Line of sight is part of it: a sense that
 * drew through walls would be a tracker, and this is meant to be something you notice about
 * someone in front of you.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientSuppression
{
    /** One perceived player, as the server described them. */
    public record Perceived(
            int entityId, int rings, float radius, float strength, float legibility, float displacement,
            float range, boolean distortion, boolean audio)
    {
    }

    /** A perceived player currently in view, with the entity itself for anything that needs its position. */
    public record InView(Perceived perceived, Player player, double distance)
    {
    }

    private static final Map<Integer, Perceived> PERCEIVED = new ConcurrentHashMap<>();

    private static ClientLevel lastLevel;

    private ClientSuppression()
    {
    }

    public static void set(SyncSuppressionMessage message)
    {
        if (message.getRings() <= 0)
        {
            PERCEIVED.remove(message.getEntityId());
            return;
        }

        PERCEIVED.put(message.getEntityId(), new Perceived(
                message.getEntityId(),
                message.getRings(),
                message.getRadius(),
                message.getStrength(),
                message.getLegibility(),
                message.getDisplacement(),
                message.getRange(),
                message.isDistortion(),
                message.isAudio()));
    }

    public static boolean isEmpty()
    {
        return PERCEIVED.isEmpty();
    }

    /**
     * Every perceived player this client can see right now, nearest first: alive, in this level, not
     * this player, not a spectator, not invisible to them, within the range their rank is perceived
     * at, and in plain sight.
     */
    public static List<InView> visible()
    {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer self = minecraft.player;
        final ClientLevel level = minecraft.level;

        if (self == null || level == null || PERCEIVED.isEmpty())
        {
            return List.of();
        }

        final List<InView> inView = new ArrayList<>();

        for (Perceived perceived : PERCEIVED.values())
        {
            final Entity entity = level.getEntity(perceived.entityId());

            if (!(entity instanceof Player player)
                    || player == self
                    || !player.isAlive()
                    || player.isSpectator()
                    || player.isInvisibleTo(self))
            {
                continue;
            }

            final double distance = player.distanceTo(self);

            if (distance > perceived.range() || !self.hasLineOfSight(player))
            {
                continue;
            }

            inView.add(new InView(perceived, player, distance));
        }

        inView.sort(Comparator.comparingDouble(InView::distance));
        return inView;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        // entity ids are per level: a dimension change leaves every id this map holds pointing at
        // nothing, or worse at some other entity that took the number
        final ClientLevel level = Minecraft.getInstance().level;

        if (level != lastLevel)
        {
            PERCEIVED.clear();
            lastLevel = level;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        PERCEIVED.clear();
        lastLevel = null;
    }
}
