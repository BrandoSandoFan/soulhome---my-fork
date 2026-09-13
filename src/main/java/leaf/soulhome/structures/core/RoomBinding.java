/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * One room bound into an attunement slot (#151/#152).
 *
 * <p>A binding is the attunement: there is no separate "attuned" flag anywhere, because a room is
 * attuned exactly when a binding names it. The archetype and footprint are the room's last-known
 * state rather than live data, and are here for the one case that makes this record necessary at
 * all - a bound room that is <b>not in the current scan</b>.
 *
 * <p>#152 asks for that case to be explicit: a demolished room keeps its binding rather than
 * silently freeing a slot that something else then silently fills. So a binding outlives its room,
 * holds its slot, and is re-matched to the rebuild by {@link AttunementBook#reconcile} - which
 * needs a footprint to match against and an archetype to know which pool the held slot came out of.
 *
 * @param roomId      the identity {@link AttunementBook#reconcile} assigned; never 0
 * @param archetypeId what the room was when it was last seen, so a ghost still knows its pool
 * @param footprint   where it stood when it was last seen, so a rebuild on the same spot is
 *                    recognised as the same room
 */
public record RoomBinding(int roomId, String archetypeId, RegionBounds footprint)
{
    public RoomBinding
    {
        if (roomId <= 0)
        {
            throw new IllegalArgumentException("A binding names a real room id, got " + roomId);
        }

        if (archetypeId == null || archetypeId.isBlank())
        {
            throw new IllegalArgumentException("A binding names its archetype");
        }
    }

    /** The binding for a room as it currently stands. */
    public static RoomBinding of(AwardedRoom room)
    {
        return new RoomBinding(room.roomId(), room.archetypeId(), room.footprint());
    }

    /** This binding, with the room's current whereabouts folded in - called on every scan it is seen in. */
    public RoomBinding seenAs(AwardedRoom room)
    {
        return new RoomBinding(this.roomId, room.archetypeId(), room.footprint());
    }
}
