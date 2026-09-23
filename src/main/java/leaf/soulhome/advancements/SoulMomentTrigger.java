/*
 * File created ~ 23 - 9 - 2026
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
 * One trigger for the Meditation epic's firsts (#190) - a first meditation, a first death through
 * the body, a first gaze - rather than a trigger class each. They carry nothing but the fact that
 * the moment happened, so a string naming which moment is the whole of the condition; see
 * {@link SoulAdvancements.Moment} for the set.
 */
public class SoulMomentTrigger extends SimpleCriterionTrigger<SoulMomentTrigger.Instance>
{
    @Override
    public Codec<Instance> codec()
    {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String moment)
    {
        trigger(player, instance -> instance.moment().equals(moment));
    }

    public record Instance(Optional<ContextAwarePredicate> player, String moment)
            implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        Codec.STRING.optionalFieldOf("moment", "").forGetter(Instance::moment))
                .apply(builder, Instance::new));

        public static Criterion<Instance> of(SoulAdvancements.Moment moment)
        {
            return SoulAdvancements.MOMENT.get().createCriterion(new Instance(Optional.empty(), moment.id()));
        }
    }
}
