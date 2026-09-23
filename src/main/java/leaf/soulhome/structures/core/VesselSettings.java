/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The numbers a Soul Vessel (#182) is spawned with, and what happens to it afterwards. One record
 * for the whole feature, not one per entry path: {@link #DEFAULT_KEY_FRAGILITY} is the Soul Key's,
 * {@link #DEFAULT_CUSHION_FRAGILITY} is Meditation's (#183), and {@link #DEFAULT_GAZE_FRAGILITY} is
 * Soulgaze's (#187) - the way {@link AttunementSettings} is one record for both of attunement's
 * pools rather than two.
 *
 * <p>Fragility multiplies a hit on the vessel before it is forwarded to its owner (#185). The
 * vessel never learns which door its owner used; it holds the number it was spawned with, and
 * that is all it needs.
 *
 * <p>{@link #damageTransfer} is the one switch a server that wants the old safety looks for. With
 * it off a vessel is still spawned and still holds its chunk - both are structural, per #190 - it
 * simply forwards nothing, and a soul is a safe room again.
 */
public record VesselSettings(
        boolean damageTransfer,
        float keyFragility,
        float cushionFragility,
        float gazeFragility,
        double dropScatter)
{
    /** On, per #181's decisions: it is the point of the feature, and a switch nobody knows to look for is not a default. */
    public static final boolean DEFAULT_DAMAGE_TRANSFER = true;

    /** Full damage, unscaled: the Soul Key is the fragile entry, per rule 2 of #181. */
    public static final float DEFAULT_KEY_FRAGILITY = 1.0f;

    /** Half damage: a cushion is the safer entry, per #183's own table. */
    public static final float DEFAULT_CUSHION_FRAGILITY = 0.5f;

    /**
     * Full damage. A gazer used neither a cushion nor a key, so inherits neither number by accident
     * (#187) - and "gazing is no safer than meditating" means no lower than the cushion's.
     */
    public static final float DEFAULT_GAZE_FRAGILITY = 1.0f;

    /**
     * Horizontal speed, in blocks per tick, of the outward push each spilled item gets (#186). Enough
     * to read as a body's belongings scattering rather than a neat pile, small enough that nothing
     * sails off a ledge the vessel was sitting beside.
     */
    public static final double DEFAULT_DROP_SCATTER = 0.12d;

    public static final VesselSettings DEFAULTS = new VesselSettings(
            DEFAULT_DAMAGE_TRANSFER,
            DEFAULT_KEY_FRAGILITY,
            DEFAULT_CUSHION_FRAGILITY,
            DEFAULT_GAZE_FRAGILITY,
            DEFAULT_DROP_SCATTER);

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

        if (gazeFragility <= 0f)
        {
            throw new IllegalArgumentException("gazeFragility must be positive, got " + gazeFragility);
        }

        if (dropScatter < 0d || Double.isNaN(dropScatter))
        {
            throw new IllegalArgumentException("dropScatter must not be negative, got " + dropScatter);
        }
    }

    /**
     * What a hit of {@code amount} on a vessel of this {@code fragility} costs its owner, or zero
     * with damage transfer off. Pure, so the one piece of #185 that is arithmetic can be pinned
     * offline.
     */
    public float forwardedDamage(float amount, float fragility)
    {
        if (!this.damageTransfer || amount <= 0f || Float.isNaN(amount))
        {
            return 0f;
        }

        return amount * fragility;
    }
}
