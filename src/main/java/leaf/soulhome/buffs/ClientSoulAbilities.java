/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.network.SyncSoulAbilitiesMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of its own player's active abilities (#87), as last sent by the server.
 *
 * <p>Display only, unlike {@link ClientSoulBuffs} - nothing about an active is predicted, because
 * the server is the only thing that decides whether a press did anything. What this exists for is
 * the HUD: charges, the selection, and a recharge arc that has to animate every frame off a packet
 * that arrives once a cooldown.
 *
 * <p>That animation is why {@link #tick} exists. Between packets the client walks its own copy of
 * each clock down; the next packet overwrites whatever it had drifted to, so a dropped or delayed
 * update costs at most a slightly wrong arc for one cooldown and never a wrong charge count.
 *
 * <p>Plain statics, and no client-only imports, for the same reasons as {@link ClientSoulBuffs}.
 */
public final class ClientSoulAbilities
{
    private static volatile String selected = "";
    private static volatile Map<String, SyncSoulAbilitiesMessage.State> states = Map.of();

    private ClientSoulAbilities()
    {
    }

    public static void accept(String newSelected, Map<String, SyncSoulAbilitiesMessage.State> newStates)
    {
        selected = newSelected == null ? "" : newSelected;
        states = newStates == null ? Map.of() : Map.copyOf(newStates);
    }

    public static String selected()
    {
        return selected;
    }

    public static Map<String, SyncSoulAbilitiesMessage.State> states()
    {
        return states;
    }

    /** The abilities the player owns, in the order the server sent them - the cycle key's order. */
    public static List<String> owned()
    {
        return List.copyOf(states.keySet());
    }

    public static boolean isEmpty()
    {
        return states.isEmpty();
    }

    public static SyncSoulAbilitiesMessage.State stateOf(String ability)
    {
        return states.get(ability);
    }

    /**
     * One client tick of the local clocks, so the HUD's arc moves between packets. Only the
     * remaining ticks are advanced - a charge landing is the server's to announce, so a clock that
     * runs out here simply sits at zero until it does.
     */
    public static void tick()
    {
        if (states.isEmpty())
        {
            return;
        }

        Map<String, SyncSoulAbilitiesMessage.State> advanced = new LinkedHashMap<>();

        for (Map.Entry<String, SyncSoulAbilitiesMessage.State> entry : states.entrySet())
        {
            final SyncSoulAbilitiesMessage.State state = entry.getValue();

            advanced.put(entry.getKey(), state.ticksToNext() <= 0
                    ? state
                    : new SyncSoulAbilitiesMessage.State(
                            state.charges(), state.ticksToNext() - 1, state.maxCharges(), state.cooldownTicks()));
        }

        states = Map.copyOf(advanced);
    }

    /** Forget the previous player's abilities, on the way out of a world. */
    public static void clear()
    {
        selected = "";
        states = Map.of();
    }
}
