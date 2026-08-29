/*
 * File created ~ 29 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #53: the alchemy lab extended every non-instantaneous potion effect a player drank, Turtle
 * Master's Slowness included - a nerf disguised as a buff. {@link PotionDurationEffect#isExtendable}
 * is the one place that decision is made now, and it decides by category rather than by name, so
 * these effects are never registered anywhere - a bare, synthetic {@link MobEffect} needs no game
 * bootstrap, and exercises the same logic a modded effect would be judged by.
 */
class PotionDurationEffectTest
{
    private static MobEffect effectOf(MobEffectCategory category)
    {
        return new MobEffect(category, 0xffffff)
        {
        };
    }

    private static MobEffect instantaneousEffectOf(MobEffectCategory category)
    {
        return new MobEffect(category, 0xffffff)
        {
            @Override
            public boolean isInstantenous()
            {
                return true;
            }
        };
    }

    private static MobEffectInstance instanceOf(MobEffect effect)
    {
        return new MobEffectInstance(effect, 200, 0);
    }

    @Test
    @DisplayName("a beneficial effect - Resistance, Strength - is extendable")
    void beneficialIsExtendable()
    {
        assertTrue(PotionDurationEffect.isExtendable(instanceOf(effectOf(MobEffectCategory.BENEFICIAL))));
    }

    @Test
    @DisplayName("a harmful effect - Turtle Master's Slowness, a poison - is not extended")
    void harmfulIsNotExtendable()
    {
        assertFalse(PotionDurationEffect.isExtendable(instanceOf(effectOf(MobEffectCategory.HARMFUL))));
    }

    @Test
    @DisplayName("a neutral effect is not extended either - the tighter rule #53 recommended")
    void neutralIsNotExtendable()
    {
        assertFalse(PotionDurationEffect.isExtendable(instanceOf(effectOf(MobEffectCategory.NEUTRAL))));
    }

    @Test
    @DisplayName("an instantaneous effect is skipped regardless of category")
    void instantaneousIsNotExtendable()
    {
        assertFalse(PotionDurationEffect.isExtendable(instanceOf(instantaneousEffectOf(MobEffectCategory.BENEFICIAL))));
    }

    @Test
    @DisplayName("the decision is by category, not by identity - an unregistered harmful effect is skipped too")
    void decidesByCategoryNotIdentity()
    {
        // a fresh, never-registered effect stands in for a modded one: nothing here depends on
        // the block/item registries or on being a named vanilla constant like MobEffects.POISON
        MobEffect syntheticHarmfulEffect = effectOf(MobEffectCategory.HARMFUL);

        assertFalse(PotionDurationEffect.isExtendable(instanceOf(syntheticHarmfulEffect)));
    }
}
