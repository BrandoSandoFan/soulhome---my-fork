/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires when the ascension ritual (#83) raises a soulhome's rank. Carries the new rank so #93's
 * "first ascension" and "rank V" advancements can both be written against this one trigger rather
 * than needing a criterion each - {@code min_rank} is exactly {@link ClassifiedRoomTrigger}'s
 * {@code min_tier} idea, reused for the same reason.
 */
public class AscensionTrigger extends SimpleCriterionTrigger<AscensionTrigger.Instance>
{
    @Override
    public Codec<Instance> codec()
    {
        return Instance.CODEC;
    }

    /** Tell the game a soulhome was just raised to {@code newRank}. */
    public void trigger(ServerPlayer player, int newRank)
    {
        trigger(player, instance -> instance.matches(newRank));
    }

    public record Instance(Optional<ContextAwarePredicate> player, int minRank)
            implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        Codec.INT.optionalFieldOf("min_rank", 1).forGetter(Instance::minRank))
                .apply(builder, Instance::new));

        public Instance
        {
            minRank = Math.max(1, minRank);
        }

        public static Criterion<Instance> atLeast(int minRank)
        {
            return SoulAdvancements.ASCENDED.get().createCriterion(new Instance(Optional.empty(), minRank));
        }

        public boolean matches(int newRank)
        {
            return newRank >= this.minRank;
        }
    }
}
