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
     * Alchemy lab: longer potions, as a fraction of the duration applied. Drunk, self-splashed or
     * stood in your own lingering cloud - never a potion thrown at something else. Beneficial
     * effects only; a harmful or neutral effect from your own potion is left exactly as brewed.
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

    public static final Set<String> BUILT_IN =
            Set.of(SATURATION, SWORD_DAMAGE, XP_GAIN, ENCHANTMENT_POWER,
                    POTION_DURATION, HEALING, MINING_SPEED,
                    SPEED, DOUBLE_JUMP, FALL_PROTECTION, FIRE_ASPECT);

    /**
     * Types measured as a flat amount rather than a proportion: a count of jumps, a number of
     * seconds, a number of effective levels. Held as a set rather than a single special case now
     * that enchanting power is no longer the only one.
     */
    private static final Set<String> NON_FRACTION = Set.of(ENCHANTMENT_POWER, DOUBLE_JUMP, FIRE_ASPECT);

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
