/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's targeted knockback resistance against everyone whose head hangs in one of their
 * trophy rooms (#196).
 *
 * <p>Deliberately separate from {@link BuffCalculator}: that pipeline only ever produces a
 * magnitude per buff type, and a magnitude has nowhere to carry "against whom". This reads the
 * same carried rooms it does, but keeps its own small result shape.
 */
public final class TrophyGrudges
{
    /** Tier 1's targeted fraction - see the room's own {@code buffs} block for the general half. */
    private static final double BASE_FRACTION = 0.25d;

    /** Tier 3 is {@link #BASE_FRACTION} + 2 * this. */
    private static final double FRACTION_PER_TIER = 0.125d;

    /** The one archetype whose mounted heads mean anything - see the room's own follow-up note. */
    public static final String TROPHY_ROOM_ARCHETYPE = "soulhome:trophy_room";

    private TrophyGrudges()
    {
    }

    /**
     * One player's grudges: whom they take less knockback from, and by how much.
     *
     * <p>Only rooms the player actually carries contribute - an unattuned trophy room grants no
     * general resistance either, and the targeted half is not a way around that. A player mounting
     * the same target's head in two carried trophy rooms takes the stronger of the two grudges
     * rather than both added together - "one head, one grudge" holds across rooms as well as within
     * one.
     */
    public static Map<UUID, Double> compute(List<AwardedRoom> carried)
    {
        Map<UUID, Double> grudges = new HashMap<>();

        for (AwardedRoom room : carried)
        {
            if (!TROPHY_ROOM_ARCHETYPE.equals(room.archetypeId()) || !room.hasMountedHeads())
            {
                continue;
            }

            final double fraction = fractionFor(room.tier());

            for (HeadOwner owner : room.mountedHeads())
            {
                grudges.merge(owner.id(), fraction, Math::max);
            }
        }

        return grudges;
    }

    /** This tier's targeted fraction - tier 1 is {@link #BASE_FRACTION}, tier 3 is 0.5. */
    public static double fractionFor(int tier)
    {
        final int clamped = Math.max(1, Math.min(3, tier));
        return BASE_FRACTION + (clamped - 1) * FRACTION_PER_TIER;
    }
}
