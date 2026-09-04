/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.datagen.language;

import leaf.soulhome.structures.core.SoulBuffTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each buff is called in English, in one place.
 *
 * <p>Two generators want these strings: {@link EngLangGen}, which writes the
 * {@code buff.soulhome.*} keys the game reads, and the guide book, whose room pages say what a
 * room grants. The book used to derive that from the buff's id instead - {@code soulhome:xp_gain}
 * rendered as "xp gain" - which is the id showing through the prose, and it disagreed with the
 * name the same buff carries in {@code /soulhome buffs}.
 *
 * <p>Ordered, so the lang file it feeds keeps a readable shape rather than a hash order.
 */
public final class BuffDisplayNames
{
    private static final Map<String, String> NAMES = build();

    private BuffDisplayNames()
    {
    }

    /** Buff type id to display name, in reading order. */
    public static Map<String, String> all()
    {
        return NAMES;
    }

    /**
     * One buff's name, falling back to its id read as words for a type this mod does not ship -
     * a datapack's own buff has no entry here and should still read as something.
     */
    public static String of(String buffType)
    {
        final String name = NAMES.get(buffType);

        if (name != null)
        {
            return name;
        }

        final int separator = buffType.indexOf(':');
        final String path = separator < 0 ? buffType : buffType.substring(separator + 1);

        return path.replace('_', ' ');
    }

    private static Map<String, String> build()
    {
        Map<String, String> names = new LinkedHashMap<>();

        names.put(SoulBuffTypes.SATURATION, "Saturation from food");
        names.put(SoulBuffTypes.SWORD_DAMAGE, "Sword damage");
        names.put(SoulBuffTypes.XP_GAIN, "Experience gain");
        names.put(SoulBuffTypes.ENCHANTMENT_POWER, "Enchanting levels");
        names.put(SoulBuffTypes.POTION_DURATION, "Potion duration");
        names.put(SoulBuffTypes.HEALING, "Healing");
        names.put(SoulBuffTypes.MINING_SPEED, "Mining speed");
        names.put(SoulBuffTypes.SPEED, "Movement speed");
        names.put(SoulBuffTypes.DOUBLE_JUMP, "Extra jumps");
        names.put(SoulBuffTypes.FALL_PROTECTION, "Fall damage reduction");
        names.put(SoulBuffTypes.FIRE_ASPECT, "Fire on hit");
        names.put(SoulBuffTypes.MAX_MANA, "Maximum mana");
        names.put(SoulBuffTypes.SPELL_POWER, "Spell power");
        names.put(SoulBuffTypes.REACH, "Reach");
        names.put(SoulBuffTypes.FIRE_RESISTANCE, "Fire damage reduction");
        names.put(SoulBuffTypes.SOUL_EMBER, "Experience kept on death");
        names.put(SoulBuffTypes.NOURISHED, "Slower hunger");
        names.put(SoulBuffTypes.FORTUNE, "Bonus drops");
        names.put(SoulBuffTypes.KNOCKBACK_RESISTANCE, "Knockback resistance");
        names.put(SoulBuffTypes.SWIM_SPEED, "Swim speed");

        //the actives (#87), named as what they do rather than as a magnitude, since an active's
        //number is charges and reach rather than a percentage of anything
        names.put(SoulBuffTypes.SURVEYORS_EYE, "Surveyor's Eye");
        names.put(SoulBuffTypes.AEGIS, "Aegis");
        names.put(SoulBuffTypes.SOUL_STEP, "Soul Step");
        names.put(SoulBuffTypes.RALLY, "Rally");
        names.put(SoulBuffTypes.CALL_OF_THE_HERD, "Call of the Herd");
        names.put(SoulBuffTypes.THUNDERCLAP, "Thunderclap");
        names.put(SoulBuffTypes.BARRAGE, "Barrage");
        names.put(SoulBuffTypes.RUPTURE, "Rupture");

        //unmodifiableMap rather than Map.copyOf: the copy would be free to reorder itself, and the
        //order here is the order the lang file is written in
        return Collections.unmodifiableMap(names);
    }
}
