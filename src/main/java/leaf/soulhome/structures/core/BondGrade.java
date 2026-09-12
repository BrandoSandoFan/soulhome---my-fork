/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * What one {@link BondRelation} made of a pair of rooms.
 *
 * @param confidence clamped to {@code [0, 1]}
 * @param diagnostic a short reason, player-facing, and most useful when the confidence is zero -
 *                    "19 blocks away; within 12 would count" is actionable, "no bond" is not
 */
public record BondGrade(double confidence, String diagnostic)
{
    public static final BondGrade NONE = new BondGrade(0d, "");

    public BondGrade
    {
        confidence = Double.isNaN(confidence) ? 0d : Math.max(0d, Math.min(1d, confidence));
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static BondGrade of(double confidence, String diagnostic)
    {
        return new BondGrade(confidence, diagnostic);
    }
}
