/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

/**
 * One note of a composed piece.
 *
 * @param start      seconds from the start of the piece
 * @param duration   seconds it is held before its release begins; a struck instrument ignores
 *                   this and simply rings out
 * @param midi       the pitch, as a MIDI note number
 * @param velocity   0 to 1
 * @param instrument what plays it
 * @param pan        -1 hard left to 1 hard right
 */
public record NoteEvent(double start, double duration, int midi, float velocity, Instrument instrument, float pan)
{
    /** The pitch in hertz, equal temperament on A440. */
    public double frequency()
    {
        return 440d * Math.pow(2d, (this.midi - 69) / 12d);
    }
}
