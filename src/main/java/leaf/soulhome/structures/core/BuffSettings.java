/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * How the rooms a player has built turn into magnitudes.
 *
 * <p>These are provisional and belong to the balance pass, which will put them behind Forge
 * config. They live here so that the aggregation rules are testable and in one place rather than
 * scattered through the effects.
 *
 * @param repeatedRoomFalloff  what each additional room of an archetype is worth relative to the
 *                             one before it. 0.5 means a second library counts half, a third a
 *                             quarter. Building one good library should beat building four
 *                             mediocre ones.
 * @param maxRoomsPerArchetype hard cap on how many rooms of one archetype contribute at all
 * @param globalMaxMagnitude   ceiling on any single buff type, however it was accumulated. The
 *                             last line of defence against a datapack that means well.
 */
public record BuffSettings(
        double repeatedRoomFalloff,
        int maxRoomsPerArchetype,
        double globalMaxMagnitude)
{
    public static final BuffSettings DEFAULTS = new BuffSettings(0.5d, 3, 1.0d);

    public BuffSettings
    {
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
}
