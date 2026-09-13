/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.Locale;

/**
 * Which pool of attunement slots a room is bound into (#151/#156).
 *
 * <p>A room is {@link #ACTIVE} when what it grants is pressed rather than carried - see
 * {@link SoulBuffTypes#ACTIVE} - and {@link #PASSIVE} otherwise. A room granting both is active:
 * the ability is the reason a player would spend the slot, and charging them a passive slot for it
 * as well would make one room cost two.
 *
 * <p>Decided from what the room actually pays out, under the aspect it took (#171), rather than
 * from its archetype's top-level buffs - a library is a passive room either way, but an archetype
 * whose aspects differ in kind would otherwise be filed under whichever one it was written with.
 */
public enum RoomPool
{
    PASSIVE,
    ACTIVE;

    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Reads back a serialised pool, defaulting to {@link #PASSIVE} for anything unrecognised. */
    public static RoomPool byName(String name)
    {
        for (RoomPool pool : values())
        {
            if (pool.getSerializedName().equalsIgnoreCase(name))
            {
                return pool;
            }
        }

        return PASSIVE;
    }
}
