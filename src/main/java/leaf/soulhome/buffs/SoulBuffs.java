/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs;

import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncSoulBuffsMessage;
import leaf.soulhome.structures.core.SoulBuffSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The one way an effect asks "what is this player's magnitude for buff X".
 *
 * <p>Every guard rail lives here rather than being repeated - and eventually forgotten - in each
 * effect:
 *
 * <ul>
 *   <li>a null or fake player is always zero. Fake players are machines: an autocrafter should not
 *       inherit the sword damage of whoever placed it</li>
 *   <li>the magnitude is clamped to that buff type's configured ceiling on the way out, so
 *       neither a datapack nor a value saved under a more generous config can hand an effect a
 *       number it was never designed to receive</li>
 *   <li>everything is zero while the feature is switched off in the config</li>
 *   <li>never negative, and never null</li>
 * </ul>
 */
public final class SoulBuffs
{
    private SoulBuffs()
    {
    }

    public static SoulBuffSet of(Player player)
    {
        if (player == null || player instanceof FakePlayer)
        {
            return SoulBuffSet.empty();
        }

        return player.getCapability(SoulBuffsProvider.CAPABILITY)
                .map(PlayerSoulBuffs::get)
                .orElse(SoulBuffSet.empty());
    }

    /**
     * This player's magnitude for a buff type, clamped and guarded. Zero means "no buff", which is
     * the case every effect should handle first and cheapest.
     */
    public static double magnitude(Player player, String buffType)
    {
        if (!SoulHomeConfig.enabled())
        {
            return 0d;
        }

        final double raw = of(player).magnitude(buffType);

        if (raw <= 0d)
        {
            return 0d;
        }

        return Math.min(raw, SoulHomeConfig.buffSettings().capFor(buffType));
    }

    public static boolean has(Player player, String buffType)
    {
        return magnitude(player, buffType) > 0d;
    }

    /**
     * Replace a player's buffs and tell their client, if anything actually changed.
     *
     * <p>Server-authoritative: the client copy exists so the interface can render without a
     * round-trip, and is never what an effect reads.
     */
    public static void set(ServerPlayer player, SoulBuffSet buffs)
    {
        if (player == null || player instanceof FakePlayer)
        {
            return;
        }

        player.getCapability(SoulBuffsProvider.CAPABILITY).ifPresent(held ->
        {
            if (held.set(buffs))
            {
                sync(player);
            }
        });
    }

    /** Push the player's current buffs to their client unconditionally. */
    public static void sync(ServerPlayer player)
    {
        if (player == null || player instanceof FakePlayer)
        {
            return;
        }

        Network.sendTo(new SyncSoulBuffsMessage(of(player)), player);
    }
}
