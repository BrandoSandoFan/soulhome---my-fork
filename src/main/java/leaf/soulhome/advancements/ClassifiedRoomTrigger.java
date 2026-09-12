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
 * archetype, and so a pack can ask for "a tier 3 library" without needing its own trigger - and,
 * since the Soul Architecture epic (#140), how many rooms the same scan awarded, so an
 * advancement can ask for two at once: the guide book's bonds page gates behind that, since a
 * player with one room has nothing to bond.
 *
 * <p>{@code archetype} is optional: an instance without one matches any classified room, which is
 * what the "you built your first room" advancement wants.
 *
 * <h2>A codec rather than a pair of Gson methods</h2>
 *
 * 1.20.2 replaced {@code createInstance}/{@code serializeToJson} with one {@link Codec} and made
 * the instance a record - the trigger no longer carries its own id either, because it is registered
 * into {@code Registries.TRIGGER_TYPE} and the registry knows the name. {@code min_tier} and
 * {@code min_rooms} default through {@code optionalFieldOf} rather than through a
 * {@code GsonHelper} default, so a pack that omits them and a pack that writes {@code 1} produce
 * the same instance.
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
        trigger(player, archetypeId, tier, 1);
    }

    /**
     * @param roomsAwarded how many rooms the same scan awarded in total, for an advancement about
     *                     having more than one
     */
    public void trigger(ServerPlayer player, String archetypeId, int tier, int roomsAwarded)
    {
        trigger(player, instance -> instance.matches(archetypeId, tier, roomsAwarded));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> archetype, int minTier, int minRooms)
            implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        Codec.STRING.optionalFieldOf("archetype").forGetter(Instance::archetype),
                        Codec.INT.optionalFieldOf("min_tier", 1).forGetter(Instance::minTier),
                        Codec.INT.optionalFieldOf("min_rooms", 1).forGetter(Instance::minRooms))
                .apply(builder, Instance::new));

        public Instance
        {
            archetype = archetype.filter(id -> !id.isBlank()).map(id -> id.toLowerCase(Locale.ROOT));
            minTier = Math.max(1, minTier);
            minRooms = Math.max(1, minRooms);
        }

        /** Any room of any archetype, at any tier. */
        public static Criterion<Instance> any()
        {
            return criterion(Optional.empty(), 1, 1);
        }

        /** At least this many rooms awarded by one scan, of any archetype. */
        public static Criterion<Instance> atLeastRooms(int minRooms)
        {
            return criterion(Optional.empty(), 1, minRooms);
        }

        public static Criterion<Instance> of(String archetypeId)
        {
            return criterion(Optional.ofNullable(archetypeId), 1, 1);
        }

        public static Criterion<Instance> of(String archetypeId, int minTier)
        {
            return criterion(Optional.ofNullable(archetypeId), minTier, 1);
        }

        private static Criterion<Instance> criterion(Optional<String> archetype, int minTier, int minRooms)
        {
            // A Criterion wraps the instance with the trigger it belongs to, which is what an
            // advancement builder takes now - the trigger type is no longer named in JSON by the
            // instance itself.
            return SoulAdvancements.CLASSIFIED_ROOM
                    .get()
                    .createCriterion(new Instance(Optional.empty(), archetype, minTier, minRooms));
        }

        public boolean matches(String awardedArchetype, int awardedTier)
        {
            return matches(awardedArchetype, awardedTier, 1);
        }

        public boolean matches(String awardedArchetype, int awardedTier, int roomsAwarded)
        {
            if (awardedTier < this.minTier || roomsAwarded < this.minRooms)
            {
                return false;
            }

            return this.archetype.isEmpty() || this.archetype.get().equals(awardedArchetype);
        }
    }
}
