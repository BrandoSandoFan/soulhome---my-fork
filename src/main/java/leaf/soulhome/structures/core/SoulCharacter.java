/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a soulhome is made of, as a blend rather than as a verdict - the Ambience epic (#165).
 *
 * <p>Every classified room pulls the soul toward the traits its archetype declares, weighted by
 * what that room scored, and the result is the sum of those pulls. Nothing here picks a winner:
 * there is no threshold, no label, and no point at which a soulhome becomes a kind of soul. The
 * mod judges fuzzily everywhere else - two libraries that look nothing alike are both libraries -
 * and an ambience that announced "your soul is a fire soul" would be the first thing in it to sort
 * a player into a box.
 *
 * <p>Read from <b>every classified room</b>, not the attuned ones. Attunement (#151) is about what
 * a player carries out of their soul; this is about what is built in it, and a library you are not
 * carrying today is still standing there.
 *
 * <p>Three numbers come off this, and the distinction between them is the whole design:
 *
 * <ul>
 *   <li>{@link #lean} - which way an axis points, from -1 to 1.</li>
 *   <li>{@link #tension} - how contested it is. A soul of forges <i>and</i> freezers has a leaning
 *       of about zero and a tension of about one, and reads as neither warm nor cold nor the
 *       average of the two. See {@link SoulAxis}.</li>
 *   <li>{@link #depth} - how much has been built at all, so an empty soul is neutral and a full one
 *       is strongly coloured, with everything in between in between.</li>
 * </ul>
 *
 * <p>Minecraft-free, like the rest of {@code structures.core}, so the blend is testable without
 * booting a client to look at it.
 */
public record SoulCharacter(Map<SoulTrait, Double> pulls)
{
    /** A soul that has been built in, but not in any particular direction. */
    public static final SoulCharacter EMPTY = new SoulCharacter(Map.of());

    /**
     * Total pull at which a soul is half as coloured as it will ever be. Chosen against what rooms
     * actually score: a handful of characterful rooms at a middling tier lands near it, so the
     * colouring arrives over the course of building a soulhome rather than on the first room or
     * only on the fiftieth.
     */
    public static final double HALF_DEPTH_PULL = 150.0d;

    public SoulCharacter
    {
        Map<SoulTrait, Double> copy = new EnumMap<>(SoulTrait.class);

        if (pulls != null)
        {
            for (Map.Entry<SoulTrait, Double> entry : pulls.entrySet())
            {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0d)
                {
                    continue;
                }

                copy.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        pulls = Map.copyOf(copy);
    }

    /**
     * The blend of one soulhome's classified rooms.
     *
     * <p>Order-independent by construction - the pulls are summed - which matters because the room
     * list comes off a scan whose order is stable but whose meaning is not: a player who rebuilds
     * the same rooms in a different corner should get the same soul.
     *
     * @param rooms      every classified room, attuned or not
     * @param archetypes the loaded archetypes by id; a room whose archetype is gone (a datapack
     *                   removed between scans) contributes nothing rather than throwing
     */
    public static SoulCharacter of(List<AwardedRoom> rooms, Map<String, ArchetypeDefinition> archetypes)
    {
        if (rooms == null || rooms.isEmpty() || archetypes == null)
        {
            return EMPTY;
        }

        Map<SoulTrait, Double> pulls = new EnumMap<>(SoulTrait.class);

        for (AwardedRoom room : rooms)
        {
            final ArchetypeDefinition archetype = archetypes.get(room.archetypeId());

            if (archetype == null || archetype.character().isEmpty())
            {
                continue;
            }

            for (Map.Entry<SoulTrait, Double> pull : archetype.characterPulls().entrySet())
            {
                // weighted by what the room scored, so a fine hearth says more about a soul than a
                // hearth that only just qualified - the same currency every other judgement uses
                pulls.merge(pull.getKey(), pull.getValue() * Math.max(0d, room.score()), Double::sum);
            }
        }

        return new SoulCharacter(pulls);
    }

    public double pull(SoulTrait trait)
    {
        return this.pulls.getOrDefault(trait, 0d);
    }

    /** Everything pulling on this soul, in the same units {@link #pull} is in. */
    public double totalPull()
    {
        double total = 0d;

        for (double pull : this.pulls.values())
        {
            total += pull;
        }

        return total;
    }

    /** What this axis carries, both poles together. */
    public double presence(SoulAxis axis)
    {
        return pull(axis.positive()) + pull(axis.negative());
    }

    /** This axis's share of the whole soul, 0 to 1. Zero for a soul with nothing on it. */
    public double share(SoulAxis axis)
    {
        final double total = totalPull();

        return total <= 0d ? 0d : presence(axis) / total;
    }

    /**
     * Which way this axis points: 1 all the way to its positive pole, -1 all the way to its
     * negative one, 0 for an axis that is either untouched or exactly contested. Read it with
     * {@link #tension}, which is what tells those last two apart.
     */
    public double lean(SoulAxis axis)
    {
        final double presence = presence(axis);

        return presence <= 0d ? 0d : (pull(axis.positive()) - pull(axis.negative())) / presence;
    }

    /**
     * How contested this axis is: 0 when only one pole is built, 1 when both are built equally.
     * An untouched axis is 0 rather than undefined, so a soul with nothing on an axis and a soul
     * committed to one pole of it are both simply "not contested".
     */
    public double tension(SoulAxis axis)
    {
        final double positive = pull(axis.positive());
        final double negative = pull(axis.negative());
        final double presence = positive + negative;

        return presence <= 0d ? 0d : 2d * Math.min(positive, negative) / presence;
    }

    /**
     * How much of a character this soul has at all, 0 to 1: an unbuilt soul is neutral, and one
     * full of characterful rooms is strongly coloured. Saturating rather than capped, so there is
     * no point at which another room stops mattering and no point at which one more finishes it.
     */
    public double depth()
    {
        final double total = totalPull();

        return total <= 0d ? 0d : total / (total + HALF_DEPTH_PULL);
    }

    public boolean isEmpty()
    {
        return this.pulls.isEmpty();
    }
}
