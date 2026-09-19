/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The numbers a Soul Vessel (#182) is spawned with. One record for the whole feature, not one per
 * entry path: the Soul Key is the only path that spawns a vessel today, so
 * {@link #DEFAULT_KEY_FRAGILITY} is the only constant actually read yet. Meditation (#183) and
 * Soulgaze (#187) are expected to add their own {@code DEFAULT_*} fragility constants here rather
 * than starting a settings record of their own, the same way {@link AttunementSettings} is one
 * record for both of attunement's pools rather than two.
 *
 * <p>Fragility does nothing on its own - it is read only once damage transfer (#185) exists to
 * multiply an incoming hit by it. It is carried on the vessel from the moment it is spawned so
 * that #185 does not have to touch the spawning code at all.
 */
public record VesselSettings(float keyFragility)
{
    /** Full damage, unscaled: the Soul Key is the fragile entry, per rule 2 of #181. */
    public static final float DEFAULT_KEY_FRAGILITY = 1.0f;

    public static final VesselSettings DEFAULTS = new VesselSettings(DEFAULT_KEY_FRAGILITY);

    public VesselSettings
    {
        if (keyFragility <= 0f)
        {
            throw new IllegalArgumentException("keyFragility must be positive, got " + keyFragility);
        }
    }
}
