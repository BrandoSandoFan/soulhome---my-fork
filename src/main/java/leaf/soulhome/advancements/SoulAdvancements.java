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
    public static SoulMomentTrigger MOMENT;

    /**
     * The Meditation epic's firsts (#190). Each is an advancement of its own, and two of them gate a
     * book page - see {@code PatchouliBasics}.
     */
    public enum Moment
    {
        /** A channel at a Meditation Cushion completed and took its player in. */
        MEDITATED("meditated"),
        /** A player died through their own body while away. The moment the new risk became real. */
        VESSEL_DEATH("vessel_death"),
        /** A player walked into a soul that is not their own - the rank gate cleared (#184). */
        GUEST("guest"),
        /** A player cast Soulgaze into someone else's soul (#187). */
        GAZED("gazed"),
        /** A player noticed someone looking into their soul (#189). */
        GAZED_AT("gazed_at"),
        /** A player first perceived another's ascension as suppression (#188). */
        SUPPRESSION("suppression");

        private final String id;

        Moment(String id)
        {
            this.id = id;
        }

        public String id()
        {
            return this.id;
        }
    }

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

        if (MOMENT == null)
        {
            MOMENT = CriteriaTriggers.register(new SoulMomentTrigger());
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
            // whether the room took an aspect (#171) comes off the awarded room itself, so a server
            // with aspects switched off never fires the aspect criterion and the book's page on
            // them stays invisible there - no config read, because there is nothing to read
            CLASSIFIED_ROOM.trigger(player, room.archetypeId(), room.tier(), awarded.size(), room.hasAspect());
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

    /** One of the Meditation epic's firsts happened to this player - see {@link Moment}. */
    public static void onMoment(ServerPlayer player, Moment moment)
    {
        if (MOMENT == null || player == null)
        {
            return;
        }

        MOMENT.trigger(player, moment.id());
    }
}
