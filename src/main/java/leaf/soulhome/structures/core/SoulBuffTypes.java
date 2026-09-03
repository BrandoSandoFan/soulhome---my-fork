/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Set;

/**
 * The buff ids the mod ships with.
 *
 * <p>These are strings in three places at once - the archetype JSON, the effect that applies them,
 * and any feedback that names them - and a typo in any one of those produces a room that
 * classifies perfectly and then does nothing. Naming them once here means the shipped definitions
 * can be checked against the shipped effects by a test rather than by a player noticing.
 *
 * <p>Datapacks are not limited to this set; an archetype may grant a buff added by another mod.
 * Anything unrecognised is warned about at load rather than rejected.
 */
public final class SoulBuffTypes
{
    /** Farm: extra saturation, as a fraction of the food's own value. */
    public static final String SATURATION = "soulhome:saturation";

    /** Armoury: extra sword damage, as a fraction of the hit. */
    public static final String SWORD_DAMAGE = "soulhome:sword_damage";

    /** Library: extra experience, as a fraction of the amount gained. */
    public static final String XP_GAIN = "soulhome:xp_gain";

    /** Enchanting room: extra effective enchanting levels. */
    public static final String ENCHANTMENT_POWER = "soulhome:enchantment_power";

    /**
     * Alchemy lab: your own potions know what to keep and what to cut short, as a fraction of the
     * duration applied. Drunk, self-splashed or stood in your own lingering cloud - never a potion
     * thrown at something else. A beneficial effect runs longer; a harmful one runs shorter; a
     * neutral effect (Glowing is the main one) is left exactly as brewed.
     */
    public static final String POTION_DURATION = "soulhome:potion_duration";

    /** Bedchamber: faster healing, as a fraction of the health restored. */
    public static final String HEALING = "soulhome:healing";

    /** Mine: faster block breaking, as a fraction of the speed. */
    public static final String MINING_SPEED = "soulhome:mining_speed";

    /** Track: extra movement speed, as a fraction of the player's own. */
    public static final String SPEED = "soulhome:speed";

    /** Training yard: extra jumps available while airborne. */
    public static final String DOUBLE_JUMP = "soulhome:double_jump";

    /** Training yard: less fall damage, as a fraction of the fall. */
    public static final String FALL_PROTECTION = "soulhome:fall_protection";

    /** Hearth: seconds the target is set on fire by a sword hit. */
    public static final String FIRE_ASPECT = "soulhome:fire_aspect";

    /**
     * Arcane sanctum: extra maximum mana, as a flat amount.
     *
     * <p>Written against another mod's attribute, which most installs do not have. That is not a
     * problem to solve: the room still classifies, the report still names the buff, and the
     * magnitude simply has nothing to be applied to until Iron's Spells is installed. See
     * {@code ManaEffect}.
     */
    public static final String MAX_MANA = "soulhome:max_mana";

    /** Ritual chamber: stronger spells, as a fraction of the spell's own power. */
    public static final String SPELL_POWER = "soulhome:spell_power";

    /** Workshop: extra reach in blocks, for placing and for hitting alike. */
    public static final String REACH = "soulhome:reach";

    /** Cold storage: less damage taken from fire and lava, as a fraction of the hit. */
    public static final String FIRE_RESISTANCE = "soulhome:fire_resistance";

    /** Shrine: experience kept on death that would otherwise be lost, as a fraction of it. */
    public static final String SOUL_EMBER = "soulhome:soul_ember";

    /** Greenhouse: hunger builds up slower, as a fraction of the exhaustion it would otherwise cost. */
    public static final String NOURISHED = "soulhome:nourished";

    /** Treasury: a chance of an extra drop when breaking a block, as a fraction chance. */
    public static final String FORTUNE = "soulhome:fortune";

    /** Trophy room: harder to knock back, as a fraction added to the player's own resistance. */
    public static final String KNOCKBACK_RESISTANCE = "soulhome:knockback_resistance";

    /** Aquarium: faster swimming, as a fraction of the player's own swim speed. */
    public static final String SWIM_SPEED = "soulhome:swim_speed";

    /** Watchtower: ores and hostiles outlined through terrain. Active (#88). */
    public static final String SURVEYORS_EYE = "soulhome:surveyors_eye";

    /** Bulwark: a bank of absorption that refills on its own. Active (#89). */
    public static final String AEGIS = "soulhome:aegis";

    /** Rift chamber: a short blink through blocks, landing in the first safe space. Active (#90). */
    public static final String SOUL_STEP = "soulhome:soul_step";

    /** Mead hall: a share of your own buffs to everyone nearby, and Strength either way. Active (#91). */
    public static final String RALLY = "soulhome:rally";

    /** Stable: summons your last-ridden tamed mount. Active (#92). */
    public static final String CALL_OF_THE_HERD = "soulhome:call_of_the_herd";

    /** Storm spire: lightning called down on hostiles nearby. Active (#94). */
    public static final String THUNDERCLAP = "soulhome:thunderclap";

    /** Powder magazine: a spread of explosive shots that break nothing. Active (#95). */
    public static final String BARRAGE = "soulhome:barrage";

    /** Infected grotto: an expanding shockwave in a cone. Active (#96). */
    public static final String RUPTURE = "soulhome:rupture";

    /**
     * The buffs that are pressed rather than carried (#87). Held apart from {@link #BUILT_IN}
     * because two things need to ask "is this an active" without knowing every id: the config's
     * per-ability switches, and the feedback that has to describe a magnitude as charges and a
     * radius rather than as a percentage.
     */
    public static final Set<String> ACTIVE =
            Set.of(SURVEYORS_EYE, AEGIS, SOUL_STEP, RALLY, CALL_OF_THE_HERD, THUNDERCLAP, BARRAGE, RUPTURE);

    public static final Set<String> BUILT_IN =
            Set.of(SATURATION, SWORD_DAMAGE, XP_GAIN, ENCHANTMENT_POWER,
                    POTION_DURATION, HEALING, MINING_SPEED,
                    SPEED, DOUBLE_JUMP, FALL_PROTECTION, FIRE_ASPECT,
                    MAX_MANA, SPELL_POWER, REACH,
                    FIRE_RESISTANCE, SOUL_EMBER, NOURISHED, FORTUNE, KNOCKBACK_RESISTANCE, SWIM_SPEED,
                    SURVEYORS_EYE, AEGIS, SOUL_STEP, RALLY, CALL_OF_THE_HERD, THUNDERCLAP, BARRAGE, RUPTURE);

    /**
     * Types measured as a flat amount rather than a proportion: a count of jumps, a number of
     * seconds, a number of effective levels, points of mana, blocks of reach. Held as a set rather
     * than a single special case now that enchanting power is no longer the only one.
     *
     * <p>Every active is in here too. An active's magnitude is not a proportion of anything - it is
     * the tier-scaled number its own effect reads to work out charges, radius and recharge, so
     * showing it as "+200%" would be inventing a unit it does not have.
     */
    private static final Set<String> NON_FRACTION =
            Set.of(ENCHANTMENT_POWER, DOUBLE_JUMP, FIRE_ASPECT, MAX_MANA, REACH,
                    SURVEYORS_EYE, AEGIS, SOUL_STEP, RALLY, CALL_OF_THE_HERD, THUNDERCLAP, BARRAGE, RUPTURE);

    /** Whether this buff is pressed rather than carried - see {@link #ACTIVE}. */
    public static boolean isActive(String buffType)
    {
        return ACTIVE.contains(buffType);
    }

    private SoulBuffTypes()
    {
    }

    /**
     * Whether a magnitude of this type is a proportion rather than a flat amount.
     *
     * <p>Magnitudes are unitless in the data, so anything that shows one to a player has to decide
     * between "+20%" and "+2". Kept here, next to the ids, so the book, the chat report and the
     * ceiling in the config all read the same value the same way. An effect may override this for
     * a type this mod does not ship - see {@code SoulBuffEffect#isFraction}.
     */
    public static boolean isFraction(String buffType)
    {
        return !NON_FRACTION.contains(buffType);
    }
}
