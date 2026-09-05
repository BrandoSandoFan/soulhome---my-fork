/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.buffs.SoulBuffEffects;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * What a buff is called and what its magnitude means, as a player would read them:
 * {@code soulhome:sword_damage} becomes "Sword damage", and a magnitude of 0.2 becomes "+20%".
 *
 * <p>The same argument {@link BlockNames} makes about block ids applies to buff ids, and the Soul
 * Lens used to print them raw - "sword_damage +0.2" on a screen where the room's own name, the
 * blocks it counted and everything else was already translated. Worse than untidy: the number is a
 * different unit from the one {@code /soulhome buffs} shows for the same buff, so the two answers
 * to "what am I carrying" disagreed on both halves of the line.
 *
 * <p>Magnitudes are unitless in the data, so the effect that acts on one is asked how to read it.
 * {@link SoulBuffTypes#isFraction} is the fallback rather than a guess from the number itself,
 * because it is what the book and the config ceiling already read, and because showing
 * "+0.2 enchanting levels" as "+20%" is a small lie a player would plan around.
 */
public final class BuffNames
{
    private BuffNames()
    {
    }

    /** The buff's display name, translated by whoever renders it rather than by the server. */
    public static MutableComponent name(String buffType)
    {
        return Component.translatable(key(buffType));
    }

    /** {@code soulhome:xp_gain} to {@code buff.soulhome.xp_gain}, the key the lang file holds. */
    public static String key(String buffType)
    {
        final int separator = buffType.indexOf(':');

        return separator < 0
                ? "buff." + buffType
                : "buff." + buffType.substring(0, separator) + "." + buffType.substring(separator + 1);
    }

    /** A magnitude as its own unit: a percentage for a fraction, a flat number otherwise. */
    public static String magnitude(String buffType, double value)
    {
        return isFraction(buffType)
                ? String.format(Locale.ROOT, "+%.0f%%", value * 100d)
                : String.format(Locale.ROOT, "+%.1f", value);
    }

    /**
     * How much of this magnitude sits past the buff's soft ceiling (#86) - zero for the fifteen
     * buffs with no ceiling at all, and for any buff type this mod does not register an effect for.
     */
    public static double overflow(String buffType, double magnitude)
    {
        final SoulBuffEffect effect = SoulBuffEffects.get(buffType);
        return effect == null ? 0d : Math.max(0d, magnitude - effect.softCeiling());
    }

    /**
     * What the overflow becomes, or {@code null} if there is nothing to convert it into - see
     * {@link SoulBuffEffect#describeOverflow}. Only meaningful when {@link #overflow} is positive.
     */
    public static String describeOverflow(String buffType, double overflow)
    {
        final SoulBuffEffect effect = SoulBuffEffects.get(buffType);
        return effect == null ? null : effect.describeOverflow(overflow);
    }

    /**
     * The registered effect has the last word, since a buff another mod added may read its own
     * magnitude differently from anything this mod ships. Falling back to the id list rather than
     * to a fixed answer keeps the client honest on a screen the effect registry has not been
     * initialised for.
     */
    private static boolean isFraction(String buffType)
    {
        final SoulBuffEffect effect = SoulBuffEffects.get(buffType);

        return effect == null ? SoulBuffTypes.isFraction(buffType) : effect.isFraction();
    }
}
