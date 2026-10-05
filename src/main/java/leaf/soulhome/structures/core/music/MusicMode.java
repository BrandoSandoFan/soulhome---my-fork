/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

/**
 * The seven-note modes a soul's music is written in (#163).
 *
 * <p>Modes rather than keys-with-accidentals, because a mode is a whole mood in one word and every
 * note of it is consonant with the drone under it. That is what lets a composer that has never been
 * listened to stay inside something pleasant by construction: whatever it picks, it picks from here.
 *
 * <p>Each mode's {@link #colourDegree()} is the one note that makes it itself - the raised fourth of
 * Lydian, the flat second of Phrygian - and the composer leans on it, because a Lydian piece that
 * never plays its fourth is a major piece that happens to be allowed to.
 */
public enum MusicMode
{
    IONIAN(new int[] {0, 2, 4, 5, 7, 9, 11}, 2),
    DORIAN(new int[] {0, 2, 3, 5, 7, 9, 10}, 5),
    PHRYGIAN(new int[] {0, 1, 3, 5, 7, 8, 10}, 1),
    LYDIAN(new int[] {0, 2, 4, 6, 7, 9, 11}, 3),
    MIXOLYDIAN(new int[] {0, 2, 4, 5, 7, 9, 10}, 6),
    AEOLIAN(new int[] {0, 2, 3, 5, 7, 8, 10}, 5);

    private final int[] steps;
    private final int colourDegree;

    MusicMode(int[] steps, int colourDegree)
    {
        this.steps = steps;
        this.colourDegree = colourDegree;
    }

    /** The degree, 0 to 6, that distinguishes this mode from its neighbours. */
    public int colourDegree()
    {
        return this.colourDegree;
    }

    /**
     * The MIDI note of a scale degree above {@code tonic}. Any integer is a degree: 7 is the octave,
     * -1 the leading note below the tonic, so melodic arithmetic never has to wrap by hand.
     */
    public int note(int tonic, int degree)
    {
        final int octave = Math.floorDiv(degree, 7);
        final int within = Math.floorMod(degree, 7);

        return tonic + 12 * octave + this.steps[within];
    }

    /** Whether the triad on this degree has a perfect fifth - every degree but one, in every mode. */
    public boolean stable(int degree)
    {
        return note(0, degree + 4) - note(0, degree) == 7;
    }

    /** Whether a MIDI note belongs to this mode on this tonic, in any octave. */
    public boolean contains(int tonic, int midi)
    {
        final int pitchClass = Math.floorMod(midi - tonic, 12);

        for (int step : this.steps)
        {
            if (step == pitchClass)
            {
                return true;
            }
        }

        return false;
    }
}
