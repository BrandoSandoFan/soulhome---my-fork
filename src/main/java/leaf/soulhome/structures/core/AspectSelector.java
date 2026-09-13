/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks which aspect a room takes - the Aspects epic (#171), rule 3.
 *
 * <h2>A comparison, never a weighting</h2>
 *
 * <pre>{@code
 * magnitude = (everything the mod already does)       <- unchanged, computed elsewhere
 * aspect    = argmax over aspects of support(aspect)  <- this class
 * payout    = aspect's buff, at that same magnitude   <- BuffCalculator
 * }</pre>
 *
 * <p>{@code support} exists only inside the {@code argmax}. Nothing this class returns is ever
 * multiplied by, added to, or compared against a score, and {@link AspectSelection} carries no
 * number a magnitude could be derived from. Two rooms of magnitude 5, one whose aspects stand at
 * 0.9 and 0.7 and one whose stand at 0.999 and 0.99, both take the leader and both pay it at 5.
 * Anything that makes the gap between first and second matter to the payout has broken the rule
 * this class exists to keep.
 *
 * <h2>Why the default holds unless beaten by a margin, and why nothing is saved</h2>
 *
 * A bare {@code argmax} over two near-equal aspects flips the room's buff every time a player moves
 * one block, and a buff that changes on its own is indistinguishable from a bug. The obvious cure is
 * hysteresis: remember the aspect a room had and make a challenger beat it. That was rejected, and
 * the reason is worth keeping: remembering it means saving it, and #176 requires that aspect
 * selection be derived on every scan and never trusted from a save, so that turning the feature off
 * cannot leave a soulhome granting a buff the config says it should not have.
 *
 * <p>So the default aspect is the anchor instead of the previous one. A challenger takes the room
 * only by leading the default by {@link ScoringSettings#aspectMargin}; ties and near-ties go to the
 * default, which is the aspect that pays what the room paid before aspects existed. That is
 * stateless, deterministic, and it makes "a player who updates and changes nothing notices nothing"
 * a property of the mechanism rather than a hope about how the leans were tuned.
 *
 * <p>Two challengers close to each other can still swap between themselves. That is accepted: the
 * order is fixed by support and then by the archetype's own declaration order, so it is at least
 * deterministic - the same build yields the same aspect on every scan, on every machine - and the
 * report names both, which is the difference between a system a player can read and one they cannot.
 */
public final class AspectSelector
{
    private AspectSelector()
    {
    }

    /**
     * The aspect this region takes of this archetype, or null when the archetype declares none -
     * which is the common case and must stay the zero-cost path.
     *
     * @param clauseMemo shared with the caller's form evaluation, so an aspect form naming a clause
     *                   the archetype's own forms already named is evaluated once
     */
    public static AspectSelection select(
            SoulRegion region,
            ArchetypeDefinition archetype,
            ScoringSettings settings,
            Map<FormClauseEvaluator.MemoKey, FormResult> clauseMemo)
    {
        final List<Aspect> aspects = archetype.aspects();

        if (aspects.isEmpty())
        {
            return null;
        }

        final BlockCounts blocks = region.allBlocks();
        List<Candidate> candidates = new ArrayList<>(aspects.size());

        for (int declared = 0; declared < aspects.size(); declared++)
        {
            final Aspect aspect = aspects.get(declared);
            candidates.add(new Candidate(aspect, declared, supportOf(aspect, blocks, region, clauseMemo)));
        }

        // best support first, then the archetype's own declaration order - never map iteration
        // order and never scan order, or a room's buff would depend on how it was looked at
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingDouble(Candidate::support).reversed()
                .thenComparingInt(Candidate::declared));

        final Candidate fallback = defaultOf(candidates);
        final Candidate leader = ordered.get(0);

        Candidate taken = leader;
        boolean heldByDefault = false;

        if (fallback != null && !leader.isDefault() && leader.support() < fallback.support() * settings.aspectMargin())
        {
            taken = fallback;
            heldByDefault = true;
        }

        List<AspectSelection.Support> supports = new ArrayList<>(ordered.size());

        for (Candidate candidate : ordered)
        {
            supports.add(new AspectSelection.Support(
                    candidate.aspect().id(),
                    candidate.aspect().displayName(),
                    candidate.isDefault(),
                    candidate.support()));
        }

        final Candidate runnerUp = runnerUpOf(ordered, taken);

        return new AspectSelection(
                taken.aspect().id(),
                taken.aspect().displayName(),
                taken.isDefault(),
                heldByDefault,
                settings.aspectMargin(),
                supports,
                tipFor(runnerUp, taken, settings.aspectMargin(), blocks));
    }

    /**
     * What the room's contents say for one aspect: its leans on the same {@code sqrt} curve and the
     * same style of cap the classifier gives a signal, plus its own arrangements.
     *
     * <p>An aspect form is credited here and nowhere else. A library is expected to hold a lectern;
     * it is not expected to hold them in ranked rows, so the rows say which kind of library this is
     * without saying that it is more of a library - which is the whole of the distinction the epic
     * was raised to draw.
     */
    private static double supportOf(
            Aspect aspect,
            BlockCounts blocks,
            SoulRegion region,
            Map<FormClauseEvaluator.MemoKey, FormResult> clauseMemo)
    {
        double support = 0d;

        for (Aspect.Lean lean : aspect.leans())
        {
            final double counted = Math.min(blocks.credit(lean.match()), lean.cap());
            support += lean.weight() * ArchetypeClassifier.curve(counted);
        }

        for (Form form : aspect.structures())
        {
            final ArchetypeScore.ClauseEvaluation evaluated =
                    FormClauseEvaluator.evaluate(form.root(), region.geometry(), form.elements(), clauseMemo);

            support += form.weight() * evaluated.confidence();
        }

        return support;
    }

    private static Candidate defaultOf(List<Candidate> candidates)
    {
        for (Candidate candidate : candidates)
        {
            if (candidate.isDefault())
            {
                return candidate;
            }
        }

        // an archetype with no default is malformed and never loads; a hand-built definition in a
        // test still gets a sensible answer rather than an exception
        return null;
    }

    private static Candidate runnerUpOf(List<Candidate> ordered, Candidate taken)
    {
        for (Candidate candidate : ordered)
        {
            if (candidate != taken)
            {
                return candidate;
            }
        }

        return null;
    }

    /**
     * What would hand the room to the runner-up, in blocks - the actionable half, exactly as it is
     * for a near-miss archetype.
     *
     * <p>The threshold depends on which side of the margin the two are on: a challenger has to
     * clear the default by it, the default only has to come back inside it, and two challengers
     * face each other straight. The lean chosen is the runner-up's heaviest that is not already at
     * its cap, since adding more of a capped one would change nothing and saying so would be worse
     * than saying nothing.
     */
    private static AspectSelection.Tip tipFor(Candidate runnerUp, Candidate taken, double margin, BlockCounts blocks)
    {
        if (runnerUp == null)
        {
            return null;
        }

        final double threshold;

        if (taken.isDefault())
        {
            threshold = taken.support() * margin;
        }
        else if (runnerUp.isDefault())
        {
            threshold = margin <= 0d ? taken.support() : taken.support() / margin;
        }
        else
        {
            threshold = taken.support();
        }

        final double deficit = threshold - runnerUp.support();

        if (deficit <= 0d)
        {
            // the runner-up is already level: the declaration order broke the tie, and no number of
            // blocks is the answer to that
            return null;
        }

        Aspect.Lean best = null;
        double bestCount = 0d;

        for (Aspect.Lean lean : runnerUp.aspect().leans())
        {
            final double count = blocks.credit(lean.match());

            if (count >= lean.cap())
            {
                continue;
            }

            if (best == null || lean.weight() > best.weight())
            {
                best = lean;
                bestCount = count;
            }
        }

        if (best == null)
        {
            // only an arrangement separates them, and "build it in rows" is not a number of blocks
            return null;
        }

        // invert the curve: weight * (sqrt(count + n) - sqrt(count)) >= deficit
        final double target = deficit / best.weight() + Math.sqrt(bestCount);
        final int needed = (int) Math.ceil(target * target - bestCount);

        return new AspectSelection.Tip(
                runnerUp.aspect().id(),
                runnerUp.aspect().displayName(),
                best.match().describe(),
                Math.max(1, needed));
    }

    /** Convenience for a caller with no memo of its own to share - a test, or a one-off report. */
    public static AspectSelection select(SoulRegion region, ArchetypeDefinition archetype, ScoringSettings settings)
    {
        return select(region, archetype, settings, new HashMap<>());
    }

    private record Candidate(Aspect aspect, int declared, double support)
    {
        boolean isDefault()
        {
            return this.aspect.isDefault();
        }
    }
}
