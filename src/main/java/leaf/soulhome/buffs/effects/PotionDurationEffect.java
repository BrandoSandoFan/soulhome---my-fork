/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Alchemy lab: potions last longer.
 *
 * <p>Extends what the player drank, splashed on themselves, or stood in a lingering cloud of -
 * never an effect gained some other way. An effect from a beacon, a cake shared by a friend or a
 * wither's skull is not something the player's brewing room had any hand in, and quietly
 * stretching all of them would make the buff impossible to reason about. Nor does it touch a
 * splash or lingering potion thrown <em>at someone else</em> - see #52 - since "my brewing room
 * makes my debuffs stick to skeletons for longer" is a different, much stronger buff than the one
 * this archetype advertises.
 *
 * <h2>Two hooks, one rule</h2>
 *
 * Drinking is handled by {@link #onFinishedDrinking}, which fires from
 * {@code LivingEntity#completeUsingItem} after vanilla has already applied the potion.
 *
 * <p>Splash and lingering potions never fire {@code Finish} - {@code ThrowablePotionItem#use}
 * releases the projectile on the same tick, so {@code finishUsingItem} never runs - and are
 * handled instead by {@link #onEffectAdded}, which reacts to {@code MobEffectEvent.Added}.
 * {@code getEffectSource()} is the entity vanilla credits for the effect: the thrower for
 * {@code ThrownPotion#applySplash} and for an {@code AreaEffectCloud}. Requiring the source to be
 * the same entity as the target - a player affecting themselves - gives self-splash and
 * self-lingering for free while leaving a splash at a mob, a witch's potion, or another player's
 * potion untouched. A beacon or a cake applies with a {@code null} source, so that exclusion is
 * unchanged.
 *
 * <p>Both hooks funnel into {@link #extend}, which re-adds the affected instance with a longer
 * duration. Adding an instance that matches on amplifier and beats it on duration is exactly how
 * vanilla itself handles drinking a second potion, so nothing here needs the duration field to be
 * writable - and since that re-add calls {@code player.addEffect(MobEffectInstance)} with no
 * source, the {@code MobEffectEvent.Added} it fires back carries a {@code null} source, which
 * fails {@link #onEffectAdded}'s own source check. That is what stops this from re-entering
 * itself; it is load-bearing, so do not "simplify" the re-add into a form that sets a source.
 *
 * <h2>What gets extended</h2>
 *
 * Only a {@link MobEffectCategory#BENEFICIAL} effect - see #53. Vanilla categorises every effect
 * as beneficial, harmful or neutral, the same categorisation that colours an effect's name in the
 * inventory, and a brewing room extending your own debuffs (Turtle Master's Slowness, a poison you
 * failed to avoid) is not a reward for building one. Neutral effects (Glowing is the main one) are
 * left alone too, on the same "extend only what is unambiguously wanted" reasoning - the tighter
 * rule, and the easier one for the book to describe accurately. Instantaneous effects - healing,
 * harming - are skipped as before: they have no duration to extend, and re-adding one would apply
 * it twice.
 */
public class PotionDurationEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.POTION_DURATION;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra duration for a beneficial potion effect, as a fraction of the duration applied";
    }

    @SubscribeEvent
    public void onFinishedDrinking(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final ItemStack drunk = event.getItem();
        final List<MobEffectInstance> brewed = PotionUtils.getMobEffects(drunk);

        if (brewed.isEmpty())
        {
            // eating bread, drawing a bow: not everything finished is a potion
            return;
        }

        final double magnitude = magnitudeFor(player);

        for (MobEffectInstance instance : brewed)
        {
            if (player.getEffect(instance.getEffect()) == null)
            {
                // this declared effect did not take - immune mob, cured milk, another mod's say-so
                continue;
            }

            extend(player, instance, magnitude);
        }
    }

    /** Self-splash and self-lingering potions - see the class javadoc for the source check. */
    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event)
    {
        if (!(event.getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final Entity source = event.getEffectSource();

        if (source != player)
        {
            return;
        }

        extend(player, event.getEffectInstance(), magnitudeFor(player));
    }

    /**
     * Extends {@code applied} by a fraction of its own duration - not whatever happens to be
     * running. Reading back the active instance instead would let an unrelated, longer-lived
     * effect (a beacon, an earlier potion) inflate the extension far past what the magnitude
     * promises, and would stretch exactly the beacon effect this buff is documented not to touch.
     */
    private static void extend(Player player, MobEffectInstance applied, double magnitude)
    {
        if (applied.getEffect().isInstantenous() || applied.getEffect().getCategory() != MobEffectCategory.BENEFICIAL)
        {
            return;
        }

        final int extra = (int) (applied.getDuration() * magnitude);

        if (extra <= 0)
        {
            return;
        }

        player.addEffect(new MobEffectInstance(
                applied.getEffect(),
                applied.getDuration() + extra,
                applied.getAmplifier(),
                applied.isAmbient(),
                applied.isVisible(),
                applied.showIcon()));
    }
}
