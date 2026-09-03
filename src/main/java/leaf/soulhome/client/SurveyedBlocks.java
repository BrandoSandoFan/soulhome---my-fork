/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.client;

import java.util.List;

/**
 * What Surveyor's Eye (#88) is currently showing this client, and for how much longer.
 *
 * <p>A plain static holder, like {@code ClientSoulBuffs} - there is one local player, nothing here
 * needs a client-only type to hold it, and the alternative (a capability) would buy nothing.
 *
 * <p>The countdown runs on the client because the server has already decided how long the ability
 * lasts and has no reason to send a second packet saying "now stop". A client that misses the
 * expiry - because it lagged, or because the window closed while the chunk was unloaded - simply
 * draws nothing once the count reaches zero.
 */
public final class SurveyedBlocks
{
    private static volatile List<Long> positions = List.of();
    private static volatile int ticksRemaining;

    private SurveyedBlocks()
    {
    }

    public static void accept(List<Long> newPositions, int durationTicks)
    {
        positions = newPositions == null ? List.of() : List.copyOf(newPositions);
        ticksRemaining = Math.max(0, durationTicks);
    }

    public static List<Long> current()
    {
        return ticksRemaining > 0 ? positions : List.of();
    }

    public static void tick()
    {
        if (ticksRemaining > 0 && --ticksRemaining == 0)
        {
            // dropped rather than merely hidden: a long survey of a big vein is the largest thing
            // this client is holding for the mod, and there is no reason to keep it once it is over
            positions = List.of();
        }
    }

    public static void clear()
    {
        positions = List.of();
        ticksRemaining = 0;
    }
}
