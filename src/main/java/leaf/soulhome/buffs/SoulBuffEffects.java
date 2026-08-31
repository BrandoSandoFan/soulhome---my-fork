/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.buffs.effects.AttributeBuffEffect;
import leaf.soulhome.buffs.effects.DoubleJumpEffect;
import leaf.soulhome.buffs.effects.EnchantmentPowerEffect;
import leaf.soulhome.buffs.effects.FallProtectionEffect;
import leaf.soulhome.buffs.effects.FireAspectEffect;
import leaf.soulhome.buffs.effects.FireResistanceEffect;
import leaf.soulhome.buffs.effects.FortuneEffect;
import leaf.soulhome.buffs.effects.KnockbackResistanceEffect;
import leaf.soulhome.buffs.effects.ManaEffect;
import leaf.soulhome.buffs.effects.MiningSpeedEffect;
import leaf.soulhome.buffs.effects.NourishedEffect;
import leaf.soulhome.buffs.effects.PotionDurationEffect;
import leaf.soulhome.buffs.effects.ReachEffect;
import leaf.soulhome.buffs.effects.SaturationEffect;
import leaf.soulhome.buffs.effects.SoulEmberEffect;
import leaf.soulhome.buffs.effects.SpellPowerEffect;
import leaf.soulhome.buffs.effects.SwimSpeedEffect;
import leaf.soulhome.buffs.effects.SwordDamageEffect;
import leaf.soulhome.buffs.effects.HealingEffect;
import leaf.soulhome.buffs.effects.SpeedEffect;
import leaf.soulhome.buffs.effects.XpGainEffect;
import leaf.soulhome.utils.LogHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The buff types this mod knows how to apply.
 *
 * <p>Registration is a list rather than a deferred registry because these are not game content: no
 * one needs to reference a buff type from a recipe or an item tag. What the registry is actually
 * for is validation - an archetype naming {@code soulhome:xp_gian} should produce a log line at
 * load rather than a buff that silently never does anything.
 */
public final class SoulBuffEffects
{
    private static final Map<String, SoulBuffEffect> BY_TYPE = new LinkedHashMap<>();

    private SoulBuffEffects()
    {
    }

    /** Register and subscribe the built-in effects. Call once, during common setup. */
    public static void init()
    {
        if (!BY_TYPE.isEmpty())
        {
            return;
        }

        register(new SaturationEffect());
        register(new SwordDamageEffect());
        register(new XpGainEffect());
        register(new EnchantmentPowerEffect());
        register(new PotionDurationEffect());
        register(new HealingEffect());
        register(new MiningSpeedEffect());
        register(new SpeedEffect());
        register(new DoubleJumpEffect());
        register(new FallProtectionEffect());
        register(new FireAspectEffect());
        register(new FireResistanceEffect());
        register(new SoulEmberEffect());
        register(new NourishedEffect());
        register(new FortuneEffect());
        register(new KnockbackResistanceEffect());

        //written against another mod's attributes, and registered whether or not that mod is
        //installed: the room, the report and the saved magnitude are all the same either way, and
        //the effect simply has nothing to write to until the day it is
        register(new ManaEffect());
        register(new SpellPowerEffect());
        register(new ReachEffect());
        register(new SwimSpeedEffect());

        LogHelper.info("Registered " + BY_TYPE.size() + " soul buff effect(s): " + BY_TYPE.keySet());

        reportInertEffects();
    }

    /**
     * Say, once, which buffs have nothing to act on in this install.
     *
     * <p>A buff written against another mod's attribute is registered whether or not that mod is
     * there, and on a server without it the room still classifies and {@code /soulhome buffs}
     * still lists what it grants - which is the right behaviour and an infuriating one to debug
     * from a bug report that says "my mana room does nothing". One line at startup answers it.
     */
    private static void reportInertEffects()
    {
        List<String> inert = new ArrayList<>();

        for (SoulBuffEffect effect : BY_TYPE.values())
        {
            if (effect instanceof AttributeBuffEffect attributeEffect
                    && attributeEffect.attributes().isEmpty())
            {
                inert.add(effect.type());
            }
        }

        if (!inert.isEmpty())
        {
            LogHelper.info("These soul buffs have no attribute to act on in this install and will"
                    + " do nothing until the mod that adds one is installed: " + inert);
        }
    }

    public static void register(SoulBuffEffect effect)
    {
        SoulBuffEffect existing = BY_TYPE.putIfAbsent(effect.type(), effect);

        if (existing != null)
        {
            LogHelper.warn("Two soul buff effects both claim " + effect.type() + "; keeping the first.");
            return;
        }

        effect.register();
    }

    /** Whether anything will act on this buff type. Used to validate archetype definitions. */
    public static boolean isKnown(String buffType)
    {
        return BY_TYPE.containsKey(buffType);
    }

    public static List<String> knownTypes()
    {
        return List.copyOf(BY_TYPE.keySet());
    }

    public static SoulBuffEffect get(String buffType)
    {
        return BY_TYPE.get(buffType);
    }
}
