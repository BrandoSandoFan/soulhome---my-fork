/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Map;

/**
 * How the rooms a player has built turn into magnitudes.
 *
 * <p>The values come from {@code SoulHomeConfig}; they live here, as a plain record, so that the
 * aggregation rules stay testable without a Forge config in the way and so that every rule is in
 * one place rather than scattered through the effects.
 *
 * @param repeatedRoomFalloff  what each additional room of an archetype is worth relative to the
 *                             one before it. 0.5 means a second library counts half, a third a
 *                             quarter. Building one good library should beat building four
 *                             mediocre ones.
 * @param maxRoomsPerArchetype hard cap on how many rooms of one archetype contribute at all
 * @param globalMaxMagnitude   default ceiling on a buff type, however it was accumulated. The last
 *                             line of defence against a datapack that means well.
 * @param archetypeMultipliers per-archetype scaling by id, for packs that want a shipped
 *                             archetype turned down without editing its file. Anything absent is
 *                             1.0, which is why this is a sparse map rather than a full table.
 * @param buffTypeCaps         ceilings for buff types whose magnitude is not a fraction. Magnitudes
 *                             are unitless, so one number cannot cap them all: 1.0 is a doubling
 *                             of experience gain and would be a rounding error at an enchanting
 *                             table. Anything absent falls back to {@code globalMaxMagnitude}.
 */
public record BuffSettings(
        double repeatedRoomFalloff,
        int maxRoomsPerArchetype,
        double globalMaxMagnitude,
        Map<String, Double> archetypeMultipliers,
        Map<String, Double> buffTypeCaps)
{
    /**
     * Enchanting power is measured in effective levels rather than as a proportion, so it is
     * capped in levels. Six is two thirds of the fifteen-bookshelf bonus vanilla already gives,
     * which is a meaningful reward for a whole room without making the table's own furniture
     * pointless.
     */
    public static final Map<String, Double> DEFAULT_TYPE_CAPS =
            Map.of(SoulBuffTypes.ENCHANTMENT_POWER, 6.0d);

    public static final BuffSettings DEFAULTS =
            new BuffSettings(0.5d, 3, 1.0d, Map.of(), DEFAULT_TYPE_CAPS);

    /** The common case: no per-archetype tuning, and the built-in type ceilings. */
    public BuffSettings(double repeatedRoomFalloff, int maxRoomsPerArchetype, double globalMaxMagnitude)
    {
        this(repeatedRoomFalloff, maxRoomsPerArchetype, globalMaxMagnitude, Map.of(), DEFAULT_TYPE_CAPS);
    }

    public BuffSettings(
            double repeatedRoomFalloff,
            int maxRoomsPerArchetype,
            double globalMaxMagnitude,
            Map<String, Double> archetypeMultipliers)
    {
        this(repeatedRoomFalloff, maxRoomsPerArchetype, globalMaxMagnitude, archetypeMultipliers, DEFAULT_TYPE_CAPS);
    }

    public BuffSettings
    {
        archetypeMultipliers = archetypeMultipliers == null
                ? Map.of()
                : Map.copyOf(archetypeMultipliers);
        buffTypeCaps = buffTypeCaps == null ? Map.of() : Map.copyOf(buffTypeCaps);

        if (repeatedRoomFalloff < 0 || repeatedRoomFalloff > 1)
        {
            throw new IllegalArgumentException(
                    "repeatedRoomFalloff must be between 0 and 1, got " + repeatedRoomFalloff);
        }

        if (maxRoomsPerArchetype < 1)
        {
            throw new IllegalArgumentException(
                    "maxRoomsPerArchetype must be at least 1, got " + maxRoomsPerArchetype);
        }

        if (globalMaxMagnitude < 0)
        {
            throw new IllegalArgumentException(
                    "globalMaxMagnitude must not be negative, got " + globalMaxMagnitude);
        }
    }

    /** This archetype's magnitude multiplier, defaulting to unchanged. */
    public double multiplierFor(String archetypeId)
    {
        final Double multiplier = this.archetypeMultipliers.get(archetypeId);
        return multiplier == null ? 1d : Math.max(0d, multiplier);
    }

    /**
     * The ceiling for one buff type. Applied both when the magnitude is computed and again when an
     * effect reads it, so a saved value from an older, more generous config cannot outlive it.
     */
    public double capFor(String buffType)
    {
        final Double cap = this.buffTypeCaps.get(buffType);
        return cap == null ? this.globalMaxMagnitude : Math.max(0d, cap);
    }
}
