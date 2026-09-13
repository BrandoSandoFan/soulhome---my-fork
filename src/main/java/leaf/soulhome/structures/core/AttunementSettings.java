/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * How many rooms a soulhome may actually carry at once (the Attunement epic, #151), and whether it
 * is limited at all.
 *
 * <h2>Two pools, not one</h2>
 *
 * A passive buff and an active ability are not comparable goods. The actives exist precisely
 * because four passives stopped being worth more as they grew (#86/#87), so asking a player to
 * trade Aegis against a few percent of mining speed is asking a question with an obvious answer -
 * one pool would quietly mean "every slot is an active, plus whatever is left over". Two pools cost
 * twice the tuning surface and make each choice a real one; see {@link RoomPool}.
 *
 * <h2>Where the numbers come from</h2>
 *
 * #156 asks for the slot count to be taken off what a soulhome actually holds rather than guessed.
 * Two things bound that, and they bind at opposite ends of the climb:
 *
 * <ul>
 *   <li><b>Early, the ground does.</b> At rank 0 the apron (#158) reaches 18 blocks out, which is
 *       37x37 of island, against six build layers - one storey. A modest walled room with a gap
 *       around it is about 8x8, so the ground holds on the order of twenty of them before it is
 *       full, and a player who has been building for an evening has somewhere between four and ten.
 *       Seven slots is therefore met at about the eighth room: the point #156 names, where a player
 *       has enough variety to have a preference.</li>
 *   <li><b>Late, the archetype set does.</b> By rank V the island is 157x157 across thirty-six
 *       layers and space has stopped being the constraint; the mod ships 32 archetypes, of which 20
 *       are passive and 12 active. Seventeen slots at rank V is a little over half the set - a
 *       palette to choose from rather than a checklist to complete.</li>
 * </ul>
 *
 * A slot per rank in each pool joins those two ends without a table to extend, in the same shape
 * {@link SoulBounds} and {@link AscensionSettings} already grow in, so a pack that lengthens
 * {@code max_rank} gets slots for the new ranks for free.
 */
public record AttunementSettings(
        boolean enabled,
        int basePassiveSlots,
        int passiveSlotsPerRank,
        int baseActiveSlots,
        int activeSlotsPerRank)
{
    /**
     * On by default. #151 makes the switch non-negotiable, not the default - a mechanic shipped off
     * is a mechanic nobody meets, and the epic's whole argument is that the mod is worse without it.
     */
    public static final boolean DEFAULT_ENABLED = true;

    /** Five of the twenty passive archetypes at rank 0 - see the class javadoc for the working. */
    public static final int DEFAULT_BASE_PASSIVE_SLOTS = 5;

    public static final int DEFAULT_PASSIVE_SLOTS_PER_RANK = 1;

    /** Two of the twelve actives at rank 0: enough to have a favourite, few enough to have to pick. */
    public static final int DEFAULT_BASE_ACTIVE_SLOTS = 2;

    public static final int DEFAULT_ACTIVE_SLOTS_PER_RANK = 1;

    public static final AttunementSettings DEFAULTS = new AttunementSettings(
            DEFAULT_ENABLED, DEFAULT_BASE_PASSIVE_SLOTS, DEFAULT_PASSIVE_SLOTS_PER_RANK,
            DEFAULT_BASE_ACTIVE_SLOTS, DEFAULT_ACTIVE_SLOTS_PER_RANK);

    /** What a soulhome carried before this epic: no limit anywhere, which is what {@code enabled=false} restores. */
    public static final AttunementSettings OFF = new AttunementSettings(
            false, DEFAULT_BASE_PASSIVE_SLOTS, DEFAULT_PASSIVE_SLOTS_PER_RANK,
            DEFAULT_BASE_ACTIVE_SLOTS, DEFAULT_ACTIVE_SLOTS_PER_RANK);

    public AttunementSettings
    {
        if (basePassiveSlots < 0)
        {
            throw new IllegalArgumentException("basePassiveSlots must not be negative, got " + basePassiveSlots);
        }

        if (passiveSlotsPerRank < 0)
        {
            throw new IllegalArgumentException("passiveSlotsPerRank must not be negative, got " + passiveSlotsPerRank);
        }

        if (baseActiveSlots < 0)
        {
            throw new IllegalArgumentException("baseActiveSlots must not be negative, got " + baseActiveSlots);
        }

        if (activeSlotsPerRank < 0)
        {
            throw new IllegalArgumentException("activeSlotsPerRank must not be negative, got " + activeSlotsPerRank);
        }
    }

    /** How many slots of one pool a soulhome at this rank has. */
    public int slotsFor(RoomPool pool, int rank)
    {
        final int safeRank = Math.max(0, rank);

        return switch (pool)
        {
            case PASSIVE -> this.basePassiveSlots + safeRank * this.passiveSlotsPerRank;
            case ACTIVE -> this.baseActiveSlots + safeRank * this.activeSlotsPerRank;
        };
    }

    /** Every slot a soulhome at this rank has, across both pools - what the guide book quotes. */
    public int totalSlots(int rank)
    {
        return slotsFor(RoomPool.PASSIVE, rank) + slotsFor(RoomPool.ACTIVE, rank);
    }
}
