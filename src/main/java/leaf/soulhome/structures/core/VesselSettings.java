/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The numbers a Soul Vessel (#182) is spawned with. One record for the whole feature, not one per
 * entry path: {@link #DEFAULT_KEY_FRAGILITY} is the Soul Key's, and {@link #DEFAULT_CUSHION_FRAGILITY}
 * is Meditation's (#183) own addition, following the same rule this class's own javadoc set out for
 * it rather than starting a settings record of its own - the way {@link AttunementSettings} is one
 * record for both of attunement's pools rather than two. Soulgaze (#187) is expected to add a third
 * constant here the same way.
 *
 * <p>Fragility does nothing on its own - it is read only once damage transfer (#185) exists to
 * multiply an incoming hit by it. It is carried on the vessel from the moment it is spawned so
 * that #185 does not have to touch the spawning code at all.
 */
public record VesselSettings(float keyFragility, float cushionFragility)
{
    /** Full damage, unscaled: the Soul Key is the fragile entry, per rule 2 of #181. */
    public static final float DEFAULT_KEY_FRAGILITY = 1.0f;

    /** Half damage: a cushion is the safer entry, per #183's own table. */
    public static final float DEFAULT_CUSHION_FRAGILITY = 0.5f;

    public static final VesselSettings DEFAULTS = new VesselSettings(DEFAULT_KEY_FRAGILITY, DEFAULT_CUSHION_FRAGILITY);

    public VesselSettings
    {
        if (keyFragility <= 0f)
        {
            throw new IllegalArgumentException("keyFragility must be positive, got " + keyFragility);
        }

        if (cushionFragility <= 0f)
        {
            throw new IllegalArgumentException("cushionFragility must be positive, got " + cushionFragility);
        }
    }
}
