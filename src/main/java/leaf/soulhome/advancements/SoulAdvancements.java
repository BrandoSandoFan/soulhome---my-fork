/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.advancements;

import leaf.soulhome.structures.core.AwardedRoom;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The mod's custom advancement triggers, and the one place that fires them.
 *
 * <p>Registration has to happen during common setup, before any datapack is read: an advancement
 * naming a trigger the game does not know about is dropped with an error, and dropping the
 * archetype advancements would take the book entries gated behind them with it.
 */
public final class SoulAdvancements
{
    public static ClassifiedRoomTrigger CLASSIFIED_ROOM;
    public static AscensionTrigger ASCENDED;

    private SoulAdvancements()
    {
    }

    /** Call once, from common setup, on the main thread. */
    public static void register()
    {
        if (CLASSIFIED_ROOM == null)
        {
            CLASSIFIED_ROOM = CriteriaTriggers.register(new ClassifiedRoomTrigger());
        }

        if (ASCENDED == null)
        {
            ASCENDED = CriteriaTriggers.register(new AscensionTrigger());
        }
    }

    /**
     * A scan finished and these rooms were awarded. Fired for every awarded room rather than only
     * for newly awarded ones: advancements are already idempotent, and tracking "new since last
     * scan" here would mean a player who earned a room while offline never got the toast.
     */
    public static void onRoomsAwarded(ServerPlayer player, List<AwardedRoom> awarded)
    {
        if (CLASSIFIED_ROOM == null || player == null || awarded.isEmpty())
        {
            return;
        }

        for (AwardedRoom room : awarded)
        {
            CLASSIFIED_ROOM.trigger(player, room.archetypeId(), room.tier());
        }
    }

    /** The ascension ritual (#83) just raised a soulhome to {@code newRank}. */
    public static void onAscended(ServerPlayer player, int newRank)
    {
        if (ASCENDED == null || player == null)
        {
            return;
        }

        ASCENDED.trigger(player, newRank);
    }
}
