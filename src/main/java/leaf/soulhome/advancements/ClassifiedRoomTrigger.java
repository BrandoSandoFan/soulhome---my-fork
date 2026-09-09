/*
 * File created ~ 18 - 8 - 2026
 */

package leaf.soulhome.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

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
 *
 * <h2>A codec rather than a pair of Gson methods</h2>
 *
 * 1.20.2 replaced {@code createInstance}/{@code serializeToJson} with one {@link Codec} and made
 * the instance a record - the trigger no longer carries its own id either, because it is registered
 * into {@code Registries.TRIGGER_TYPE} and the registry knows the name. {@code min_tier} defaults
 * through {@code optionalFieldOf} rather than through a {@code GsonHelper} default, so a pack that
 * omits it and a pack that writes {@code 1} produce the same instance.
 */
public class ClassifiedRoomTrigger extends SimpleCriterionTrigger<ClassifiedRoomTrigger.Instance>
{
    @Override
    public Codec<Instance> codec()
    {
        return Instance.CODEC;
    }

    /** Tell the game a room was awarded. Cheap when the player has no advancement waiting on it. */
    public void trigger(ServerPlayer player, String archetypeId, int tier)
    {
        trigger(player, instance -> instance.matches(archetypeId, tier));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> archetype, int minTier)
            implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        Codec.STRING.optionalFieldOf("archetype").forGetter(Instance::archetype),
                        Codec.INT.optionalFieldOf("min_tier", 1).forGetter(Instance::minTier))
                .apply(builder, Instance::new));

        public Instance
        {
            archetype = archetype.filter(id -> !id.isBlank()).map(id -> id.toLowerCase(Locale.ROOT));
            minTier = Math.max(1, minTier);
        }

        /** Any room of any archetype, at any tier. */
        public static Criterion<Instance> any()
        {
            return criterion(Optional.empty(), 1);
        }

        public static Criterion<Instance> of(String archetypeId)
        {
            return criterion(Optional.ofNullable(archetypeId), 1);
        }

        public static Criterion<Instance> of(String archetypeId, int minTier)
        {
            return criterion(Optional.ofNullable(archetypeId), minTier);
        }

        private static Criterion<Instance> criterion(Optional<String> archetype, int minTier)
        {
            // A Criterion wraps the instance with the trigger it belongs to, which is what an
            // advancement builder takes now - the trigger type is no longer named in JSON by the
            // instance itself.
            return SoulAdvancements.CLASSIFIED_ROOM
                    .get()
                    .createCriterion(new Instance(Optional.empty(), archetype, minTier));
        }

        public boolean matches(String awardedArchetype, int awardedTier)
        {
            if (awardedTier < this.minTier)
            {
                return false;
            }

            return this.archetype.isEmpty() || this.archetype.get().equals(awardedArchetype);
        }
    }
}
