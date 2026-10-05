/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

/**
 * What {@link SoulSynth} can play. Each is a small, well-known synthesis recipe rather than a
 * sample, because the music is composed while it plays and a sample library is a download this mod
 * would then owe somebody a licence for.
 *
 * <p>Two kinds. The pitched instruments carry the harmony and the tune, and every note they play is
 * in the piece's mode. The foley ones (#261) are the room itself heard inside the music - the anvil
 * in a workshop, the crackle in a hearth - placed on the music's grid so a clank lands on a beat
 * rather than wherever it likes. Their pitch, where they have one, is a colour rather than a note.
 */
public enum Instrument
{
    /** Two saws a couple of cents apart through a slow lowpass. The floor every piece stands on. */
    PAD(true),

    /** An FM electric piano - a hearth's instrument, round and close. */
    EPIANO(true),

    /** A soft, low-index FM bell - ice and water, heard in the middle of the range rather than above it. */
    BELL(true),

    /** Stretched sine partials, no vibrato - glass harmonica, the arcane. */
    GLASS(true),

    /** A Karplus-Strong string - worked metal and wood, and the workshop's bass. */
    PLUCK(true),

    /** Marimba partials - growing things, a kalimba in a greenhouse. */
    MALLET(true),

    /** A low fifth that swells and never quite settles - emptied places. */
    DRONE(true),

    /** A breathy sine with the lightest vibrato - the one voice that sings. */
    FLUTE(true),

    /** A hammer on wood: a short sine thump falling in pitch. The workshop's beat. */
    THUMP(false),

    /** A struck anvil: inharmonic metal modes over a click, gone in a third of a second. */
    CLANK(false),

    /** A ratchet or a clock: a tick of filtered noise. */
    TICK(false),

    /** Fire: a bed of small pops held for as long as the note is, each pop a burst of filtered noise. */
    CRACKLE(false),

    /** A steam vent: a band of noise that opens and closes. */
    HISS(false),

    /** Wind: low-passed noise swelling and falling. */
    WIND(false),

    /** A drop of water: a sine that rises as it dies. */
    DRIP(false),

    /** A bird: a short falling-then-rising whistle, kept low and quiet. */
    CHIRP(false),

    /** Leaves: soft bursts of mid-band noise. */
    RUSTLE(false),

    /** The arcane breathing in: noise rising in pitch and level into the next chord, then gone. */
    SWELL(false);

    private final boolean pitched;

    Instrument(boolean pitched)
    {
        this.pitched = pitched;
    }

    /** Whether its notes are notes - in the piece's mode, and held to the register ceiling. */
    public boolean pitched()
    {
        return this.pitched;
    }
}
