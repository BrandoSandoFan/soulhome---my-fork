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
 * @param entryFraction        the fraction of a buff's ceiling granted right at an archetype's own
 *                             tier-1 threshold - what "just barely counts" is worth. A room scoring
 *                             below that threshold grants nothing at all.
 * @param rampExponent         shapes how the rest of the ceiling is spread between the entry
 *                             threshold and the top of the archetype's tier ladder. {@code 1.0} is
 *                             linear; above 1 the payout back-loads towards the top of the range,
 *                             which is what stops a bigger pile of the same one thing from being
 *                             worth much more than a smaller one. Must be positive and finite.
 */
public record BuffSettings(
        double repeatedRoomFalloff,
        int maxRoomsPerArchetype,
        double globalMaxMagnitude,
        Map<String, Double> archetypeMultipliers,
        Map<String, Double> buffTypeCaps,
        double entryFraction,
        double rampExponent)
{
    /**
     * Ceilings for the buff types that are not proportions, so {@code globalMaxMagnitude} - a
     * fraction by default - never becomes their cap by accident. Enchanting power is measured in
     * effective levels: six is two thirds of the fifteen-bookshelf bonus vanilla already gives.
     * Double jump is a count of extra jumps, and one is exactly what the name promises - a second
     * jump, not a third or fourth, however generous a datapack's own magnitude gets. Fire aspect
     * is seconds of burn, and six is a long, deliberate punish for a sword rather than a graze.
     * Mana is points of Iron's Spells' own resource, and sixty is somewhere between a robe and a
     * full set of it. Reach is blocks, and two is the difference between reaching the top of a
     * wall and not - three would put a player past the range the server rubber-bands them at.
     */
    public static final Map<String, Double> DEFAULT_TYPE_CAPS =
            Map.ofEntries(
                    Map.entry(SoulBuffTypes.ENCHANTMENT_POWER, 6.0d),
                    Map.entry(SoulBuffTypes.DOUBLE_JUMP, 1.0d),
                    Map.entry(SoulBuffTypes.FIRE_ASPECT, 6.0d),
                    Map.entry(SoulBuffTypes.MAX_MANA, 60.0d),
                    Map.entry(SoulBuffTypes.REACH, 2.0d),

                    // every active's ceiling is the highest 'max' any shipped archetype declares
                    // for it, so what the book promises is what a player can actually reach. Left
                    // out, each of these inherited globalMaxMagnitude - a fraction's default of
                    // 1.0 - and Aegis banked half a heart against a declared twelve while
                    // Thunderclap could never call a second bolt.
                    Map.entry(SoulBuffTypes.SURVEYORS_EYE, 3.0d),
                    Map.entry(SoulBuffTypes.AEGIS, 12.0d),
                    Map.entry(SoulBuffTypes.SOUL_STEP, 6.0d),
                    Map.entry(SoulBuffTypes.RALLY, 6.0d),
                    Map.entry(SoulBuffTypes.CALL_OF_THE_HERD, 3.0d),
                    Map.entry(SoulBuffTypes.THUNDERCLAP, 3.0d),
                    Map.entry(SoulBuffTypes.BARRAGE, 6.0d),
                    Map.entry(SoulBuffTypes.RUPTURE, 6.0d));

    /** Suggested starting points - see the class-level ramp knob documentation. */
    public static final double DEFAULT_ENTRY_FRACTION = 0.10d;
    public static final double DEFAULT_RAMP_EXPONENT = 1.5d;

    public static final BuffSettings DEFAULTS = new BuffSettings(
            0.5d, 3, 1.0d, Map.of(), DEFAULT_TYPE_CAPS, DEFAULT_ENTRY_FRACTION, DEFAULT_RAMP_EXPONENT);

    /** The common case: no per-archetype tuning, and the built-in type ceilings and ramp. */
    public BuffSettings(double repeatedRoomFalloff, int maxRoomsPerArchetype, double globalMaxMagnitude)
    {
        this(repeatedRoomFalloff, maxRoomsPerArchetype, globalMaxMagnitude, Map.of(), DEFAULT_TYPE_CAPS,
                DEFAULT_ENTRY_FRACTION, DEFAULT_RAMP_EXPONENT);
    }

    public BuffSettings(
            double repeatedRoomFalloff,
            int maxRoomsPerArchetype,
            double globalMaxMagnitude,
            Map<String, Double> archetypeMultipliers)
    {
        this(repeatedRoomFalloff, maxRoomsPerArchetype, globalMaxMagnitude, archetypeMultipliers, DEFAULT_TYPE_CAPS,
                DEFAULT_ENTRY_FRACTION, DEFAULT_RAMP_EXPONENT);
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

        if (entryFraction < 0 || entryFraction > 1)
        {
            throw new IllegalArgumentException(
                    "entryFraction must be between 0 and 1, got " + entryFraction);
        }

        if (!Double.isFinite(rampExponent) || rampExponent <= 0)
        {
            throw new IllegalArgumentException(
                    "rampExponent must be positive and finite, got " + rampExponent);
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

        if (cap != null)
        {
            return Math.max(0d, cap);
        }

        // a type absent from the config falls back to this table before globalMaxMagnitude, so a
        // server whose config file was written before a buff type existed still gets that type's
        // real ceiling. A config list is only ever written once, at first launch; without this,
        // adding a non-fraction buff silently capped it at the fraction default for every existing
        // save - which is exactly what happened to all eight active abilities.
        final Double fallback = DEFAULT_TYPE_CAPS.get(buffType);

        return fallback == null ? this.globalMaxMagnitude : Math.max(0d, fallback);
    }
}
