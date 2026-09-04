/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Bulwark: a bank of absorption, pressed for when a hit is coming (#89).
 *
 * <p>The least controversial ability in the epic to amplify. More absorption is always worth
 * having, never unwieldy, and never trivial, because it is consumed the moment it is used - there
 * is no amount of it that turns into "I can ignore combat", only "I survive one more hit".
 *
 * <p><b>Vanilla absorption, not a bespoke pool.</b> Absorption already renders as its own row of
 * hearts, is spent before real health, does not regenerate on its own, and is understood by every
 * player who has drunk a potion. A second damage-absorbing pool with the same behaviour and its own
 * HUD would be worse in every way that matters.
 *
 * <p>Pressing it while it is still up tops the bank back to full and spends the charge, per #89.
 * That is deliberately not a stacking mechanic: the bank is set to this room's ceiling rather than
 * added to it, so no amount of pressing gets a player past it.
 *
 * <p>It tops up rather than overwriting, though. A player who ate a golden apple on the way into a
 * fight is carrying four absorption hearts from it, and an Aegis that banks fewer than that would
 * otherwise take some of them away - an ability that costs a charge to make you weaker.
 */
public class AegisEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.AEGIS;

    /** 120 seconds at tier 1, shortening as the room improves. */
    private static final int BASE_RECHARGE_TICKS = 2400;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 120;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "absorption points Aegis banks, and how fast it comes back";
    }

    @Override
    public int chargesFor(double magnitude)
    {
        // one charge, always. The magnitude goes into how much it absorbs, not into how many times
        // it can be pressed - a bank you can refill twice in a row is a bank twice the size, and
        // #89 puts the growth in the size
        return 1;
    }

    @Override
    public int rechargeTicksFor(double magnitude)
    {
        return BASE_RECHARGE_TICKS - (int) Math.round(magnitude * RECHARGE_SAVED_PER_MAGNITUDE);
    }

    @Override
    public boolean activate(ServerPlayer player, double magnitude)
    {
        final float banked = (float) Math.max(0d, magnitude);

        if (banked <= 0f)
        {
            return false;
        }

        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), banked));

        player.serverLevel().playSound(
                null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.6f);

        return true;
    }
}
