/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.advancements;

import com.google.gson.JsonObject;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Fires when the ascension ritual (#83) raises a soulhome's rank. Carries the new rank so #93's
 * "first ascension" and "rank V" advancements can both be written against this one trigger rather
 * than needing a criterion each - {@code min_rank} is exactly {@link ClassifiedRoomTrigger}'s
 * {@code min_tier} idea, reused for the same reason.
 */
public class AscensionTrigger extends SimpleCriterionTrigger<AscensionTrigger.Instance>
{
    public static final ResourceLocation ID = ResourceLocationHelper.prefix("ascended");

    private static final String KEY_MIN_RANK = "min_rank";

    @Override
    public ResourceLocation getId()
    {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context)
    {
        return new Instance(player, GsonHelper.getAsInt(json, KEY_MIN_RANK, 1));
    }

    /** Tell the game a soulhome was just raised to {@code newRank}. */
    public void trigger(ServerPlayer player, int newRank)
    {
        trigger(player, instance -> instance.matches(newRank));
    }

    public static class Instance extends AbstractCriterionTriggerInstance
    {
        private final int minRank;

        public Instance(ContextAwarePredicate player, int minRank)
        {
            super(ID, player);
            this.minRank = Math.max(1, minRank);
        }

        public boolean matches(int newRank)
        {
            return newRank >= this.minRank;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context)
        {
            JsonObject json = super.serializeToJson(context);
            json.addProperty(KEY_MIN_RANK, this.minRank);
            return json;
        }
    }
}
