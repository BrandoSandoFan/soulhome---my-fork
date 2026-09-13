/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import leaf.soulhome.structures.core.ArchetypeDefinition;
import leaf.soulhome.structures.core.Aspect;
import leaf.soulhome.structures.core.AttunementBook;
import leaf.soulhome.structures.core.AttunementSettings;
import leaf.soulhome.structures.core.AwardedRoom;
import leaf.soulhome.structures.core.BuffSettings;
import leaf.soulhome.structures.core.RoomBinding;
import leaf.soulhome.structures.core.RoomPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a soulhome is carrying and what it could be carrying instead (the Attunement epic, #151).
 *
 * <p>One shape for three surfaces - the Soul Anchor's screen (#154), {@code /soulhome buffs} and
 * {@code /soulhome analyse} - built once on the server and, for the screen, sent as-is. Built rather
 * than recomputed on the client for the same reason {@link LensBuffReport} is: the client's copy of
 * a soulhome exists to be drawn, and a screen that worked out its own slot arithmetic would
 * eventually disagree with the server that actually refuses a binding.
 *
 * <p>{@code present} is the field that matters most here. A binding outlives the room it names
 * (#152), so a soulhome can be carrying a room that is not currently standing anywhere - and a
 * screen that simply left it out would show a slot spent on nothing.
 */
public record AttunementReport(
        int rank,
        int passiveUsed,
        int passiveSlots,
        int activeUsed,
        int activeSlots,
        boolean owner,
        List<Room> rooms)
{
    /** What the anchor sends when there is nothing to say - attunement off, or no soulhome at all. */
    public static final AttunementReport EMPTY = new AttunementReport(0, 0, 0, 0, 0, false, List.of());

    public static final Codec<AttunementReport> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.optionalFieldOf("rank", 0).forGetter(AttunementReport::rank),
                    Codec.INT.optionalFieldOf("passive_used", 0).forGetter(AttunementReport::passiveUsed),
                    Codec.INT.optionalFieldOf("passive_slots", 0).forGetter(AttunementReport::passiveSlots),
                    Codec.INT.optionalFieldOf("active_used", 0).forGetter(AttunementReport::activeUsed),
                    Codec.INT.optionalFieldOf("active_slots", 0).forGetter(AttunementReport::activeSlots),
                    Codec.BOOL.optionalFieldOf("owner", false).forGetter(AttunementReport::owner),
                    Room.CODEC.listOf().optionalFieldOf("rooms", List.of()).forGetter(AttunementReport::rooms))
            .apply(instance, AttunementReport::new));

    public AttunementReport
    {
        rooms = List.copyOf(rooms);
    }

    public int usedIn(RoomPool pool)
    {
        return pool == RoomPool.ACTIVE ? this.activeUsed : this.passiveUsed;
    }

    public int slotsIn(RoomPool pool)
    {
        return pool == RoomPool.ACTIVE ? this.activeSlots : this.passiveSlots;
    }

    public boolean isEmpty()
    {
        return this.rooms.isEmpty();
    }

    /** The rooms a player has built but is not carrying - what {@code /soulhome buffs} names. */
    public List<Room> dormant()
    {
        List<Room> out = new ArrayList<>();

        for (Room room : this.rooms)
        {
            if (!room.attuned() && room.present())
            {
                out.add(room);
            }
        }

        return out;
    }

    /**
     * Build a soulhome's report.
     *
     * @param owner whether the player being told is the soul's own owner, and so may change any of
     *              this. A visitor may look (#154).
     */
    public static AttunementReport of(
            List<AwardedRoom> awarded,
            List<RoomBinding> bindings,
            Map<String, ArchetypeDefinition> archetypes,
            AttunementSettings attunement,
            BuffSettings buffs,
            int rank,
            boolean owner)
    {
        if (!attunement.enabled())
        {
            return EMPTY;
        }

        final Set<Integer> bound = AttunementBook.boundIds(bindings);
        final Map<RoomPool, Integer> used = AttunementBook.slotsUsed(bindings, awarded, archetypes);

        Map<Integer, AwardedRoom> byId = new HashMap<>();

        for (AwardedRoom room : awarded)
        {
            byId.put(room.roomId(), room);
        }

        List<Room> rooms = new ArrayList<>();

        for (AwardedRoom room : awarded)
        {
            rooms.add(describe(room, archetypes.get(room.archetypeId()), buffs, rank, bound.contains(room.roomId()), true));
        }

        // the bound rooms that are not standing anywhere any more, listed after the ones that are:
        // a slot spent on a room a player pulled down is a slot they can plainly get back, and the
        // only way to see that is for the room to still be on the list
        for (RoomBinding binding : bindings)
        {
            if (byId.containsKey(binding.roomId()))
            {
                continue;
            }

            final ArchetypeDefinition archetype = archetypes.get(binding.archetypeId());

            rooms.add(new Room(
                    binding.roomId(),
                    binding.archetypeId(),
                    archetype == null ? binding.archetypeId() : archetype.displayName(),
                    "",
                    0,
                    0d,
                    true,
                    false,
                    AttunementBook.poolOf(archetype, null).getSerializedName(),
                    List.of()));
        }

        return new AttunementReport(
                rank,
                used.getOrDefault(RoomPool.PASSIVE, 0), attunement.slotsFor(RoomPool.PASSIVE, rank),
                used.getOrDefault(RoomPool.ACTIVE, 0), attunement.slotsFor(RoomPool.ACTIVE, rank),
                owner,
                rooms);
    }

    private static Room describe(
            AwardedRoom room, ArchetypeDefinition archetype, BuffSettings buffs, int rank, boolean attuned, boolean present)
    {
        final Aspect aspect = archetype == null ? null : archetype.aspect(room.aspectId());
        List<Grant> grants = new ArrayList<>();

        for (Map.Entry<String, Double> grant : AttunementBook.wouldGrant(room, archetype, buffs, rank).entrySet())
        {
            grants.add(new Grant(grant.getKey(), grant.getValue()));
        }

        return new Room(
                room.roomId(),
                room.archetypeId(),
                archetype == null ? room.archetypeId() : archetype.displayName(),
                aspect == null ? "" : aspect.displayName(),
                room.tier(),
                room.score(),
                attuned,
                present,
                AttunementBook.poolOf(archetype, room.aspectId()).getSerializedName(),
                grants);
    }

    /**
     * @param present whether this room is in the soulhome's current scan. False for a bound room
     *                that has been demolished - still bound, still holding its slot, and restored
     *                the moment it is rebuilt (#152)
     * @param grants  what this room grants, or would grant if it were attuned - see
     *                {@link AttunementBook#wouldGrant} for why that is a "would" rather than a promise
     */
    public record Room(
            int roomId,
            String archetypeId,
            String displayName,
            String aspectName,
            int tier,
            double score,
            boolean attuned,
            boolean present,
            String pool,
            List<Grant> grants)
    {
        public static final Codec<Room> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.INT.fieldOf("room_id").forGetter(Room::roomId),
                        Codec.STRING.fieldOf("archetype").forGetter(Room::archetypeId),
                        Codec.STRING.optionalFieldOf("display_name", "").forGetter(Room::displayName),
                        Codec.STRING.optionalFieldOf("aspect_name", "").forGetter(Room::aspectName),
                        Codec.INT.optionalFieldOf("tier", 0).forGetter(Room::tier),
                        Codec.DOUBLE.optionalFieldOf("score", 0d).forGetter(Room::score),
                        Codec.BOOL.optionalFieldOf("attuned", false).forGetter(Room::attuned),
                        Codec.BOOL.optionalFieldOf("present", true).forGetter(Room::present),
                        Codec.STRING.optionalFieldOf("pool", RoomPool.PASSIVE.getSerializedName()).forGetter(Room::pool),
                        Grant.CODEC.listOf().optionalFieldOf("grants", List.of()).forGetter(Room::grants))
                .apply(instance, Room::new));

        public Room
        {
            grants = List.copyOf(grants);
        }

        public RoomPool roomPool()
        {
            return RoomPool.byName(this.pool);
        }

        public boolean hasAspect()
        {
            return !this.aspectName.isBlank();
        }
    }

    public record Grant(String buffType, double magnitude)
    {
        public static final Codec<Grant> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.STRING.fieldOf("buff_type").forGetter(Grant::buffType),
                        Codec.DOUBLE.fieldOf("magnitude").forGetter(Grant::magnitude))
                .apply(instance, Grant::new));
    }
}
