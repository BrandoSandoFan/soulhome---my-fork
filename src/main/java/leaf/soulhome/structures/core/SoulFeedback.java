/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * A moment of this mod's own audio that the ambience has to get out of the way of (#212).
 *
 * <p>#166's rule is that nothing the ambience does may make it harder to hear the ascension hum
 * (#114), an ability firing, or a soul key. Until now that rule was kept by accident - the one-shots
 * were quiet, and before #208 they were inaudible - which is not the same as keeping it.
 *
 * <h2>Why a kind rather than a sound event</h2>
 *
 * <p>The obvious client-side implementation listens on Forge's {@code PlaySoundEvent} and matches
 * the sound being started. That cannot work here: every sound this mod plays is a vanilla event
 * with a meaning of its own, so a duck keyed on {@code BEACON_AMBIENT} would also duck for somebody
 * else's beacon, and one keyed on {@code ANVIL_LAND} would duck every time a player used an anvil.
 * Matching instead on {@code SoundSource.PLAYERS} inside a soul would catch the whole of it -
 * including a player walking, and including every block they place, which is the one thing the
 * owner of #212 asked explicitly not to happen.
 *
 * <p>So the mark is put where the knowledge actually is: at this mod's own call sites, through
 * {@code SoulSounds#playFeedback}. A sound nobody routed through there never holds anything, which
 * makes footsteps and block-placing correct by construction rather than by a filter somebody has to
 * remember to maintain.
 *
 * <p>Minecraft-free, and the hold lengths live here rather than on the client, so
 * {@code SoulAmbienceTest} can pin them.
 */
public enum SoulFeedback
{
    /**
     * The ascension ritual (#114) - its milestone hums and the beacon note it completes on. The
     * longest of the three by far, and the caller passes its own length rather than taking the
     * default: the ritual's duration is a server config value, so only the ritual knows it.
     */
    RITUAL("ritual", 120),

    /** A soul ability firing. Long enough to cover the cue and its tail, short enough to be a blip. */
    ABILITY("ability", 60),

    /** A soul key carrying somebody in or out. */
    KEY("key", 60);

    private final String id;
    private final int defaultHoldTicks;

    SoulFeedback(String id, int defaultHoldTicks)
    {
        this.id = id;
        this.defaultHoldTicks = defaultHoldTicks;
    }

    public String id()
    {
        return this.id;
    }

    /** How long the ambience stays out of the way, unless the caller knows better. */
    public int defaultHoldTicks()
    {
        return this.defaultHoldTicks;
    }

    /**
     * An id from the wire. An unknown one reads as {@link #ABILITY} rather than throwing: a client
     * a version behind should keep the rule this exists to enforce, and losing the exact flavour of
     * a hold costs nothing anyone can hear.
     */
    public static SoulFeedback byId(String id)
    {
        for (SoulFeedback kind : values())
        {
            if (kind.id.equals(id))
            {
                return kind;
            }
        }

        return ABILITY;
    }
}
