/*
 * File created ~ 11 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code facing}: does {@code of} point at {@code to} - a ring of chairs turned inward towards the
 * fire rather than out towards the walls, a lectern's stand angled at the reader, a bed with its
 * head away from the door. See #36 of the structural considerations epic (#25).
 *
 * <p>Deliberately its own relation rather than a field on {@link BlockSignature}: the game-side
 * signature keys on the {@code Block}, not the {@code BlockState}, on purpose - a lit and an unlit
 * candle are one entry, because they are one thing to a player counting candles.
 * Widening it to carry rotation would multiply every directional block's signature by four to six
 * and change what "16 bookshelves" means throughout the classifier. Orientation is positional data
 * instead, riding alongside each {@link RegionGeometry.Cell}'s coordinates rather than its
 * identity - see {@link Facing}.
 *
 * <p><b>Graded per cell, not all-or-nothing.</b> Each {@code of} cell scores against whichever
 * {@code to} cell it is best aligned with, on a smooth ramp rather than a hit/miss test: pointed
 * exactly at it scores 1.0, off by one compass sector still scores highly (a player who built a
 * ring of stairs will get several exactly right and a couple off by a notch, and this should read
 * as "basically facing in", not as failure), and perpendicular or facing away scores toward zero.
 * The clause's own confidence is the average of those per-cell scores - see {@code diagnostic}.
 *
 * <p><b>A cell with no orientation counts for neither side.</b> Most of a room is not directional
 * blocks, so an {@code of} matcher that happens to catch a non-directional block (stone in with the
 * chairs, say) must not drag the score down the way actually facing the wrong way would - it is
 * simply excluded from the average, the same way a missing {@code to} candidate excludes nothing
 * but itself. Only when every matched {@code of} cell lacks a facing does the clause fall back to
 * the "nothing to judge" zero the empty-match case already uses.
 */
public final class FacingClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "facing";
    }

    @Override
    public Kind kind()
    {
        return Kind.RELATION;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.required("to", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("max_distance", ClauseParamSpec.Type.INT, 6),
                ClauseParamSpec.optional("tolerance", ClauseParamSpec.Type.STRING, "adjacent_sector"));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new FacingClause(
                params.getElement("of"), params.getElement("to"),
                params.getInt("max_distance"), params.getString("tolerance"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        FacingClause facing = (FacingClause) clause;
        return Map.of(
                "of", facing.of(), "to", facing.to(),
                "max_distance", facing.maxDistance(), "tolerance", facing.tolerance());
    }
}

record FacingClause(String of, String to, int maxDistance, String tolerance) implements FormClause
{
    /**
     * Full credit for anything inside the same 45-degree compass sector as {@code of}'s own facing
     * - a stair placed exactly right, and the small aiming slop nobody would call "off".
     */
    private static final double FULL_CREDIT_DEGREES = 22.5;
    private static final double EXACT_ALIGNMENT_EPSILON = 1e-9;

    @Override
    public String typeId()
    {
        return "facing";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher ofMatcher = elements.get(this.of);
        BlockMatcher toMatcher = elements.get(this.to);

        if (ofMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        if (toMatcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.to));
        }

        List<RegionGeometry.Cell> ofCells = geometry.cellsMatching(ofMatcher);
        List<RegionGeometry.Cell> toCells = geometry.cellsMatching(toMatcher);

        if (ofCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        if (toCells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.to));
        }

        final boolean exactTolerance = exactTolerance();
        final double zeroCreditDegrees = zeroCreditDegrees(exactTolerance);

        double total = 0d;
        int judged = 0;
        int facingAway = 0;
        int outOfRange = 0;

        for (RegionGeometry.Cell of : ofCells)
        {
            if (of.facing() == null)
            {
                continue;
            }

            judged++;

            double best = 0d;
            boolean candidateInRange = false;

            for (RegionGeometry.Cell to : toCells)
            {
                if (to == of)
                {
                    continue;
                }

                if (ClauseMath.distance(of, to, "euclidean") > this.maxDistance)
                {
                    continue;
                }

                candidateInRange = true;
                best = Math.max(best, gradeAlignment(of, to, exactTolerance, zeroCreditDegrees));
            }

            total += best;

            if (!candidateInRange)
            {
                outOfRange++;
                continue;
            }

            if (best < 0.5d)
            {
                facingAway++;
            }
        }

        if (judged == 0)
        {
            return FormResult.of(0d, "none of '" + this.of + "' carry a facing direction");
        }

        double confidence = ClauseMath.clamp01(total / judged);
        return FormResult.of(confidence, diagnostic(outOfRange, facingAway, judged));
    }

    /**
     * The angle between {@code of}'s own facing direction and the straight line to {@code to},
     * turned into a 1.0-to-0.0 ramp: within {@link #FULL_CREDIT_DEGREES} of dead-on is full credit,
     * beyond {@code zeroCreditDegrees} is none, and the gap between the two ramps down linearly -
     * exactly the "off by one sector still counts nearly in full" shape #36 asks for.
     */
    private double gradeAlignment(RegionGeometry.Cell of, RegionGeometry.Cell to,
                                  boolean exactTolerance, double zeroCreditDegrees)
    {
        final Facing facing = of.facing();
        final double dx = to.x() - of.x();
        final double dy = to.y() - of.y();
        final double dz = to.z() - of.z();
        final double magnitude = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (magnitude == 0d)
        {
            return 0d;
        }

        final double dot = facing.dx * dx + facing.dy * dy + facing.dz * dz;
        final double cosine = Math.max(-1d, Math.min(1d, dot / magnitude));
        final double angle = Math.toDegrees(Math.acos(cosine));

        if (exactTolerance)
        {
            return angle <= EXACT_ALIGNMENT_EPSILON ? 1d : 0d;
        }

        if (angle <= FULL_CREDIT_DEGREES)
        {
            return 1d;
        }

        if (angle >= zeroCreditDegrees)
        {
            return 0d;
        }

        return 1d - (angle - FULL_CREDIT_DEGREES) / (zeroCreditDegrees - FULL_CREDIT_DEGREES);
    }

    /**
     * {@code adjacent_sector} (the default) ramps down over the next two 45-degree compass
     * sectors, so a build one sector off - the common case, since nobody places a whole ring of
     * stairs with the same one wrong - still scores around three-quarters credit, and only a
     * genuinely sideways or reversed block reads as not facing. {@code exact} asks for dead-ahead
     * alignment and nothing more: any sideways component, however small, scores zero. Anything else
     * - a typo, a future value not yet understood - falls back to {@code adjacent_sector} rather than failing the
     * archetype, matching how an unrecognised {@code metric} elsewhere in this vocabulary quietly
     * defaults instead.
     */
    private boolean exactTolerance()
    {
        return "exact".equalsIgnoreCase(this.tolerance);
    }

    private double zeroCreditDegrees(boolean exactTolerance)
    {
        return exactTolerance ? FULL_CREDIT_DEGREES : FULL_CREDIT_DEGREES + 90d;
    }

    private String diagnostic(int outOfRange, int facingAway, int judged)
    {
        if (outOfRange == 0 && facingAway == 0)
        {
            return "";
        }

        if (outOfRange >= judged)
        {
            return "no '" + this.to + "' are within " + this.maxDistance + " blocks of any '" + this.of + "'";
        }

        if (facingAway >= judged)
        {
            return "no '" + this.of + "' point towards '" + this.to + "'";
        }

        List<String> parts = new ArrayList<>();

        if (facingAway > 0)
        {
            parts.add(facingAway + " of " + judged + " '" + this.of + "' do not point towards '" + this.to + "'");
        }

        if (outOfRange > 0)
        {
            parts.add(outOfRange + " of " + judged + " '" + this.of + "' have no '" + this.to
                    + "' within " + this.maxDistance + " blocks");
        }

        return String.join("; ", parts);
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' faces '" + this.to + "'";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.maxDistance < 1)
        {
            errors.add("'max_distance' must be at least 1, got " + this.maxDistance);
        }

        return errors;
    }
}
