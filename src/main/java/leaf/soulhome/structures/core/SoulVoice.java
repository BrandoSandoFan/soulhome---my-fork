/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Which palette a single ambient one-shot is drawn from (#166).
 *
 * <p>Chosen here rather than on the client so that "what does this soul sound like" is decided by
 * the same blend, with the same rules, as what it looks like - and so it can be tested without a
 * sound engine. The client owns only the mapping from a voice to actual sound events.
 *
 * <p>Every one of these is a single distant sound, minutes of silence apart. That is what a voice
 * <i>is</i>: the ambient bed under them (#210) is not drawn from this enum at all, because the bed
 * has to be one continuous thing and a voice has to be one thing at a time. Mixing the two up is
 * the mistake {@code SoulAmbience.bedMix} exists to avoid - a soul heard as first one voice and
 * then another, some minutes apart, reads as a soul that cannot make up its mind, and this mod's
 * rule everywhere else is that a soul built two ways is a third thing rather than an indecisive
 * one.
 */
public enum SoulVoice
{
    /** The soul itself, whatever is built in it. Always in the draw. */
    BASE,

    WARM,
    COLD,

    /** Warm and cold at once - see {@link SoulAxis}. */
    STEAM,

    ARCANE,
    WROUGHT,

    /** Worked matter and the arcane at once. */
    QUICKENED,

    VERDANT,
    HOLLOW,

    /** Growing things and emptied ones at once. */
    OVERGROWN
}
