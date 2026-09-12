/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.advancements;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.core.AwardedRoom;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The mod's custom advancement triggers, and the one place that fires them.
 *
 * <p>Triggers are a registry now, so these go through a {@link DeferredRegister} like every other
 * registered thing in the mod rather than being pushed into {@code CriteriaTriggers} during common
 * setup. That also answers the ordering worry the old comment here recorded - an advancement naming
 * an unknown trigger is still dropped with an error, but registration happens during the
 * registration phase, which is always before a datapack is read.
 */
public final class SoulAdvancements
{
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, SoulHome.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, ClassifiedRoomTrigger> CLASSIFIED_ROOM =
            TRIGGERS.register("classified_room", ClassifiedRoomTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, AscensionTrigger> ASCENDED =
            TRIGGERS.register("ascended", AscensionTrigger::new);

    private SoulAdvancements()
    {
    }

    /**
     * A scan finished and these rooms were awarded. Fired for every awarded room rather than only
     * for newly awarded ones: advancements are already idempotent, and tracking "new since last
     * scan" here would mean a player who earned a room while offline never got the toast.
     */
    public static void onRoomsAwarded(ServerPlayer player, List<AwardedRoom> awarded)
    {
        if (player == null || awarded.isEmpty())
        {
            return;
        }

        for (AwardedRoom room : awarded)
        {
            CLASSIFIED_ROOM.get().trigger(player, room.archetypeId(), room.tier(), awarded.size());
        }
    }

    /** The ascension ritual (#83) just raised a soulhome to {@code newRank}. */
    public static void onAscended(ServerPlayer player, int newRank)
    {
        if (player == null)
        {
            return;
        }

        ASCENDED.get().trigger(player, newRank);
    }
}
