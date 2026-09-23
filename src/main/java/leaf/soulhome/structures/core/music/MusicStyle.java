/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core.music;

import leaf.soulhome.structures.core.SoulVoice;

/**
 * How each voice of a soul (#166) is played as music (#163), and what it sounds like to be in the
 * room it stands for (#261).
 *
 * <p>The same ten voices the ambience already speaks with, so a soul's music and its ambience are
 * one character heard two ways rather than two systems that happen to agree. And the same rule that
 * keeps every other part of this epic honest: a contested voice is its own thing. Steam is not the
 * warm style and the cold style taking turns - it is warm's flicker thinned out with a bell
 * answering it and vents chuffing underneath, because warm and cold together is a working engine.
 *
 * <p>The owner's first listen (#261) set the direction for all ten: a workshop is rhythmic, with a
 * solid beat and clanking; a hearth is quick, middle-register notes over a calm base with the fire
 * crackling under it. Every other voice is extrapolated from those two - see the {@link Groove} each
 * one plays and the table on #261.
 *
 * <p>In Java rather than in archetype JSON for #167's reason: a datapack chooses which traits its
 * room pulls toward, and so which of these it hears; it does not get to write a mode or an
 * instrument that makes a soul unbearable to build in.
 *
 * @param mode       what the harmony is drawn from
 * @param groove     how the piece moves - the part #261 found was the same for every voice
 * @param lead       the melodic instrument
 * @param texture    what answers under the lead
 * @param brightness 0 to 1: how open the pad's filter is
 * @param bpm        the tempo at rank 0; rank slows it a little, never speeds it up
 * @param density    0 to 1: how busy the lead is at the height of a piece
 * @param drone      whether a drone holds the tonic under the whole piece
 * @param seventh    whether the pad voices sevenths, or crowns the chord with its fifth
 */
public record MusicStyle(
        MusicMode mode,
        Groove groove,
        Instrument lead,
        Instrument texture,
        double brightness,
        double bpm,
        double density,
        boolean drone,
        boolean seventh)
{
    /**
     * How a piece moves, one per mood. The composer has a writer for each; what they share is only
     * the pad under them, the key and the form.
     */
    public enum Groove
    {
        /** The place itself: a soft pad and a mallet, nothing to report. */
        CALM,

        /** Fire: quick flickering figures in the middle of the range over a still base, crackling. */
        FLICKER,

        /** A workshop: a hammer on the beat, the anvil on the backbeat, a driving bass under a riff. */
        FORGE,

        /** Cold: a bell or two a bar, a great deal of air, wind going past. */
        DRIFT,

        /** The arcane: low rolling arpeggios over a drone, each chord breathed in by a swell. */
        SHIMMER,

        /** Growing things: a lilting triplet ostinato under a flute, birds and leaves. */
        LILT,

        /** Emptied places: a low bell tolling on the downbeat over a drone, water dripping. */
        TOLL,

        /** Steam: the fire's flicker, thinner, answered by a bell over vents chuffing on the off-beats. */
        VENT,

        /** Worked matter and the arcane: a clock's tick-tock under a plucked ostinato and glass. */
        CLOCKWORK,

        /** Overgrowth: a sparse marimba over the drone, drips and rustle. */
        DAMP
    }

    /** The style a voice is played in. Every voice has one, so an unrecognised soul is never silent. */
    public static MusicStyle of(SoulVoice voice)
    {
        return switch (voice)
        {
            case BASE -> new MusicStyle(
                    MusicMode.IONIAN, Groove.CALM, Instrument.MALLET, Instrument.GLASS, 0.4d, 72d, 0.55d, false, false);

            case WARM -> new MusicStyle(
                    MusicMode.MIXOLYDIAN, Groove.FLICKER, Instrument.EPIANO, Instrument.MALLET, 0.45d, 88d, 0.8d, false, true);

            case COLD -> new MusicStyle(
                    MusicMode.DORIAN, Groove.DRIFT, Instrument.BELL, Instrument.GLASS, 0.3d, 66d, 0.55d, false, false);

            case STEAM -> new MusicStyle(
                    MusicMode.LYDIAN, Groove.VENT, Instrument.EPIANO, Instrument.BELL, 0.45d, 84d, 0.6d, false, true);

            case ARCANE -> new MusicStyle(
                    MusicMode.LYDIAN, Groove.SHIMMER, Instrument.GLASS, Instrument.BELL, 0.3d, 64d, 0.5d, true, true);

            case WROUGHT -> new MusicStyle(
                    MusicMode.DORIAN, Groove.FORGE, Instrument.PLUCK, Instrument.MALLET, 0.4d, 104d, 0.8d, false, false);

            case QUICKENED -> new MusicStyle(
                    MusicMode.MIXOLYDIAN, Groove.CLOCKWORK, Instrument.GLASS, Instrument.PLUCK, 0.4d, 96d, 0.6d, false, true);

            case VERDANT -> new MusicStyle(
                    MusicMode.IONIAN, Groove.LILT, Instrument.FLUTE, Instrument.MALLET, 0.5d, 84d, 0.65d, false, false);

            case HOLLOW -> new MusicStyle(
                    MusicMode.AEOLIAN, Groove.TOLL, Instrument.BELL, Instrument.GLASS, 0.25d, 54d, 0.35d, true, false);

            case OVERGROWN -> new MusicStyle(
                    MusicMode.AEOLIAN, Groove.DAMP, Instrument.MALLET, Instrument.FLUTE, 0.35d, 66d, 0.5d, true, false);
        };
    }
}
