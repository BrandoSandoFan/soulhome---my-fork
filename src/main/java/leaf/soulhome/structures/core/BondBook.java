/*
 * File created ~ 12 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every bond the loaded archetypes declare, resolved so that each room can ask "what am I bonded
 * to, and how" from its own side.
 *
 * <h2>Declared once, credited twice</h2>
 *
 * A bond is symmetric in effect: if {@code library} declares a bond with {@code enchanting_room},
 * both rooms are credited, and {@code enchanting_room.json} need not declare the mirror. So each
 * declaration becomes two {@link Resolved} entries, one per side, with a directional relation
 * turned round for the far side - {@code mine beneath workshop} is {@code workshop above mine}
 * from the workshop.
 *
 * <p>Declaring it on both sides anyway must not double the credit. Two declarations describe the
 * same bond when they name the same pair of archetypes under the same relation, mirrors counted
 * as the same relation. The declaration on the archetype whose id sorts first is kept and the
 * other is dropped and reported through {@link #duplicates()} for the loader to log - kept rather
 * than merged, because merging two different weights would be inventing a number neither
 * packmaker wrote, and deterministic rather than first-loaded so two servers with the same
 * datapacks agree.
 *
 * <p>A bond naming an archetype that is not loaded is kept as it is: it will never find a partner
 * room and never match, exactly as {@link BlockMatcher} treats a block from an absent mod.
 * Resolving it away would lose the very thing {@code /soulhome analyse} should be able to say -
 * "this room would bond with a Ritual Chamber, which is not installed".
 */
public final class BondBook
{
    public static final BondBook EMPTY = new BondBook(List.of(), BondRelationRegistry.BUILTIN);

    private final Map<String, List<Resolved>> bySide;
    private final List<String> duplicates;
    private final List<String> unknownRelations;

    private BondBook(Collection<ArchetypeDefinition> archetypes, BondRelationRegistry registry)
    {
        List<ArchetypeDefinition> sorted = new ArrayList<>(archetypes);
        sorted.sort(Comparator.comparing(ArchetypeDefinition::id));

        Map<String, Declared> byKey = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (ArchetypeDefinition archetype : sorted)
        {
            for (Bond bond : archetype.bonds())
            {
                Optional<BondRelation> relation = registry.get(bond.relation());

                if (relation.isEmpty())
                {
                    unknown.add(archetype.id() + " bonds with " + bond.with() + " by '" + bond.relation()
                            + "', which is not a known relation; skipped");
                    continue;
                }

                final String key = keyOf(archetype.id(), bond.with(), relation.get());
                Declared existing = byKey.get(key);

                if (existing != null)
                {
                    duplicates.add(archetype.id() + " declares a bond with " + bond.with() + " by '"
                            + bond.relation() + "' that " + existing.on() + " already declares from its side; "
                            + existing.on() + "'s is kept and this one is ignored");
                    continue;
                }

                byKey.put(key, new Declared(archetype.id(), bond, relation.get()));
            }
        }

        Map<String, List<Resolved>> bySide = new LinkedHashMap<>();

        for (Declared declared : byKey.values())
        {
            final Bond bond = declared.bond();
            final String self = declared.on();
            final String other = bond.with();

            bySide.computeIfAbsent(self, ignored -> new ArrayList<>()).add(new Resolved(
                    self, other, declared.relation(), bond.params(), bond.weight(), bond.role(), self));

            // the far side, unless the bond is with itself - two libraries side by side share one
            // entry, read from either
            if (!other.equals(self))
            {
                BondRelation mirrored = registry.get(declared.relation().mirror()).orElse(declared.relation());

                bySide.computeIfAbsent(other, ignored -> new ArrayList<>()).add(new Resolved(
                        other, self, mirrored, bond.params(), bond.weight(), bond.role(), self));
            }
        }

        // a stable order per side, so equal bond credit ties the same way every scan
        for (List<Resolved> resolved : bySide.values())
        {
            resolved.sort(Comparator
                    .comparing(Resolved::other)
                    .thenComparing(resolved1 -> resolved1.relation().id()));
        }

        Map<String, List<Resolved>> frozen = new LinkedHashMap<>();
        bySide.forEach((id, list) -> frozen.put(id, List.copyOf(list)));

        this.bySide = Map.copyOf(frozen);
        this.duplicates = List.copyOf(duplicates);
        this.unknownRelations = List.copyOf(unknown);
    }

    public static BondBook of(Collection<ArchetypeDefinition> archetypes, BondRelationRegistry registry)
    {
        return new BondBook(archetypes, registry);
    }

    public static BondBook of(Collection<ArchetypeDefinition> archetypes)
    {
        return of(archetypes, BondRelationRegistry.BUILTIN);
    }

    /**
     * One key per bond, whichever side declared it: the pair in id order, plus the relation as
     * seen from the first of the pair - so {@code mine beneath workshop} and
     * {@code workshop above mine} key identically.
     */
    private static String keyOf(String declaredOn, String with, BondRelation relation)
    {
        if (declaredOn.compareTo(with) <= 0)
        {
            return declaredOn + "|" + with + "|" + relation.id();
        }

        return with + "|" + declaredOn + "|" + relation.mirror();
    }

    /** Every bond this archetype takes part in, seen from its side. Empty for one that declares none and is named by none. */
    public List<Resolved> bondsOf(String archetypeId)
    {
        return this.bySide.getOrDefault(archetypeId, List.of());
    }

    public boolean isEmpty()
    {
        return this.bySide.isEmpty();
    }

    /** Declarations dropped as the mirror of one already kept - for the loader to log. */
    public List<String> duplicates()
    {
        return this.duplicates;
    }

    /** Declarations naming a relation nothing registered - for the loader to log. */
    public List<String> unknownRelations()
    {
        return this.unknownRelations;
    }

    private record Declared(String on, Bond bond, BondRelation relation)
    {
    }

    /**
     * A bond as one of its two rooms sees it.
     *
     * @param self       the archetype asking
     * @param other      the archetype it is bonded to
     * @param relation   the relation from {@code self}'s side - a mirror of what was declared, when
     *                   it was declared on the other side and is directional
     * @param declaredOn which side's JSON the bond came from, for the report
     */
    public record Resolved(
            String self,
            String other,
            BondRelation relation,
            ClauseParams params,
            double weight,
            String role,
            String declaredOn)
    {
        public boolean isDiscord()
        {
            return this.weight < 0d;
        }
    }
}
