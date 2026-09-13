/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One of the things a room of this archetype can be <i>for</i> - the Aspects epic (#171).
 *
 * <p>An archetype says what a room <i>is</i>. An aspect says what that room is for: a library heavy
 * on shelving and storage is an archive and keeps its experience gain, one heavy on lecterns laid
 * out to write at is a scriptorium and grants enchantment power instead. Same archetype, same tier,
 * same score, different payout - chosen by what the player actually built.
 *
 * <h2>The aspect selects the buff. It never scales it.</h2>
 *
 * This is rule 3 of the epic and the one that would be broken by accident, because it runs against
 * how the rest of the mod works. {@link #leans} and {@link #structures} exist only to be compared
 * against the other aspects' - see {@link AspectSelector}. Their total never reaches a magnitude,
 * never scales one, and never appears in any product with one. A room of magnitude 5 whose aspects
 * stand at 0.9 and 0.7 pays exactly what a room of magnitude 5 whose aspects stand at 0.999 and
 * 0.99 pays.
 *
 * <p>The failure that prevents is the one the classifier already has by design one level up: a
 * region holding two archetypes' blocks scores as neither, because mixed evidence dilutes. That is
 * right for deciding what a room is and would be badly wrong for deciding what it is for. A player
 * who builds a library with both a fine archive and a fine scriptorium in it has built a better
 * library, and must not be quietly charged for the aspect they did not win.
 *
 * <h2>Every block an aspect reads is a block the room already scores</h2>
 *
 * Rule 2, enforced at load by {@link #validationErrors}. Without it a player could raise an aspect
 * with material that does nothing for the room's tier, and the two systems would start pulling
 * against each other - a choice between a better room and the buff you wanted. With it, every block
 * placed does both jobs at once and the aspect is a consequence of how the room was built rather
 * than a second budget to spend.
 *
 * <p>{@link #structures} are the one thing an aspect may ask for that the archetype does not:
 * <i>the arrangement</i>, never the blocks. A library is expected to hold a lectern or two; it is
 * not expected to hold them in ranked rows, and a scriptorium is. So an aspect form's elements are
 * held to the same rule as a lean - every one of them must be something the room already scores -
 * while the shape they are asked to stand in is the aspect's own business and is credited nowhere
 * else. An aspect form never adds to the room's arrangement credit.
 *
 * @param id           short, lowercase, unique within its archetype - two archetypes may both have
 *                     an {@code archive}, so ids are never global
 * @param displayName  translation key shown to players; never an id in prose (#103)
 * @param isDefault    whether this aspect pays the archetype's own top-level {@code buffs}. Exactly
 *                     one aspect is the default, and it is what every room granted before this epic,
 *                     which is how no build loses anything.
 * @param leans        what tips a room toward this aspect. A comparison input only.
 * @param structures   arrangements that tip a room toward this aspect, likewise a comparison input
 *                     only - see above
 * @param buffs        what this aspect pays instead of the archetype's top-level {@code buffs}.
 *                     Empty on the default, which pays those; empty on a non-default means it pays
 *                     them too, which is a datapack oddity rather than an error.
 */
public record Aspect(
        String id,
        String displayName,
        boolean isDefault,
        List<Lean> leans,
        List<Form> structures,
        List<ArchetypeDefinition.BuffSpec> buffs)
{
    public Aspect
    {
        leans = leans == null ? List.of() : List.copyOf(leans);
        structures = structures == null ? List.of() : List.copyOf(structures);
        buffs = buffs == null ? List.of() : List.copyOf(buffs);
    }

    /** Whether this aspect's payout differs from the archetype's own. */
    public boolean paysItsOwn()
    {
        return !this.buffs.isEmpty();
    }

    /**
     * Problems that should reject the whole archetype, logged and skipped like any other malformed
     * element - never a reload failure.
     *
     * @param signals the archetype's own signals, which every lean and every form element must be
     *                covered by. See {@link #covers}.
     */
    public List<String> validationErrors(List<ArchetypeDefinition.Signal> signals)
    {
        List<String> errors = new ArrayList<>();

        if (this.id == null || this.id.isBlank())
        {
            errors.add("'id' is missing or blank");
        }

        if (this.displayName == null || this.displayName.isBlank())
        {
            errors.add("'display_name' is missing or blank");
        }

        if (this.leans.isEmpty() && this.structures.isEmpty())
        {
            // including the default: an aspect with nothing to say for itself cannot take part in a
            // comparison, and a default that never competes would hand every room to the first
            // alternative that scored anything at all
            errors.add("declares neither 'leans' nor 'structures', so nothing could ever tip a room toward it");
        }

        final Covered covered = Covered.of(signals);

        for (int i = 0; i < this.leans.size(); i++)
        {
            final Lean lean = this.leans.get(i);
            final String where = "leans[" + i + "]: ";

            for (String error : lean.match().validationErrors())
            {
                errors.add(where + error);
            }

            if (lean.weight() <= 0d)
            {
                errors.add(where + "'weight' must be positive, got " + lean.weight());
            }

            if (lean.cap() < 1)
            {
                errors.add(where + "'cap' must be at least 1, got " + lean.cap());
            }

            for (String uncovered : covered.uncovered(lean.match()))
            {
                errors.add(where + uncovered);
            }
        }

        Set<String> formNames = new HashSet<>();

        for (int i = 0; i < this.structures.size(); i++)
        {
            final Form form = this.structures.get(i);
            final String where = "structures[" + i + "]: ";

            for (String error : form.validationErrors())
            {
                errors.add(where + error);
            }

            if (form.name() != null && !form.name().isBlank() && !formNames.add(form.name()))
            {
                errors.add(where + "duplicate form name '" + form.name() + "'");
            }

            for (BlockMatcher element : form.elements().values())
            {
                for (String uncovered : covered.uncovered(element))
                {
                    errors.add(where + uncovered);
                }
            }
        }

        for (int i = 0; i < this.buffs.size(); i++)
        {
            final ArchetypeDefinition.BuffSpec buff = this.buffs.get(i);

            if (buff.type() == null || buff.type().isBlank())
            {
                errors.add("buffs[" + i + "]: 'type' is missing or blank");
            }
        }

        return errors;
    }

    /** Worth a packmaker's attention, never enforced. */
    public List<String> validationWarnings()
    {
        List<String> warnings = new ArrayList<>();

        if (!this.isDefault && this.buffs.isEmpty())
        {
            warnings.add("is not the default and declares no 'buffs' of its own, so taking it pays"
                    + " exactly what the default pays and a player can never tell which they have");
        }

        for (Form form : this.structures)
        {
            warnings.addAll(form.validationWarnings());
        }

        return warnings;
    }

    /**
     * Which of an archetype's signals cover which ids, judged on what the JSON declares rather than
     * by consulting a registry - the loader must stay registry-free so that an id from an absent mod
     * still loads, exactly as {@link BlockMatcher} never touches one.
     *
     * <p>The check is deliberately conservative: a block a signal only reaches <i>through a tag</i>
     * is not accepted, because knowing whether the tag holds it would need the registry. The fix for
     * an author is the one rule 1 of #174 asks for anyway - name the block in the archetype's own
     * signals too, and describe the widening as the buff to the room that it is.
     */
    record Covered(Set<String> blocks, Set<String> tags)
    {
        static Covered of(List<ArchetypeDefinition.Signal> signals)
        {
            Set<String> blocks = new HashSet<>();
            Set<String> tags = new HashSet<>();

            for (ArchetypeDefinition.Signal signal : signals == null ? List.<ArchetypeDefinition.Signal>of() : signals)
            {
                blocks.addAll(signal.match().blocks());
                tags.addAll(signal.match().tags());
            }

            return new Covered(blocks, tags);
        }

        /** One message per entry the archetype does not score - named, so the author need not guess. */
        List<String> uncovered(BlockMatcher matcher)
        {
            List<String> problems = new ArrayList<>();

            for (String block : matcher.blocks())
            {
                if (!this.blocks.contains(block))
                {
                    problems.add("'" + block + "' is not one of this archetype's own signals, so it"
                            + " would count toward the aspect without counting toward the room");
                }
            }

            for (String tag : matcher.tags())
            {
                if (!this.tags.contains(tag))
                {
                    problems.add("'#" + tag + "' is not one of this archetype's own signals, so it"
                            + " would count toward the aspect without counting toward the room");
                }
            }

            return problems;
        }
    }

    /**
     * One piece of evidence that a room is meant for this aspect.
     *
     * <p>Counts go through the same {@code sqrt} curve signals do, and take the same kind of cap,
     * for the same reason: a comparison decided by sheer mass would make the way to get the aspect
     * you want a wall of one block rather than a room built for it. What a lean does not carry is a
     * {@code role} - roles feed the diversity multiplier, and a lean touches no multiplier at all.
     *
     * @param cap the count past which more of this block stops tipping the room any further
     */
    public record Lean(BlockMatcher match, double weight, int cap)
    {
        public Lean(BlockMatcher match, double weight)
        {
            this(match, weight, ArchetypeDefinition.DEFAULT_CAP);
        }
    }
}
