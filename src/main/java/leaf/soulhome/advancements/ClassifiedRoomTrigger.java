/*
 * File created ~ 18 - 8 - 2026
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

import java.util.Locale;

/**
 * Fires when a room in a player's soulhome is awarded an archetype.
 *
 * <p>An advancement is the one piece of feedback that finds a player rather than waiting to be
 * asked for, so this is what turns "something happened in my soul" from a number that quietly
 * changed into a toast. It carries the archetype and the tier so the book can gate one entry per
 * archetype, and so a pack can ask for "a tier 3 library" without needing its own trigger.
 *
 * <p>{@code archetype} is optional: an instance without one matches any classified room, which is
 * what the "you built your first room" advancement wants.
 */
public class ClassifiedRoomTrigger extends SimpleCriterionTrigger<ClassifiedRoomTrigger.Instance>
{
    public static final ResourceLocation ID = ResourceLocationHelper.prefix("classified_room");

    private static final String KEY_ARCHETYPE = "archetype";
    private static final String KEY_MIN_TIER = "min_tier";

    @Override
    public ResourceLocation getId()
    {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context)
    {
        final String archetype = json.has(KEY_ARCHETYPE)
                ? GsonHelper.getAsString(json, KEY_ARCHETYPE)
                : null;

        return new Instance(player, archetype, GsonHelper.getAsInt(json, KEY_MIN_TIER, 1));
    }

    /** Tell the game a room was awarded. Cheap when the player has no advancement waiting on it. */
    public void trigger(ServerPlayer player, String archetypeId, int tier)
    {
        trigger(player, instance -> instance.matches(archetypeId, tier));
    }

    public static class Instance extends AbstractCriterionTriggerInstance
    {
        private final String archetypeId;
        private final int minTier;

        public Instance(ContextAwarePredicate player, String archetypeId, int minTier)
        {
            super(ID, player);

            this.archetypeId = archetypeId == null || archetypeId.isBlank()
                    ? null
                    : archetypeId.toLowerCase(Locale.ROOT);
            this.minTier = Math.max(1, minTier);
        }

        /** Any room of any archetype, at any tier. */
        public static Instance any()
        {
            return new Instance(ContextAwarePredicate.ANY, null, 1);
        }

        public static Instance of(String archetypeId)
        {
            return new Instance(ContextAwarePredicate.ANY, archetypeId, 1);
        }

        public static Instance of(String archetypeId, int minTier)
        {
            return new Instance(ContextAwarePredicate.ANY, archetypeId, minTier);
        }

        public boolean matches(String awardedArchetype, int awardedTier)
        {
            if (awardedTier < this.minTier)
            {
                return false;
            }

            return this.archetypeId == null || this.archetypeId.equals(awardedArchetype);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context)
        {
            JsonObject json = super.serializeToJson(context);

            if (this.archetypeId != null)
            {
                json.addProperty(KEY_ARCHETYPE, this.archetypeId);
            }

            json.addProperty(KEY_MIN_TIER, this.minTier);

            return json;
        }
    }
}
