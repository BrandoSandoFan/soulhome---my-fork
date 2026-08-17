/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a player has earned: buff type to magnitude.
 *
 * <p>Immutable, so the value that gets attached to a player, serialised to NBT and sent to a
 * client is the same object throughout and cannot be mutated behind any of their backs.
 *
 * <p>Magnitudes are unitless here on purpose - each effect decides what its number means. For
 * {@code soulhome:xp_gain} it is a fraction added to the rate; for
 * {@code soulhome:enchantment_power} it is levels added at the table.
 */
public final class SoulBuffSet
{
    private static final SoulBuffSet EMPTY = new SoulBuffSet(Map.of());

    private final Map<String, Double> magnitudes;

    private SoulBuffSet(Map<String, Double> magnitudes)
    {
        this.magnitudes = magnitudes;
    }

    public static SoulBuffSet empty()
    {
        return EMPTY;
    }

    /**
     * Build from raw values, dropping anything that would have no effect. Entries are ordered by
     * buff type so that serialisation and display are stable.
     */
    public static SoulBuffSet of(Map<String, Double> magnitudes)
    {
        List<String> types = new ArrayList<>(magnitudes.keySet());
        types.sort(Comparator.naturalOrder());

        Map<String, Double> cleaned = new LinkedHashMap<>();

        for (String type : types)
        {
            final Double magnitude = magnitudes.get(type);

            if (type == null || type.isBlank() || magnitude == null || magnitude <= 0d)
            {
                continue;
            }

            cleaned.put(type, magnitude);
        }

        return cleaned.isEmpty() ? EMPTY : new SoulBuffSet(cleaned);
    }

    /**
     * This player's magnitude for a buff type, or zero. The zero case is the common one - most
     * players have most buffs at zero - so it must be cheap and must never be null.
     */
    public double magnitude(String buffType)
    {
        return this.magnitudes.getOrDefault(buffType, 0d);
    }

    public boolean has(String buffType)
    {
        return magnitude(buffType) > 0d;
    }

    public boolean isEmpty()
    {
        return this.magnitudes.isEmpty();
    }

    public Map<String, Double> asMap()
    {
        return Collections.unmodifiableMap(this.magnitudes);
    }

    @Override
    public boolean equals(Object other)
    {
        return this == other
                || (other instanceof SoulBuffSet set && this.magnitudes.equals(set.magnitudes));
    }

    @Override
    public int hashCode()
    {
        return this.magnitudes.hashCode();
    }

    @Override
    public String toString()
    {
        return "SoulBuffSet" + this.magnitudes;
    }
}
