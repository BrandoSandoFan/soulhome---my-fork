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

    public static final Set<String> BUILT_IN =
            Set.of(SATURATION, SWORD_DAMAGE, XP_GAIN, ENCHANTMENT_POWER);

    private SoulBuffTypes()
    {
    }
}
