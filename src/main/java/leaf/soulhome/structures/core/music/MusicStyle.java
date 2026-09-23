/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;

/**
 * How each voice of a soul (#166) is played as music (#163).
 *
 * <p>The same ten voices the ambience already speaks with, so a soul's music and its ambience are
 * one character heard two ways rather than two systems that happen to agree. And the same rule that
 * keeps every other part of this epic honest: a contested voice is its own thing. Steam is not the
 * warm style and the cold style taking turns - it is Lydian, the brightest mode, played on both the
 * hearth's piano and the cold's bells at once, because warm and cold together is the air between
 * them and that air is bright.
 *
 * <p>In Java rather than in archetype JSON for #167's reason: a datapack chooses which traits its
 * room pulls toward, and so which of these it hears; it does not get to write a mode or an
 * instrument that makes a soul unbearable to build in.
 *
 * @param mode        what the harmony is drawn from
 * @param lead        the melodic instrument
 * @param texture     what plays the arpeggios and answers under the lead - never {@link Instrument#DRONE},
 *                    which is a floor rather than a voice and is asked for through {@code drone}
 * @param brightness  0 to 1: how open the pad's filter is
 * @param leadOctave  octaves above the pad the lead sits
 * @param tempo       a multiplier on the rank's tempo; below one is slower
 * @param density     0 to 1: how often the lead speaks at the height of a piece
 * @param drone       whether a drone holds the tonic under the whole piece
 * @param seventh     whether the pad voices sevenths, or stays with open fifths and seconds
 */
public record MusicStyle(
        MusicMode mode,
        Instrument lead,
        Instrument texture,
        double brightness,
        int leadOctave,
        double tempo,
        double density,
        boolean drone,
        boolean seventh)
{
    /** The style a voice is played in. Every voice has one, so an unrecognised soul is never silent. */
    public static MusicStyle of(SoulVoice voice)
    {
        return switch (voice)
        {
            // the place itself: major, soft, a mallet answering a pad - a soul before it is a mix of rooms
            case BASE -> new MusicStyle(MusicMode.IONIAN, Instrument.MALLET, Instrument.GLASS, 0.45d, 1, 1.0d, 0.55d, false, false);

            // close and round: a hearth's piano over a warm pad, sevenths for the warmth in them
            case WARM -> new MusicStyle(MusicMode.MIXOLYDIAN, Instrument.EPIANO, Instrument.PLUCK, 0.55d, 1, 1.0d, 0.65d, false, true);

            // high, glassy, slow: bells over a thin pad, minor and never sad - Dorian's raised sixth
            case COLD -> new MusicStyle(MusicMode.DORIAN, Instrument.BELL, Instrument.GLASS, 0.3d, 2, 0.85d, 0.5d, false, false);

            // both at once, and brighter than either: the piano and the bells in Lydian
            case STEAM -> new MusicStyle(MusicMode.LYDIAN, Instrument.EPIANO, Instrument.BELL, 0.6d, 1, 0.95d, 0.6d, false, true);

            // the raised fourth that makes Lydian sound like it is floating; glass harmonica over it
            case ARCANE -> new MusicStyle(MusicMode.LYDIAN, Instrument.GLASS, Instrument.BELL, 0.4d, 2, 0.8d, 0.5d, true, true);

            // plucked strings in a steady pulse, low and Dorian - the workshop keeping time
            case WROUGHT -> new MusicStyle(MusicMode.DORIAN, Instrument.PLUCK, Instrument.MALLET, 0.5d, 1, 1.1d, 0.7d, false, false);

            // worked matter run through with the arcane: strings under glass, in Mixolydian
            case QUICKENED -> new MusicStyle(MusicMode.MIXOLYDIAN, Instrument.GLASS, Instrument.PLUCK, 0.5d, 1, 1.05d, 0.6d, false, true);

            // growing things: a flute over marimba, major and open
            case VERDANT -> new MusicStyle(MusicMode.IONIAN, Instrument.FLUTE, Instrument.MALLET, 0.6d, 1, 1.0d, 0.6d, false, false);

            // emptied places: a drone, a low bell, a great deal of room between the notes
            case HOLLOW -> new MusicStyle(MusicMode.AEOLIAN, Instrument.BELL, Instrument.GLASS, 0.25d, 1, 0.75d, 0.3d, true, false);

            // growth that has taken somewhere emptied: marimba over the drone, Aeolian
            case OVERGROWN -> new MusicStyle(MusicMode.AEOLIAN, Instrument.MALLET, Instrument.FLUTE, 0.4d, 1, 0.9d, 0.5d, true, false);
        };
    }
}
