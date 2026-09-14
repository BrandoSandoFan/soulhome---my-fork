/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which of a soulhome's rooms it is actually carrying (the Attunement epic, #151), and the rules
 * that decide it. Minecraft-free, like everything else in this package, so the hard half - room
 * identity across scans - is testable without booting the game.
 *
 * <h2>What is attuned, and why it is not any of the obvious things</h2>
 *
 * #152's hard part. A binding has to survive the player editing the room it names, and the three
 * candidates fail in different ways:
 *
 * <ul>
 *   <li><b>Not the archetype.</b> A player with two libraries could not choose which one, and the
 *       whole point of the epic is that they choose.</li>
 *   <li><b>Not the {@link SoulRegion}.</b> Its {@code identityHash} is a digest of shape, contents
 *       and arrangement - it has to be, or an unchanged soulhome could not skip a rescan. Binding to
 *       it means one bookshelf added to an attuned library silently unbinds it, which is the worst
 *       possible version of this feature.</li>
 *   <li><b>Not the position.</b> A room's centre moves the moment a wing is added.</li>
 * </ul>
 *
 * So a room gets a plain serial number the first time it is classified, and every later scan carries
 * it forward by <b>footprint overlap</b>: a fresh room inherits the identity of whichever previously
 * known room it most overlaps, provided the overlap is at least
 * {@link #MIN_OVERLAP_SHARE} of the smaller of the two. Adding a wing, knocking a wall through,
 * rebuilding a wall and re-roofing all leave far more than half of the box where it was; two rooms
 * that merely share a wall overlap by a single slab and are never confused for one another.
 *
 * <p>Splitting one room into two is decided the same way: the half with the larger overlap keeps the
 * identity and the other is a new room. Merging two into one is its mirror - the merged room keeps
 * one identity, and the other room's binding becomes a ghost. That is deliberate rather than
 * tolerated: see below.
 *
 * <h2>A binding outlives its room</h2>
 *
 * #152 asks for this explicitly. A bound room that stops existing keeps its binding and keeps
 * holding its slot. Freeing the slot would mean demolishing a room silently re-arms something else,
 * and rebuilding it would then silently take that back - two changes to what a player is carrying
 * that they never asked for and were never told about. A ghost binding is instead re-matched to the
 * rebuild by the same overlap rule, so demolishing and rebuilding a room leaves the loadout exactly
 * as it was.
 *
 * <h2>Order independence</h2>
 *
 * Every decision here is taken from the pairs themselves - overlap, then archetype agreement, then
 * the ids - never from the order either list happens to arrive in. The same soulhome therefore
 * reconciles to the same ids however the scan walked it, which is the same property
 * {@link SoulRegion#identityHash} needs for rescan skipping to be sound.
 */
public final class AttunementBook
{
    /**
     * How much of the smaller of two footprints must overlap before they are taken to be the same
     * room. A half is generous on purpose: the failure it guards against is a player's binding
     * quietly evaporating because they extended a room, and the failure it risks - two rooms on top
     * of one another being confused - needs one to sit inside more than half of the other, which is
     * not two rooms.
     */
    public static final double MIN_OVERLAP_SHARE = 0.5d;

    private AttunementBook()
    {
    }

    /**
     * What a scan's rooms are, once their identities have been carried across from the last one.
     *
     * @param rooms          the fresh rooms, each now carrying a {@code roomId}
     * @param bindings       the attunements, with every binding whose room was seen this scan brought
     *                       up to date, and every binding whose room was not left exactly as it was
     * @param newlyNumbered  the ids handed out for the first time this scan - rooms nothing known was
     *                       carried onto. What {@link #autoFill} is allowed to reach for, and the
     *                       reason a room a player deliberately released does not quietly come back
     * @param nextRoomId     the serial to hand out next
     */
    public record Reconciliation(
            List<AwardedRoom> rooms, List<RoomBinding> bindings, Set<Integer> newlyNumbered, int nextRoomId)
    {
        public Reconciliation
        {
            rooms = List.copyOf(rooms);
            bindings = List.copyOf(bindings);
            newlyNumbered = Set.copyOf(newlyNumbered);
        }
    }

    /**
     * Carry room identities across from the previous scan onto this one.
     *
     * @param previous what the last scan found, each room carrying the id it was given then
     * @param bindings the current attunements, which may name rooms {@code previous} no longer holds
     * @param fresh    what this scan found, with no ids yet
     */
    public static Reconciliation reconcile(
            List<AwardedRoom> previous, List<RoomBinding> bindings, List<AwardedRoom> fresh, int nextRoomId)
    {
        final List<Known> known = knownRooms(previous, bindings);
        final int[] assigned = new int[fresh.size()];

        // every pair that could plausibly be the same room, strongest evidence first. Built in full
        // rather than greedily walked, because "the best match for this fresh room" and "the best
        // match for this known room" are different questions and only taking the pairs in order
        // answers both at once
        List<Candidate> candidates = new ArrayList<>();

        for (int k = 0; k < known.size(); k++)
        {
            final Known candidate = known.get(k);

            if (candidate.footprint() == null)
            {
                continue;
            }

            for (int f = 0; f < fresh.size(); f++)
            {
                final RegionBounds here = fresh.get(f).footprint();

                if (here == null)
                {
                    continue;
                }

                final long overlap = overlapVolume(candidate.footprint(), here);

                if (overlap <= 0)
                {
                    continue;
                }

                final long smaller = Math.min(candidate.footprint().volume(), here.volume());

                if (overlap < Math.ceil(smaller * MIN_OVERLAP_SHARE))
                {
                    continue;
                }

                candidates.add(new Candidate(
                        candidate.roomId(), f, overlap,
                        candidate.archetypeId().equals(fresh.get(f).archetypeId())));
            }
        }

        final Comparator<Candidate> byOverlap = Comparator.comparingLong(Candidate::overlap);

        final Comparator<Candidate> strongestFirst = byOverlap.reversed()
                // a room that is still the same kind of room is the better match for a tie, but only
                // as a tiebreak: a library converted into a workshop is still that room, and letting
                // the archetype decide identity would mean rebuilding a room's purpose silently
                // unbinds it - exactly the fault the whole overlap rule exists to avoid
                .thenComparingInt(candidate -> candidate.sameArchetype() ? 0 : 1)
                .thenComparingInt(Candidate::roomId)
                .thenComparingInt(Candidate::freshIndex);

        candidates.sort(strongestFirst);

        Set<Integer> takenIds = new HashSet<>();

        for (Candidate candidate : candidates)
        {
            if (assigned[candidate.freshIndex()] != 0 || takenIds.contains(candidate.roomId()))
            {
                continue;
            }

            assigned[candidate.freshIndex()] = candidate.roomId();
            takenIds.add(candidate.roomId());
        }

        List<AwardedRoom> rooms = new ArrayList<>(fresh.size());
        Set<Integer> newlyNumbered = new LinkedHashSet<>();
        int next = Math.max(1, nextRoomId);

        for (int f = 0; f < fresh.size(); f++)
        {
            if (assigned[f] != 0)
            {
                rooms.add(fresh.get(f).withRoomId(assigned[f]));
                continue;
            }

            newlyNumbered.add(next);
            rooms.add(fresh.get(f).withRoomId(next++));
        }

        Map<Integer, AwardedRoom> byId = new HashMap<>();

        for (AwardedRoom room : rooms)
        {
            byId.put(room.roomId(), room);
        }

        List<RoomBinding> updated = new ArrayList<>(bindings.size());

        for (RoomBinding binding : bindings)
        {
            final AwardedRoom seen = byId.get(binding.roomId());

            // a binding whose room was not seen this scan is left exactly as it was, footprint
            // included - that footprint is the only thing a later rebuild can be matched against
            updated.add(seen == null ? binding : binding.seenAs(seen));
        }

        return new Reconciliation(rooms, updated, newlyNumbered, next);
    }

    /**
     * Bind rooms the player has never seen bound into whatever slots are free, best first.
     *
     * <p>This is what makes "a player who updates and changes nothing notices nothing" a property of
     * the mechanism rather than a hope about tuning. Without it, attunement landing on an existing
     * save would zero every buff in it until its owner walked to their anchor - which is not the
     * nerf #151 argues for, it is the mod appearing to break. With it, a soul under its slot count
     * carries exactly what it carried before, a soul over it carries its best rooms and is told once
     * (#157), and the choice only ever presents itself at the point where there is actually one to
     * make.
     *
     * <p>Only {@code candidates} - the ids handed out for the first time this scan - are reached
     * for. A room the player has <b>released</b> is a room they know about, and quietly re-binding it
     * the moment a slot opened would be the system overruling the only decision it exists to ask for.
     *
     * @return whether anything was bound
     */
    public static boolean autoFill(
            List<RoomBinding> bindings,
            List<AwardedRoom> rooms,
            Set<Integer> candidates,
            Map<String, ArchetypeDefinition> archetypes,
            AttunementSettings settings,
            int rank)
    {
        if (!settings.enabled() || candidates.isEmpty())
        {
            return false;
        }

        List<AwardedRoom> eligible = new ArrayList<>();
        final Set<Integer> bound = boundIds(bindings);

        for (AwardedRoom room : rooms)
        {
            if (candidates.contains(room.roomId()) && !bound.contains(room.roomId()))
            {
                eligible.add(room);
            }
        }

        // best first, so an existing save keeps the rooms its owner is proudest of rather than
        // whichever ones the scan happened to walk into first
        eligible.sort(Comparator.comparingDouble(AwardedRoom::score).reversed()
                .thenComparingInt(AwardedRoom::roomId));

        Map<RoomPool, Integer> used = new LinkedHashMap<>(slotsUsed(bindings, rooms, archetypes));
        boolean changed = false;

        for (AwardedRoom room : eligible)
        {
            final RoomPool pool = poolOf(archetypes.get(room.archetypeId()), room.aspectId());

            if (used.getOrDefault(pool, 0) >= settings.slotsFor(pool, rank))
            {
                continue;
            }

            bindings.add(RoomBinding.of(room));
            used.merge(pool, 1, Integer::sum);
            changed = true;
        }

        return changed;
    }

    /**
     * The rooms as a soulhome saves them while {@code attunement.enabled} is off: no identities, no
     * footprints, nothing the epic added. #151 rule 1 asks for the switch to restore the mod as it
     * was rather than to skip a step, and a save file that quietly grew two fields per room is not
     * that.
     */
    public static List<AwardedRoom> anonymise(List<AwardedRoom> rooms)
    {
        List<AwardedRoom> out = new ArrayList<>(rooms.size());

        for (AwardedRoom room : rooms)
        {
            out.add(room.anonymised());
        }

        return List.copyOf(out);
    }

    /**
     * The rooms whose buffs a player actually carries.
     *
     * <p>With attunement off this is every classified room, unchanged and in the same order, which
     * is what makes {@code enabled=false} indistinguishable from the mod before this epic. With it
     * on it is the bound ones - and the order is preserved, so the repeated-room falloff in
     * {@link BuffCalculator} ranks the attuned subset against itself. Attuning your second-best
     * library then grants what your best one would have: any other answer punishes a player for a
     * choice the system invited them to make (#153).
     */
    public static List<AwardedRoom> carried(
            List<AwardedRoom> rooms, Collection<RoomBinding> bindings, AttunementSettings settings)
    {
        if (!settings.enabled())
        {
            return rooms;
        }

        final Set<Integer> bound = boundIds(bindings);
        List<AwardedRoom> out = new ArrayList<>();

        for (AwardedRoom room : rooms)
        {
            if (bound.contains(room.roomId()))
            {
                out.add(room);
            }
        }

        return List.copyOf(out);
    }

    public static Set<Integer> boundIds(Collection<RoomBinding> bindings)
    {
        Set<Integer> ids = new LinkedHashSet<>();

        for (RoomBinding binding : bindings)
        {
            ids.add(binding.roomId());
        }

        return ids;
    }

    /**
     * Which pool a room's slot comes out of - see {@link RoomPool}. An archetype that is not loaded
     * (a datapack removed between the scan and this call) reads as passive, which costs a slot from
     * the larger pool rather than from the scarcer one.
     */
    public static RoomPool poolOf(ArchetypeDefinition archetype, String aspectId)
    {
        if (archetype == null)
        {
            return RoomPool.PASSIVE;
        }

        for (ArchetypeDefinition.BuffSpec spec : archetype.buffsFor(aspectId))
        {
            if (SoulBuffTypes.isActive(spec.type()))
            {
                return RoomPool.ACTIVE;
            }
        }

        return RoomPool.PASSIVE;
    }

    /**
     * How many slots of each pool the current bindings occupy.
     *
     * <p>A bound room that is in this scan is counted under the aspect it took; a ghost - a binding
     * whose room is not there - under its archetype's own default, since an aspect is derived from
     * blocks that are not currently standing anywhere.
     */
    public static Map<RoomPool, Integer> slotsUsed(
            Collection<RoomBinding> bindings, List<AwardedRoom> rooms, Map<String, ArchetypeDefinition> archetypes)
    {
        Map<Integer, AwardedRoom> byId = new HashMap<>();

        for (AwardedRoom room : rooms)
        {
            byId.put(room.roomId(), room);
        }

        Map<RoomPool, Integer> used = new LinkedHashMap<>();
        used.put(RoomPool.PASSIVE, 0);
        used.put(RoomPool.ACTIVE, 0);

        for (RoomBinding binding : bindings)
        {
            final AwardedRoom room = byId.get(binding.roomId());
            final String archetypeId = room == null ? binding.archetypeId() : room.archetypeId();
            final String aspectId = room == null ? null : room.aspectId();

            used.merge(poolOf(archetypes.get(archetypeId), aspectId), 1, Integer::sum);
        }

        return used;
    }

    /**
     * Whether this soulhome holds more rooms of some kind than it has slots to carry them - the
     * moment #157 says a player has to be told, once, what happened and where to change it.
     *
     * <p>Asked of the rooms rather than of the bindings: a player who has not bound anything yet has
     * not "used" a slot, and is exactly the player who most needs telling.
     */
    public static boolean exceedsSlots(
            List<AwardedRoom> rooms, Map<String, ArchetypeDefinition> archetypes, AttunementSettings settings, int rank)
    {
        if (!settings.enabled())
        {
            return false;
        }

        Map<RoomPool, Integer> held = new LinkedHashMap<>();

        for (AwardedRoom room : rooms)
        {
            held.merge(poolOf(archetypes.get(room.archetypeId()), room.aspectId()), 1, Integer::sum);
        }

        for (RoomPool pool : RoomPool.values())
        {
            if (held.getOrDefault(pool, 0) > settings.slotsFor(pool, rank))
            {
                return true;
            }
        }

        return false;
    }

    /** Why a binding request was refused, or that it was not. */
    public enum BindResult
    {
        BOUND,
        UNBOUND,

        /** The room id names nothing this soulhome has ever classified. */
        NO_SUCH_ROOM,

        /** Every slot of that room's pool is spoken for. */
        NO_SLOTS,

        /** Already in the state asked for; nothing was written. */
        UNCHANGED
    }

    /**
     * Bind or unbind one room, server-side, against the actual room set. Every check lives here
     * rather than in the screen: the client's copy of what is attuned exists to be drawn, and a
     * forged message must change nothing (#154).
     *
     * @param bindings the current bindings; mutated in place on success
     * @return what happened, for the message the player gets back
     */
    public static BindResult apply(
            List<RoomBinding> bindings,
            List<AwardedRoom> rooms,
            Map<String, ArchetypeDefinition> archetypes,
            AttunementSettings settings,
            int rank,
            int roomId,
            boolean bind)
    {
        RoomBinding existing = null;

        for (RoomBinding binding : bindings)
        {
            if (binding.roomId() == roomId)
            {
                existing = binding;
                break;
            }
        }

        if (!bind)
        {
            if (existing == null)
            {
                return BindResult.UNCHANGED;
            }

            bindings.remove(existing);
            return BindResult.UNBOUND;
        }

        if (existing != null)
        {
            return BindResult.UNCHANGED;
        }

        AwardedRoom room = null;

        for (AwardedRoom candidate : rooms)
        {
            if (candidate.roomId() == roomId)
            {
                room = candidate;
                break;
            }
        }

        if (room == null)
        {
            // a room that is not in the current scan cannot be bound. A ghost is the other way
            // round - it was bound while it existed - and stays bound without passing through here
            return BindResult.NO_SUCH_ROOM;
        }

        final RoomPool pool = poolOf(archetypes.get(room.archetypeId()), room.aspectId());

        if (slotsUsed(bindings, rooms, archetypes).getOrDefault(pool, 0) >= settings.slotsFor(pool, rank))
        {
            return BindResult.NO_SLOTS;
        }

        bindings.add(RoomBinding.of(room));
        return BindResult.BOUND;
    }

    /**
     * What one room would grant on its own - the "would grant Sword damage +20%" line #153 asks for
     * on an unattuned room.
     *
     * <p>Computed by the same arithmetic {@link BuffCalculator} uses, for a soulhome in which this
     * were the only room of its archetype: the room's own magnitude, its archetype's declared
     * ceiling, the config multiplier, rank amplification and the global type cap. It is therefore
     * exactly what the player would get for attuning it into an empty soulhome, and an over-estimate
     * only where they already have a better room of the same kind and the falloff would bite. Said
     * as "would grant" rather than as a promise for that reason.
     */
    public static Map<String, Double> wouldGrant(
            AwardedRoom room, ArchetypeDefinition archetype, BuffSettings settings, int rank)
    {
        Map<String, Double> grants = new LinkedHashMap<>();

        if (archetype == null)
        {
            return grants;
        }

        final double multiplier = settings.multiplierFor(archetype.id());

        for (ArchetypeDefinition.BuffSpec spec : archetype.buffsFor(room.aspectId()))
        {
            final double beforeRank =
                    Math.min(spec.magnitudeAt(room.score(), archetype, settings), spec.max()) * multiplier;

            final double granted = SoulBuffTypes.amplifiesWithRank(spec.type())
                    ? beforeRank * settings.rankFactor(rank)
                    : beforeRank;

            if (granted <= 0d)
            {
                continue;
            }

            grants.merge(spec.type(), Math.min(granted, settings.capFor(spec.type(), rank)), Double::sum);
        }

        return grants;
    }

    /** Every room previously known, by id, with the freshest footprint each one has. */
    private static List<Known> knownRooms(List<AwardedRoom> previous, List<RoomBinding> bindings)
    {
        Map<Integer, Known> known = new LinkedHashMap<>();

        for (AwardedRoom room : previous)
        {
            if (room.hasIdentity() && room.footprint() != null)
            {
                known.put(room.roomId(), new Known(room.roomId(), room.archetypeId(), room.footprint()));
            }
        }

        for (RoomBinding binding : bindings)
        {
            // a binding whose room is in the previous scan says the same thing that scan does; one
            // whose room is not is a ghost, and is the only record of where it used to stand
            known.putIfAbsent(
                    binding.roomId(), new Known(binding.roomId(), binding.archetypeId(), binding.footprint()));
        }

        return List.copyOf(known.values());
    }

    /** Cells two boxes have in common. */
    private static long overlapVolume(RegionBounds a, RegionBounds b)
    {
        final long x = span(a.minX(), a.maxX(), b.minX(), b.maxX());
        final long y = span(a.minY(), a.maxY(), b.minY(), b.maxY());
        final long z = span(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());

        return x * y * z;
    }

    private static long span(int aMin, int aMax, int bMin, int bMax)
    {
        return Math.max(0L, Math.min(aMax, bMax) - Math.max(aMin, bMin) + 1L);
    }

    private record Known(int roomId, String archetypeId, RegionBounds footprint)
    {
    }

    private record Candidate(int roomId, int freshIndex, long overlap, boolean sameArchetype)
    {
    }
}
