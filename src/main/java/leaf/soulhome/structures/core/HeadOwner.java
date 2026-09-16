/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.UUID;

/**
 * Whose head is mounted, for the trophy room's targeted knockback resistance (#196).
 *
 * <p>The identity is the {@code id} - a player who changes their name keeps their head's effect,
 * since a rename does not touch their UUID. {@code lastKnownName} is display only, refreshed
 * whenever the server can resolve it, and never compared for equality: two owners are the same
 * grudge if and only if their UUIDs match.
 */
public record HeadOwner(UUID id, String lastKnownName)
{
    public HeadOwner
    {
        if (id == null)
        {
            throw new IllegalArgumentException("A mounted head must name whose it is");
        }

        lastKnownName = lastKnownName == null || lastKnownName.isBlank() ? id.toString() : lastKnownName;
    }
}
