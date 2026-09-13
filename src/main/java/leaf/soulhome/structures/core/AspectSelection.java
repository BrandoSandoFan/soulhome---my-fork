/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;

/**
 * Which aspect a room took, what came second, and what would tip it - the Aspects epic (#171).
 *
 * <p>Everything here is comparison working, kept so the report can be written from what was
 * actually decided rather than reconstructed afterwards, exactly as {@link ArchetypeScore} keeps
 * the scoring working. <b>None of it reaches a magnitude.</b> A room's score and the buff's size
 * are settled before an aspect is looked at and are not touched by anything on this record; see
 * {@link Aspect} and {@link AspectSelector}.
 *
 * <p>An aspect chosen silently is a room that grants the wrong buff for no visible reason, which a
 * player is right to read as a bug. So the near miss is carried as well as the winner, and
 * {@link #tip} says what would change it, in blocks, the way the near-miss archetype reporting
 * already does.
 *
 * @param takenId          the aspect that took the room, never null
 * @param takenDisplayName its translation key
 * @param takenIsDefault   whether the room kept the archetype's own payout
 * @param heldByDefault    the default kept the room because the aspect leading on support did not
 *                         clear {@code margin}. Worth reporting as its own thing: "a scriptorium is
 *                         ahead but not far enough" is a different sentence from "this is an
 *                         archive", and the first one is actionable.
 * @param margin           how far clear of the default a challenger had to be - see
 *                         {@link ScoringSettings#aspectMargin}
 * @param supports         every aspect and what it stood at, best first. The order ties are broken
 *                         in is the archetype's own declaration order, so a scan cannot disagree
 *                         with the one before it.
 * @param tip              what would hand the room to the runner-up, or null when there is no
 *                         runner-up or no block that would do it
 */
public record AspectSelection(
        String takenId,
        String takenDisplayName,
        boolean takenIsDefault,
        boolean heldByDefault,
        double margin,
        List<Support> supports,
        Tip tip)
{
    public AspectSelection
    {
        supports = List.copyOf(supports);
    }

    /** The aspect that came second, or null when the archetype declares only one. */
    public Support runnerUp()
    {
        for (Support support : this.supports)
        {
            if (!support.aspectId().equals(this.takenId))
            {
                return support;
            }
        }

        return null;
    }

    public Support taken()
    {
        for (Support support : this.supports)
        {
            if (support.aspectId().equals(this.takenId))
            {
                return support;
            }
        }

        return null;
    }

    /** Whether there is a second aspect worth naming at all. */
    public boolean hasRunnerUp()
    {
        return runnerUp() != null;
    }

    /**
     * One aspect and what the room's contents said for it.
     *
     * @param support the comparison figure, and only that. It is meaningless on its own and is
     *                shown to a player only next to another aspect's.
     */
    public record Support(String aspectId, String displayName, boolean isDefault, double support)
    {
    }

    /**
     * What would hand the room to another aspect, said in blocks because that is what a player can
     * act on - "three more lecterns would make this a Scriptorium".
     *
     * @param blockDescription the matcher that would do it, as {@link BlockMatcher#describe}
     *                         writes it, for {@code BlockNames} to turn into prose
     * @param blocksNeeded     how many more of it, always at least 1
     */
    public record Tip(String aspectId, String displayName, String blockDescription, int blocksNeeded)
    {
    }
}
