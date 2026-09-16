/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A room that was actually awarded an archetype, reduced to the few things buffs depend on.
 *
 * <p>Deliberately small. This is what gets written to a soulhome's saved data, so that recomputing
 * a player's buffs on login is a map lookup rather than a chunk sweep - a player who logs in, dies
 * and changes dimension should not trigger three scans of a soulhome nobody has touched.
 *
 * @param aspectId which aspect the room took (the Aspects epic, #171), or null when its archetype
 *                 declares none and when the switch is off. Carried so that a login recomputes the
 *                 buff the last scan actually granted rather than quietly falling back to the
 *                 archetype's default - but never <i>trusted</i>: every scan derives it again, and
 *                 with {@code aspects.enabled} off nothing ever writes one, which is what makes the
 *                 saved form byte-identical to a save from before the epic (#176).
 * @param roomId   this room's stable identity across scans (the Attunement epic, #152), or 0 when
 *                 none has been assigned - which is every room on a server with
 *                 {@code attunement.enabled} off, and every room of a save written before the epic.
 *                 Assigned by {@link AttunementBook#reconcile}, never by the classifier: a region's
 *                 own {@code identityHash} is a digest of its shape and contents, so placing one
 *                 bookshelf would change it and silently unbind the room it named.
 * @param footprint where the room stood at this scan, or null when unknown. Kept for exactly one
 *                 purpose: it is what the <i>next</i> scan matches against to decide that a room
 *                 which has grown a wing is still the same room.
 * @param mountedHeads whose heads this room holds - the trophy room's targeted knockback
 *                 resistance (#196). Sorted by uuid wherever it is built, for the same reason
 *                 {@code identityHash} sorts everything else it folds in: two scans of an
 *                 unchanged wall of heads must agree. Empty for every archetype but the trophy
 *                 room, and empty for the trophy room too while its own tracking knob is off - see
 *                 {@code SoulHomeConfig#trackTrophyHeads}.
 */
public record AwardedRoom(
        String archetypeId, int tier, double score, String aspectId, int roomId, RegionBounds footprint,
        List<HeadOwner> mountedHeads)
{
    public AwardedRoom
    {
        if (archetypeId == null || archetypeId.isBlank())
        {
            throw new IllegalArgumentException("An awarded room must name its archetype");
        }

        if (tier < 1)
        {
            throw new IllegalArgumentException("An awarded room is at least tier 1, got " + tier);
        }

        if (roomId < 0)
        {
            throw new IllegalArgumentException("A room id is never negative, got " + roomId);
        }

        aspectId = aspectId == null || aspectId.isBlank() ? null : aspectId;
        mountedHeads = mountedHeads == null || mountedHeads.isEmpty()
                ? List.of()
                : mountedHeads.stream().sorted(Comparator.comparing(HeadOwner::id)).toList();
    }

    /** A room that took no aspect - every awarded room before #171, and most fixtures since. */
    public AwardedRoom(String archetypeId, int tier, double score)
    {
        this(archetypeId, tier, score, null);
    }

    /** A room with no identity of its own - what the classifier produces, before #152 reconciles it. */
    public AwardedRoom(String archetypeId, int tier, double score, String aspectId)
    {
        this(archetypeId, tier, score, aspectId, 0, null, List.of());
    }

    /** A room with no mounted heads of its own - every awarded room before #196. */
    public AwardedRoom(
            String archetypeId, int tier, double score, String aspectId, int roomId, RegionBounds footprint)
    {
        this(archetypeId, tier, score, aspectId, roomId, footprint, List.of());
    }

    public boolean hasAspect()
    {
        return this.aspectId != null;
    }

    /** Whether this room has been given a stable identity - see {@link #roomId}. */
    public boolean hasIdentity()
    {
        return this.roomId > 0;
    }

    /** This room, carrying {@code roomId} as its identity. */
    public AwardedRoom withRoomId(int roomId)
    {
        return new AwardedRoom(
                this.archetypeId, this.tier, this.score, this.aspectId, roomId, this.footprint,
                this.mountedHeads);
    }

    /**
     * This room with its identity and footprint dropped. What a soulhome saves while
     * {@code attunement.enabled} is off: neither field means anything without the epic, and writing
     * them anyway is the difference between "the switch is off" and "the switch is off but the save
     * file grew two columns" - see {@link AttunementBook#anonymise}.
     */
    public AwardedRoom anonymised()
    {
        return new AwardedRoom(
                this.archetypeId, this.tier, this.score, this.aspectId, 0, null, this.mountedHeads);
    }

    /** This room, carrying whose heads it holds - see {@link #mountedHeads}. */
    public AwardedRoom withMountedHeads(List<HeadOwner> mountedHeads)
    {
        return new AwardedRoom(
                this.archetypeId, this.tier, this.score, this.aspectId, this.roomId, this.footprint,
                mountedHeads);
    }

    /** Whether this room has one grudge or more - see {@link #mountedHeads}. */
    public boolean hasMountedHeads()
    {
        return !this.mountedHeads.isEmpty();
    }

    /** Reduce a full classification pass to just the rooms that earned something. */
    public static List<AwardedRoom> from(List<ClassificationResult> results)
    {
        List<AwardedRoom> awarded = new ArrayList<>();

        for (ClassificationResult result : results)
        {
            result.awarded().ifPresent(score -> awarded.add(new AwardedRoom(
                    score.archetypeId(), score.tier(), score.score(), score.aspectId(),
                    0, result.region().bounds(), List.of())));
        }

        return awarded;
    }
}
