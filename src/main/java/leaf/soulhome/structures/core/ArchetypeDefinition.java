/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * A datapack-defined description of what some kind of room looks like.
 *
 * <p>Everything about an archetype lives in JSON so packmakers can add their own, and so balance
 * changes do not need a recompile:
 *
 * <pre>{@code
 * data/<namespace>/soulhome_archetypes/library.json
 * }</pre>
 *
 * <p>Note what this is <i>not</i>: a schematic. There is no block-for-block layout to copy. A
 * player builds whatever they think a library looks like, and the classifier decides how
 * library-ish it is. Two players' libraries can look nothing alike and both work.
 *
 * @param id           namespaced id, taken from the file path rather than the file body
 * @param displayName  translation key shown to players
 * @param regionTypes  which region shapes this archetype will consider - a farm accepts open air,
 *                     an armoury insists on a room
 * @param minVolume    smallest region that can qualify, so a broom cupboard is not a great hall
 * @param requirements hard gates; failing any one scores zero no matter what else is present
 * @param signals      weighted positive evidence
 * @param detractors   weighted negative evidence, used to tell near-miss archetypes apart
 * @param tiers        score thresholds, ascending
 * @param buffs        what the player gets, consumed by the buff registry
 * @param structures   weighted structural evidence - how the room is arranged, not just what it
 *                      holds. Never a gate: a {@link Form} cannot appear in {@link #requirements},
 *                      because there is nowhere on {@link Requirement} to put one. See the
 *                      structural considerations epic (#25).
 * @param bonds        weighted evidence about where this room sits relative to other rooms - a
 *                      library that connects to the enchanting room, a mine beneath the workshop.
 *                      Declared once, credited to both rooms, and never a gate either. See the
 *                      Soul Architecture epic (#140) and {@link Bond}.
 * @param aspects      what a room of this archetype can be <i>for</i> - a library as an archive or
 *                      as a scriptorium. Optional, and empty for most archetypes: an archetype
 *                      declaring none behaves exactly as it did before the Aspects epic (#171), and
 *                      that must stay the zero-cost path. An aspect is not evidence and is not
 *                      scored: it selects which buff the room's magnitude is paid into and changes
 *                      nothing else. See {@link Aspect}.
 */
public record ArchetypeDefinition(
        String id,
        String displayName,
        List<RegionType> regionTypes,
        int minVolume,
        List<Requirement> requirements,
        List<Signal> signals,
        List<Signal> detractors,
        List<Tier> tiers,
        List<BuffSpec> buffs,
        List<Form> structures,
        List<Bond> bonds,
        List<Aspect> aspects)
{
    /**
     * Default per-signal ceiling. Caps are the hard backstop against volume-stuffing; the
     * sublinear scoring curve in {@link ArchetypeClassifier} is the soft one.
     */
    public static final int DEFAULT_CAP = 64;

    /**
     * Stand-in id used between deserialisation and the loader supplying the real one from the file
     * path. Seeing this in a log means an archetype escaped {@link #withId}.
     */
    public static final String PLACEHOLDER_ID = "soulhome:unnamed";

    public ArchetypeDefinition
    {
        regionTypes = regionTypes == null || regionTypes.isEmpty()
                ? List.of(RegionType.ENCLOSED)
                : List.copyOf(regionTypes);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        signals = signals == null ? List.of() : List.copyOf(signals);
        detractors = detractors == null ? List.of() : List.copyOf(detractors);
        buffs = buffs == null ? List.of() : List.copyOf(buffs);
        structures = structures == null ? List.of() : List.copyOf(structures);
        bonds = bonds == null ? List.of() : List.copyOf(bonds);
        aspects = aspects == null ? List.of() : List.copyOf(aspects);

        // ascending, so tierFor can walk them and take the last one cleared
        List<Tier> sortedTiers = new ArrayList<>(tiers == null ? List.of() : tiers);
        sortedTiers.sort(Comparator.comparingDouble(Tier::minScore));
        tiers = List.copyOf(sortedTiers);
    }

    /** An archetype declaring no bonds - every archetype before #140, and most fixtures since. */
    public ArchetypeDefinition(
            String id,
            String displayName,
            List<RegionType> regionTypes,
            int minVolume,
            List<Requirement> requirements,
            List<Signal> signals,
            List<Signal> detractors,
            List<Tier> tiers,
            List<BuffSpec> buffs,
            List<Form> structures)
    {
        this(id, displayName, regionTypes, minVolume, requirements, signals, detractors, tiers, buffs, structures, List.of());
    }

    /** An archetype declaring no aspects - every archetype before #171, and most fixtures since. */
    public ArchetypeDefinition(
            String id,
            String displayName,
            List<RegionType> regionTypes,
            int minVolume,
            List<Requirement> requirements,
            List<Signal> signals,
            List<Signal> detractors,
            List<Tier> tiers,
            List<BuffSpec> buffs,
            List<Form> structures,
            List<Bond> bonds)
    {
        this(id, displayName, regionTypes, minVolume, requirements, signals, detractors, tiers, buffs,
                structures, bonds, List.of());
    }

    /** The loader supplies the id from the file path; the file body does not get to name itself. */
    public ArchetypeDefinition withId(String newId)
    {
        return new ArchetypeDefinition(
                newId, this.displayName, this.regionTypes, this.minVolume,
                this.requirements, this.signals, this.detractors, this.tiers, this.buffs,
                this.structures, this.bonds, this.aspects);
    }

    /**
     * The aspect with this id, or null - including for a null id, which is what an archetype with no
     * aspects, and every room awarded before this epic, carries.
     */
    public Aspect aspect(String aspectId)
    {
        if (aspectId == null || aspectId.isBlank())
        {
            return null;
        }

        for (Aspect aspect : this.aspects)
        {
            if (aspect.id().equals(aspectId))
            {
                return aspect;
            }
        }

        return null;
    }

    /** The aspect that pays this archetype's own {@link #buffs}, or null when none is declared. */
    public Aspect defaultAspect()
    {
        for (Aspect aspect : this.aspects)
        {
            if (aspect.isDefault())
            {
                return aspect;
            }
        }

        return null;
    }

    /**
     * What a room that took this aspect pays. The archetype's own {@link #buffs} unless a non-default
     * aspect declared its own - which is the whole of what an aspect does (#171 rule 3).
     *
     * <p>Falls back to the archetype's own payout for an aspect id it does not know, which is what a
     * save written against a datapack that has since dropped the aspect hands back. That is the same
     * answer the switch being off gives, and both are the answer a player expects: the room keeps
     * granting what its kind of room grants.
     */
    public List<BuffSpec> buffsFor(String aspectId)
    {
        final Aspect aspect = aspect(aspectId);

        return aspect == null || !aspect.paysItsOwn() ? this.buffs : aspect.buffs();
    }

    /**
     * Every form this archetype can evaluate, its aspects' included - what
     * {@code ArchetypeSignals} has to index geometry for. An aspect form is still credited only to
     * its aspect; what it shares with the archetype's own forms is the cell index it reads, not the
     * score it feeds.
     */
    public List<Form> allForms()
    {
        if (this.aspects.isEmpty())
        {
            return this.structures;
        }

        List<Form> forms = new ArrayList<>(this.structures);

        for (Aspect aspect : this.aspects)
        {
            forms.addAll(aspect.structures());
        }

        return List.copyOf(forms);
    }

    public boolean accepts(RegionType type)
    {
        return this.regionTypes.contains(type);
    }

    /** The highest tier this score clears, or 0 for "did not qualify". */
    public int tierFor(double score)
    {
        int result = 0;

        for (Tier tier : this.tiers)
        {
            if (score >= tier.minScore())
            {
                result = Math.max(result, tier.tier());
            }
        }

        return result;
    }

    /**
     * Score still needed to reach the next tier up, or empty when already at the top. Feedback
     * needs this to say "42 more points to tier 2" rather than just "tier 1".
     */
    public OptionalDouble scoreToNextTier(double score)
    {
        for (Tier tier : this.tiers)
        {
            if (score < tier.minScore())
            {
                return OptionalDouble.of(tier.minScore() - score);
            }
        }

        return OptionalDouble.empty();
    }

    /**
     * Problems that should be logged loudly but do not justify failing the whole datapack. An
     * archetype reported here is skipped; the rest of the pack still loads.
     */
    public List<String> validationErrors()
    {
        List<String> errors = new ArrayList<>();

        if (this.displayName == null || this.displayName.isBlank())
        {
            errors.add("'display_name' is missing or blank");
        }

        if (this.minVolume < 1)
        {
            errors.add("'min_volume' must be at least 1, got " + this.minVolume);
        }

        if (this.signals.isEmpty())
        {
            errors.add("no 'signals' defined, so this archetype can never score above zero");
        }

        if (this.tiers.isEmpty())
        {
            errors.add("no 'tiers' defined, so this archetype can never be awarded");
        }

        for (Tier tier : this.tiers)
        {
            if (tier.tier() < 1)
            {
                errors.add("tier numbers must be at least 1, got " + tier.tier());
            }
        }

        for (int i = 0; i < this.requirements.size(); i++)
        {
            Requirement requirement = this.requirements.get(i);

            for (String error : requirement.match().validationErrors())
            {
                errors.add("requirements[" + i + "]: " + error);
            }

            if (requirement.minCount() < 1)
            {
                errors.add("requirements[" + i + "]: 'min_count' must be at least 1");
            }

            if (requirement.minVolumeFraction() < 0d || requirement.minVolumeFraction() > 1d)
            {
                errors.add("requirements[" + i + "]: 'min_volume_fraction' must be between 0 and 1, got "
                        + requirement.minVolumeFraction());
            }
        }

        collectSignalErrors(errors, "signals", this.signals, true);
        collectSignalErrors(errors, "detractors", this.detractors, false);

        for (int i = 0; i < this.buffs.size(); i++)
        {
            BuffSpec buff = this.buffs.get(i);

            if (buff.type() == null || buff.type().isBlank())
            {
                errors.add("buffs[" + i + "]: 'type' is missing or blank");
            }
        }

        Set<String> formNames = new HashSet<>();

        for (int i = 0; i < this.structures.size(); i++)
        {
            Form form = this.structures.get(i);
            final String where = "structures[" + i + "]";

            for (String error : form.validationErrors())
            {
                errors.add(where + ": " + error);
            }

            if (form.name() != null && !form.name().isBlank() && !formNames.add(form.name()))
            {
                errors.add(where + ": duplicate form name '" + form.name() + "'");
            }
        }

        for (int i = 0; i < this.bonds.size(); i++)
        {
            for (String error : this.bonds.get(i).validationErrors())
            {
                errors.add("bonds[" + i + "]: " + error);
            }
        }

        collectAspectErrors(errors);

        return errors;
    }

    /**
     * The aspect rules from #172, checked here rather than left to authors to remember: exactly one
     * default, ids unique within the archetype, and every block an aspect reads already scored by
     * the room (#171 rule 2, see {@link Aspect}).
     *
     * <p>A malformed aspect fails the whole archetype rather than being dropped on its own. An
     * archetype missing one of its aspects is an archetype that quietly pays the wrong buff, and a
     * packmaker is far better served by a room that does not load and says why.
     */
    private void collectAspectErrors(List<String> errors)
    {
        if (this.aspects.isEmpty())
        {
            return;
        }

        Set<String> ids = new HashSet<>();
        int defaults = 0;

        for (int i = 0; i < this.aspects.size(); i++)
        {
            final Aspect aspect = this.aspects.get(i);

            // named as well as numbered: "aspects[1] 'scriptorium': '#soulhome:writing' is not one
            // of this archetype's own signals" is a line an author can act on without counting
            // entries in their own file (#172)
            final String where = aspect.id() == null || aspect.id().isBlank()
                    ? "aspects[" + i + "]: "
                    : "aspects[" + i + "] '" + aspect.id() + "': ";

            for (String error : aspect.validationErrors(this.signals))
            {
                errors.add(where + error);
            }

            if (aspect.id() != null && !aspect.id().isBlank() && !ids.add(aspect.id()))
            {
                errors.add(where + "duplicate aspect id '" + aspect.id() + "'");
            }

            if (aspect.isDefault())
            {
                defaults++;
            }
        }

        if (defaults == 0)
        {
            errors.add("aspects: none is marked 'default', so there is nothing to pay the"
                    + " archetype's own 'buffs' and no anchor for a room that leans nowhere");
        }
        else if (defaults > 1)
        {
            errors.add("aspects: " + defaults + " are marked 'default', and exactly one may be");
        }

        if (this.aspects.size() == 1)
        {
            errors.add("aspects: only one is declared, so there is nothing to choose between -"
                    + " an archetype with a single payout should declare no aspects at all");
        }
    }

    /**
     * Problems worth a packmaker's attention that do not reject the archetype - a form that
     * relates only one element, say. Logged, never enforced.
     */
    public List<String> validationWarnings()
    {
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < this.structures.size(); i++)
        {
            for (String warning : this.structures.get(i).validationWarnings())
            {
                warnings.add("structures[" + i + "]: " + warning);
            }
        }

        for (int i = 0; i < this.aspects.size(); i++)
        {
            for (String warning : this.aspects.get(i).validationWarnings())
            {
                warnings.add("aspects[" + i + "]: " + warning);
            }
        }

        return warnings;
    }

    private static void collectSignalErrors(List<String> errors, String field, List<Signal> signals, boolean positive)
    {
        for (int i = 0; i < signals.size(); i++)
        {
            Signal signal = signals.get(i);
            final String where = field + "[" + i + "]: ";

            for (String error : signal.match().validationErrors())
            {
                errors.add(where + error);
            }

            if (signal.cap() < 1)
            {
                errors.add(where + "'cap' must be at least 1, got " + signal.cap());
            }

            if (positive && signal.weight() <= 0)
            {
                errors.add(where + "'weight' must be positive for a signal, got " + signal.weight()
                        + " (negative evidence belongs in 'detractors')");
            }

            if (!positive && signal.weight() >= 0)
            {
                errors.add(where + "'weight' must be negative for a detractor, got " + signal.weight());
            }
        }
    }

    /**
     * A hard gate. Fail one and the region scores zero for this archetype regardless of anything
     * else, which is what stops a farm with one lonely bookshelf from registering as a library.
     *
     * @param minVolumeFraction beyond a flat {@code minCount}, some rooms are only themselves when
     *                          they leave almost nothing else standing - an aquarium is not one
     *                          with a dry corner in it. {@code 0} (the default) means this plays no
     *                          part and only {@code minCount} gates; above {@code 0} the effective
     *                          threshold is {@code max(minCount, ceil(minVolumeFraction * volume))},
     *                          checked against the region's own {@link SoulRegion#volume()} - which
     *                          already excludes both the shell and anything solid enough to stand
     *                          in the room, since neither one is ever part of an enclosed region's
     *                          interior fill in the first place. See {@link ArchetypeClassifier}.
     */
    public record Requirement(BlockMatcher match, int minCount, double minVolumeFraction)
    {
        public Requirement(BlockMatcher match, int minCount)
        {
            this(match, minCount, 0d);
        }
    }

    /**
     * Weighted evidence for (or, with a negative weight, against) an archetype.
     *
     * @param role a grouping label such as {@code core}, {@code comfort} or {@code light}. Rooms
     *             that hit several distinct roles score better than rooms that hit one role
     *             harder - this is the main lever that rewards building a room over stacking a
     *             block.
     * @param cap  the count past which more of this block stops helping at all
     * @param seed whether, for an archetype that accepts open regions, the scanner may gather an
     *             open-air region around this block - seed a cluster on it, and cross it to
     *             reach the next. {@code true} unless the datapack says otherwise. Off for
     *             evidence a room is glad of but which is also simply what the ground is made
     *             of: the masonry of a storm spire is stone, and stone is what a starter island
     *             is, so the whole island used to gather into one region around it (#139). A
     *             non-seeding signal still counts wherever a region takes it in - beside a
     *             build's own blocks, or closed around by them - it just never starts or
     *             extends one. Meaningless for a detractor, and for an archetype that only
     *             accepts enclosed regions.
     */
    public record Signal(BlockMatcher match, double weight, String role, int cap, boolean seed)
    {
        public static final String DEFAULT_ROLE = "general";

        public Signal
        {
            role = role == null || role.isBlank() ? DEFAULT_ROLE : role;
        }

        /** The common case: a signal the scanner may gather an open-air region around. */
        public Signal(BlockMatcher match, double weight, String role, int cap)
        {
            this(match, weight, role, cap, true);
        }
    }

    /** A score threshold and the tier it awards. */
    public record Tier(double minScore, int tier)
    {
    }

    /**
     * What the archetype grants. Consumed by the buff registry rather than by the classifier, so
     * the shape here is deliberately inert.
     *
     * @param type    buff id, e.g. {@code soulhome:xp_gain}
     * @param perTier magnitude added per tier. No longer read by {@link #magnitudeAt}, which
     *                derives magnitude from the score directly - kept as a datapack field rather
     *                than removed, since dropping it would be a breaking format change on its own.
     * @param max     ceiling on the total magnitude from this archetype
     */
    public record BuffSpec(String type, double perTier, double max)
    {
        /**
         * Magnitude for a room that scored this well, ramped continuously across the archetype's
         * own tier ladder rather than jumping at each tier boundary - see {@code BuffSettings} for
         * what {@code entryFraction} and {@code rampExponent} each shape.
         *
         * <pre>{@code
         * entry     = tiers[0].minScore
         * top       = tiers[last].minScore
         * t         = clamp((score - entry) / (top - entry), 0, 1)
         * magnitude = max * (entryFraction + (1 - entryFraction) * pow(t, rampExponent))
         * }</pre>
         *
         * <p>Below {@code entry} this is 0 - a room that has not earned its archetype's first tier
         * grants nothing. At or above {@code top} this is exactly {@link #max}, at every exponent.
         *
         * @param score     the room's raw classifier score, not its tier
         * @param archetype supplies the tier ladder {@code entry} and {@code top} are read from
         */
        public double magnitudeAt(double score, ArchetypeDefinition archetype, BuffSettings settings)
        {
            List<Tier> tiers = archetype.tiers();

            if (tiers.isEmpty())
            {
                return 0d;
            }

            final double entry = tiers.get(0).minScore();

            if (score < entry)
            {
                return 0d;
            }

            final double top = tiers.get(tiers.size() - 1).minScore();
            final double t = top <= entry ? 1d : Math.max(0d, Math.min(1d, (score - entry) / (top - entry)));
            final double entryFraction = settings.entryFraction();
            final double fraction = entryFraction + (1d - entryFraction) * Math.pow(t, settings.rampExponent());

            return this.max * fraction;
        }
    }
}
