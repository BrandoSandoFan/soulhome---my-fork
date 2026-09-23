/*
 * File created ~ 23 - 9 - 2026
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
 * One trigger for the Meditation epic's firsts (#190) - a first meditation, a first death through
 * the body, a first gaze - rather than a trigger class each. They carry nothing but the fact that
 * the moment happened, so a string naming which moment is the whole of the condition; see
 * {@link SoulAdvancements.Moment} for the set.
 */
public class SoulMomentTrigger extends SimpleCriterionTrigger<SoulMomentTrigger.Instance>
{
    public static final ResourceLocation ID = ResourceLocationHelper.prefix("soul_moment");

    private static final String KEY_MOMENT = "moment";

    @Override
    public ResourceLocation getId()
    {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context)
    {
        return new Instance(player, GsonHelper.getAsString(json, KEY_MOMENT, ""));
    }

    public void trigger(ServerPlayer player, String moment)
    {
        trigger(player, instance -> instance.moment.equals(moment));
    }

    public static class Instance extends AbstractCriterionTriggerInstance
    {
        private final String moment;

        public Instance(ContextAwarePredicate player, String moment)
        {
            super(ID, player);
            this.moment = moment;
        }

        public static Instance of(SoulAdvancements.Moment moment)
        {
            return new Instance(ContextAwarePredicate.ANY, moment.id());
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context)
        {
            JsonObject json = super.serializeToJson(context);
            json.addProperty(KEY_MOMENT, this.moment);
            return json;
        }
    }
}
