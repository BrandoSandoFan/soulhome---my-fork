/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * What one {@link FormClause} made of a region: a graded confidence, never a bare boolean - a
 * check that can only return 0 or 1 is a schematic wearing a costume, and rule 2 of the structural
 * considerations epic (#25) rejects that in review.
 *
 * @param confidence clamped to {@code [0, 1]}
 * @param diagnostic a short, player-facing reason for the score - empty when there is nothing
 *                    worth saying. Turned into a full sentence by #33; this is just the raw material.
 */
public record FormResult(double confidence, String diagnostic)
{
    public static final FormResult ZERO = new FormResult(0d, "");

    public FormResult
    {
        confidence = Math.max(0d, Math.min(1d, confidence));
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static FormResult of(double confidence, String diagnostic)
    {
        return new FormResult(confidence, diagnostic);
    }
}
