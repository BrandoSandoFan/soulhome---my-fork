/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

/**
 * What {@link SoulSynth} can play. Each is a small, well-known synthesis recipe rather than a
 * sample, because the music is composed while it plays and a sample library is a download this mod
 * would then owe somebody a licence for.
 *
 * <p>Chosen for how they sit in a space people build in for hours: nothing with a hard attack
 * except the plucked and struck ones, and those decay rather than sustain.
 */
public enum Instrument
{
    /** Three detuned saws through a slow lowpass. The floor every piece stands on. */
    PAD,

    /** An FM electric piano - a hearth's instrument, round and close. */
    EPIANO,

    /** An FM bell with an inharmonic ratio - ice, water, the cold. */
    BELL,

    /** Stretched sine partials with a slow vibrato - glass harmonica, the arcane. */
    GLASS,

    /** A Karplus-Strong string - worked metal and wood, the workshop. */
    PLUCK,

    /** Marimba partials - growing things, a kalimba in a greenhouse. */
    MALLET,

    /** A low fifth that swells and never quite settles - emptied places. */
    DRONE,

    /** A breathy sine with delayed vibrato - the one voice that sings. */
    FLUTE
}
