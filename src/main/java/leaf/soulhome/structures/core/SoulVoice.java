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
 * <p>There is no loop and no bed: every one of these is a single distant sound, minutes of silence
 * apart. A loop a player can hum along with is worse than the silence it replaced, and the surest
 * way not to write one is not to have one.
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
