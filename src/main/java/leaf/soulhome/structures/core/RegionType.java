/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Locale;
import java.util.Optional;

/**
 * The two shapes a candidate structure can take.
 *
 * <p>Both are needed. An armoury is a room; a farm is a field under an open sky, and a field is
 * not a room. Archetypes declare which types they accept.
 */
public enum RegionType
{
    /** A pocket of space fully enclosed by blocks - the classic room. */
    ENCLOSED("enclosed"),

    /** A dense cluster of signal-bearing blocks that is not inside any room. */
    OPEN("open");

    private final String serializedName;

    RegionType(String serializedName)
    {
        this.serializedName = serializedName;
    }

    public String getSerializedName()
    {
        return this.serializedName;
    }

    public static Optional<RegionType> byName(String name)
    {
        if (name == null)
        {
            return Optional.empty();
        }

        final String normalised = name.toLowerCase(Locale.ROOT);

        for (RegionType type : values())
        {
            if (type.serializedName.equals(normalised))
            {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }
}
