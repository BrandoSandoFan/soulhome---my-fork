/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.structures.core.SoulBuffSet;

/**
 * The client's copy of its own player's buffs, as last sent by the server.
 *
 * <p>Display first - a tooltip should not cost a round trip - but not only display. Block breaking
 * is predicted by the client and confirmed by the server, so an effect that changes break speed
 * has to be able to read a magnitude on both sides or the two disagree and the block snaps back.
 * {@link SoulBuffs#of} routes client-side reads here for exactly that case.
 *
 * <p>Deliberately a plain static holder rather than a client capability: nothing here needs a
 * client-only class, so there is no dist hazard, and there is exactly one local player.
 *
 * <p>Never authoritative. The server computes and clamps every magnitude before sending it, and a
 * server-side effect always reads the capability instead.
 */
public final class ClientSoulBuffs
{
    private static volatile SoulBuffSet current = SoulBuffSet.empty();

    private ClientSoulBuffs()
    {
    }

    public static void accept(SoulBuffSet buffs)
    {
        current = buffs == null ? SoulBuffSet.empty() : buffs;
    }

    public static SoulBuffSet get()
    {
        return current;
    }

    /** Forget the previous player's buffs, on the way out of a world. */
    public static void clear()
    {
        current = SoulBuffSet.empty();
    }
}
