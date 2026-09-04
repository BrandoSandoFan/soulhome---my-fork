/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSoulAbilitiesMessage;
import leaf.soulhome.structures.core.AbilityCharges;
import leaf.soulhome.structures.core.ActiveAbilitySettings;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's side of the active abilities (#87): what a player owns, what is banked, and the one
 * path a keypress travels to actually do something.
 *
 * <p><b>Nothing here trusts the client.</b> A press arrives as "I want to use X"; this decides
 * whether the player owns X, whether X is switched on, whether a charge is available, and whether
 * the press came too soon after the last one. The client's copy of all of that exists to draw a HUD
 * and for no other purpose. That asymmetry is deliberate and is the reason the two client-to-server
 * messages are the only ones in the mod - see {@code SyncSoulAbilitiesMessage} for the return leg.
 */
public final class SoulAbilities
{
    /**
     * Minimum server ticks between two accepted requests from one player, whatever they ask for.
     * A held key sends on repeat, and a rejected press still costs a packet to reject; this is the
     * cheapest possible answer and runs before anything looks at the capability.
     */
    private static final int REQUEST_INTERVAL_TICKS = 4;

    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private SoulAbilities()
    {
    }

    /**
     * Every active this player currently has a magnitude for, in the registry's own order so the
     * cycle key walks a stable list rather than one that reshuffles when a room is rescanned.
     */
    public static List<String> ownedBy(Player player)
    {
        if (player == null || player instanceof FakePlayer || !settings().enabled())
        {
            return List.of();
        }

        List<String> owned = new ArrayList<>();

        for (String type : SoulBuffEffects.knownTypes())
        {
            if (SoulBuffEffects.get(type) instanceof SoulActiveEffect
                    && SoulHomeConfig.isAbilityEnabled(type)
                    && SoulBuffs.magnitude(player, type) > 0d)
            {
                owned.add(type);
            }
        }

        return List.copyOf(owned);
    }

    /**
     * One server tick of recharging, for one player. Cheap and early-returning: the overwhelming
     * majority of players own no active at all and leave here having touched nothing.
     */
    public static void tick(ServerPlayer player)
    {
        if (player == null || player instanceof FakePlayer || !settings().enabled())
        {
            return;
        }

        List<String> owned = ownedBy(player);

        if (owned.isEmpty())
        {
            return;
        }

        player.getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(held ->
        {
            boolean chargesChanged = false;

            for (String type : owned)
            {
                if (!(SoulBuffEffects.get(type) instanceof SoulActiveEffect effect))
                {
                    continue;
                }

                final double magnitude = SoulBuffs.magnitude(player, type);
                final int maxCharges = maxChargesOf(effect, magnitude);
                final int cooldown = cooldownOf(effect, magnitude);

                // a newly granted ability arrives full: the player earned it by building the room,
                // and making them wait out a cooldown for something they have never used reads as
                // the mod being broken rather than as a cost
                AbilityCharges before = held.hasChargesFor(type)
                        ? held.chargesOf(type)
                        : AbilityCharges.full(maxCharges);

                AbilityCharges after = held.hasChargesFor(type) ? before.tick(maxCharges, cooldown) : before;

                if (!after.equals(before) || !held.hasChargesFor(type))
                {
                    held.setCharges(type, after);
                }

                if (after.charges() != before.charges() || !held.hasChargesFor(type))
                {
                    chargesChanged = true;
                }
            }

            // nothing is selected yet, or what was selected is gone - fall to the first thing owned
            if (held.selectedAbility().isEmpty() || !owned.contains(held.selectedAbility()))
            {
                held.selectAbility(owned.get(0));
                chargesChanged = true;
            }

            if (chargesChanged)
            {
                sync(player);
            }
        });
    }

    /**
     * A player asked to fire their selected ability. Every check that matters happens here, in
     * order of cost: the rate limit first, then ownership, then the bank, then the ability's own
     * verdict on whether it could actually do anything.
     */
    public static void use(ServerPlayer player, String requestedType)
    {
        if (!accept(player))
        {
            return;
        }

        player.getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(held ->
        {
            // the client names what it thinks it is firing rather than relying on the server's idea
            // of the selection, so a press cannot land on a different ability than the one the
            // player saw in the HUD. It is still only a request: ownership is checked below.
            final String type = requestedType == null || requestedType.isEmpty()
                    ? held.selectedAbility()
                    : requestedType;

            if (type.isEmpty() || !ownedBy(player).contains(type))
            {
                // either a crafted packet, or a room demolished between the press and its arrival.
                // Both are the same answer, and neither is worth a log line on a busy server.
                return;
            }

            if (!(SoulBuffEffects.get(type) instanceof SoulActiveEffect effect))
            {
                return;
            }

            final double magnitude = SoulBuffs.magnitude(player, type);
            final int maxCharges = maxChargesOf(effect, magnitude);
            final int cooldown = cooldownOf(effect, magnitude);
            final AbilityCharges charges = held.chargesOf(type);

            if (!charges.canSpend())
            {
                return;
            }

            final boolean fired;

            try
            {
                fired = effect.activate(player, magnitude);
            }
            catch (RuntimeException e)
            {
                // a datapack-registered ability throwing must not take the player's tick with it
                LogHelper.error("Soul ability " + type + " threw while firing: " + e);
                return;
            }

            if (!fired)
            {
                // the ability refused - no mount to call, nowhere safe to land. It has told the
                // player why; charging them for it would be the mod taking payment for nothing.
                return;
            }

            held.setCharges(type, charges.spend(maxCharges, cooldown));
            sync(player);
        });
    }

    /**
     * Move to the next ability the player owns, wrapping in either direction. A player with fewer
     * than two owns nothing to cycle between and this does nothing.
     */
    public static void cycle(ServerPlayer player, boolean forward)
    {
        if (!accept(player))
        {
            return;
        }

        List<String> owned = ownedBy(player);

        if (owned.size() < 2)
        {
            return;
        }

        player.getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(held ->
        {
            final int current = owned.indexOf(held.selectedAbility());
            final int step = forward ? 1 : -1;

            // indexOf returns -1 when nothing is selected; the +size keeps the modulus positive so
            // a backward cycle from an unselected state lands on the last ability rather than
            // throwing
            final int next = Math.floorMod(current + step, owned.size());

            held.selectAbility(owned.get(next));
            sync(player);
        });
    }

    /** Empties every bank, per #87 - charges do not survive death. */
    public static void onDeath(ServerPlayer player)
    {
        if (player == null || player instanceof FakePlayer)
        {
            return;
        }

        player.getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(held ->
        {
            held.resetOnDeath();
            sync(player);
        });
    }

    /** Push this player's ability state to their client. */
    public static void sync(ServerPlayer player)
    {
        if (player == null || player instanceof FakePlayer)
        {
            return;
        }

        player.getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(held ->
        {
            Map<String, SyncSoulAbilitiesMessage.State> states = new LinkedHashMap<>();

            for (String type : ownedBy(player))
            {
                if (!(SoulBuffEffects.get(type) instanceof SoulActiveEffect effect))
                {
                    continue;
                }

                final double magnitude = SoulBuffs.magnitude(player, type);
                final AbilityCharges charges = held.chargesOf(type);

                states.put(type, new SyncSoulAbilitiesMessage.State(
                        charges.charges(),
                        charges.ticksToNextCharge(),
                        maxChargesOf(effect, magnitude),
                        cooldownOf(effect, magnitude)));
            }

            Network.sendTo(new SyncSoulAbilitiesMessage(held.selectedAbility(), states), player);
        });
    }

    /** Forgets a player's rate-limit record. Called on logout so the map does not grow forever. */
    public static void forget(ServerPlayer player)
    {
        if (player != null)
        {
            LAST_REQUEST.remove(player.getUUID());
        }
    }

    /**
     * The rate limit, and the cheap guards ahead of it. Deliberately the first thing both entry
     * points call: a client sending sixty presses a second should cost this server a map lookup,
     * not a capability read and a config snapshot.
     */
    private static boolean accept(ServerPlayer player)
    {
        if (player == null || player instanceof FakePlayer || !settings().enabled())
        {
            return false;
        }

        final long now = player.server.getTickCount();
        final Long last = LAST_REQUEST.get(player.getUUID());

        if (last != null && now - last < REQUEST_INTERVAL_TICKS)
        {
            return false;
        }

        LAST_REQUEST.put(player.getUUID(), now);
        return true;
    }

    private static int maxChargesOf(SoulActiveEffect effect, double magnitude)
    {
        return settings().effectiveCharges(effect.chargesFor(magnitude));
    }

    private static int cooldownOf(SoulActiveEffect effect, double magnitude)
    {
        return settings().effectiveCooldown(effect.rechargeTicksFor(magnitude));
    }

    private static ActiveAbilitySettings settings()
    {
        return SoulHomeConfig.activeAbilitySettings();
    }
}
