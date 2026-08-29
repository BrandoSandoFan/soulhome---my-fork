/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.mixin.MobEffectInstanceAccessor;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Alchemy lab: potions last longer, and their harmful effects fall short.
 *
 * <p>Acts on what the player drank, splashed on themselves, or stood in a lingering cloud of -
 * never an effect gained some other way. An effect from a beacon, a cake shared by a friend or a
 * wither's skull is not something the player's brewing room had any hand in, and quietly touching
 * all of them would make the buff impossible to reason about. Nor does it touch a splash or
 * lingering potion thrown <em>at someone else</em> - see #52 - since "my brewing room makes my
 * debuffs stick to skeletons for longer" is a different, much stronger buff than the one this
 * archetype advertises.
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
 * <h2>What happens, by category</h2>
 *
 * Vanilla categorises every effect as {@link MobEffectCategory#BENEFICIAL},
 * {@link MobEffectCategory#HARMFUL} or {@link MobEffectCategory#NEUTRAL} - the same categorisation
 * that colours an effect's name in the inventory:
 *
 * <ul>
 * <li>{@code BENEFICIAL} is extended, via {@link #extend}. Instantaneous effects are skipped:
 * they have no duration to extend, and re-adding one would apply it twice.
 * <li>{@code HARMFUL} is shortened, via {@link #shortenInPlace} - see #53. Turtle Master's
 * Resistance growing with the tier and its Slowness growing right along with it was a nerf
 * dressed as a buff; a brewing room knowing what to keep down is the intended read.
 * <li>{@code NEUTRAL} (Glowing is the main one) is left exactly as brewed, on the same "only act
 * on what is unambiguous" reasoning that excludes it from extension too - the tighter rule, and
 * the easier one for the book to describe accurately.
 * </ul>
 *
 * <h2>How each half is applied</h2>
 *
 * Extension re-adds the affected instance with a longer duration. Adding an instance that matches
 * on amplifier and beats it on duration is exactly how vanilla itself handles drinking a second
 * potion, so nothing here needs the duration field to be writable for that half - and since that
 * re-add calls {@code player.addEffect(MobEffectInstance)} with no source, the
 * {@code MobEffectEvent.Added} it fires back carries a {@code null} source, which fails
 * {@link #onEffectAdded}'s own source check. That is what stops this from re-entering itself; it
 * is load-bearing, so do not "simplify" the re-add into a form that sets a source.
 *
 * <p>Shortening cannot use the same trick: {@code MobEffectInstance#update} refuses an instance
 * that is weaker or shorter than what is already running, so re-adding a shortened one is a
 * silent no-op. The alternative of removing the effect and re-adding a shorter one works, but
 * fires {@code MobEffectEvent.Remove} and a second {@code Added} for a change that is not really
 * a removal - side effects another mod may react to for no reason. {@link #shortenInPlace} writes
 * the duration field directly instead, through {@link MobEffectInstanceAccessor}, which fires no
 * event at all - nothing to re-enter, and no extra event for anything else to see.
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
        return "as a fraction of the duration applied: extra time for a beneficial potion effect, less for a harmful one";
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

        for (MobEffectInstance declared : brewed)
        {
            if (player.getEffect(declared.getEffect()) == null)
            {
                // this declared effect did not take - immune mob, cured milk, another mod's say-so
                continue;
            }

            // exactly one of these does anything, per isExtendable/isShortenable - a category is
            // never both
            extend(player, declared, magnitude);
            reduce(player, declared, magnitude);
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

        if (event.getEffectSource() != player)
        {
            return;
        }

        final MobEffectInstance applied = event.getEffectInstance();
        final double magnitude = magnitudeFor(player);

        extend(player, applied, magnitude);
        reduce(player, applied, magnitude);
    }

    /**
     * Extends {@code applied} by a fraction of its own duration - not whatever happens to be
     * running. Reading back the active instance instead would let an unrelated, longer-lived
     * effect (a beacon, an earlier potion) inflate the extension far past what the magnitude
     * promises, and would stretch exactly the beacon effect this buff is documented not to touch.
     */
    private static void extend(Player player, MobEffectInstance applied, double magnitude)
    {
        if (!isExtendable(applied))
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

    /**
     * Shortens the harmful effect this drink just applied - but only the instance this drink is
     * actually responsible for. If a longer dose of the same effect is already running, the drink
     * did not touch it (vanilla's own update rules keep the stronger existing one), and shortening
     * it here would be shortening something this potion never applied.
     */
    private static void reduce(Player player, MobEffectInstance declared, double magnitude)
    {
        if (!isShortenable(declared))
        {
            return;
        }

        final MobEffectInstance active = player.getEffect(declared.getEffect());

        if (active == null || active.getDuration() > declared.getDuration())
        {
            return;
        }

        shortenInPlace(active, magnitude);
    }

    /** See the class javadoc's "How each half is applied" for why this writes the field directly. */
    private static void shortenInPlace(MobEffectInstance active, double magnitude)
    {
        final int shortened = (int) Math.round(active.getDuration() * (1d - magnitude));

        if (shortened < 1)
        {
            // percentage-based, so this only happens for a pathologically short duration or a
            // datapack-configured magnitude at or past 100% - never leave an effect at 0 ticks
            return;
        }

        ((MobEffectInstanceAccessor) active).setDuration(shortened);
    }

    /**
     * Whether {@code applied} is a duration the alchemy lab may stretch - see #53 and the class
     * javadoc. Split out of {@link #extend} so the decision reads as one rule rather than being
     * folded into a guard clause; deliberately does not touch {@link Player}, so nothing here
     * requires a live game world to reason about, even though {@code structures.core}'s
     * Minecraft-free test approach does not extend to {@link MobEffect}/{@link MobEffectInstance}
     * themselves - those need an actual game bootstrap to construct safely, so this is verified by
     * reading rather than by a unit test.
     */
    static boolean isExtendable(MobEffectInstance applied)
    {
        return !applied.getEffect().isInstantenous() && applied.getEffect().getCategory() == MobEffectCategory.BENEFICIAL;
    }

    /** The harmful-effect mirror of {@link #isExtendable} - see #53 and the class javadoc. */
    static boolean isShortenable(MobEffectInstance applied)
    {
        return !applied.getEffect().isInstantenous() && applied.getEffect().getCategory() == MobEffectCategory.HARMFUL;
    }
}
