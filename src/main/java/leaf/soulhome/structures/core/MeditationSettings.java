/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The two channel lengths meditation and the Soul Key hold you for (#183/#184), and how close a
 * Meditation Cushion has to be for meditation to accept one at all. One record for both entry
 * paths rather than one each: {@link #keyChannelTicks} moves the Soul Key's own duration here from
 * a local constant on {@code SoulKeyItem} rather than starting a settings record of its own, the
 * way {@link AttunementSettings} is one record for both of attunement's pools.
 *
 * <p>Meditation is the shorter of the two on purpose. The cushion is what earns the shorter hold -
 * a cheap, common trip - and the reduced fragility on {@link VesselSettings#cushionFragility}; the
 * Soul Key stays the longer, costlier way in for a player with no cushion, per #183's own table.
 * Neither number changes what meditation returns you to: see {@code MeditationService} and
 * {@code VesselLifecycleService#onKeyUse} for why coming back is always the same trip regardless of
 * which door you used to leave.
 */
public record MeditationSettings(int cushionChannelTicks, int keyChannelTicks, boolean cushionDiagonalAdjacency)
{
    /** ~3 seconds at 20 ticks/second - #183's own figure for the cushion's channel. */
    public static final int DEFAULT_CUSHION_CHANNEL_TICKS = 60;

    /** 80 ticks - the Soul Key's own duration, moved here rather than left as a local constant. */
    public static final int DEFAULT_KEY_CHANNEL_TICKS = 80;

    /**
     * Whether a cushion diagonally adjacent to the player (rather than only the four cardinal
     * neighbours) still counts. On by default: a player standing at a cushion's corner should not
     * have to shuffle a step to be recognised as standing beside it.
     */
    public static final boolean DEFAULT_CUSHION_DIAGONAL_ADJACENCY = true;

    public static final MeditationSettings DEFAULTS = new MeditationSettings(
            DEFAULT_CUSHION_CHANNEL_TICKS, DEFAULT_KEY_CHANNEL_TICKS, DEFAULT_CUSHION_DIAGONAL_ADJACENCY);

    public MeditationSettings
    {
        if (cushionChannelTicks < 1)
        {
            throw new IllegalArgumentException("cushionChannelTicks must be at least 1, got " + cushionChannelTicks);
        }

        if (keyChannelTicks < 1)
        {
            throw new IllegalArgumentException("keyChannelTicks must be at least 1, got " + keyChannelTicks);
        }
    }
}
