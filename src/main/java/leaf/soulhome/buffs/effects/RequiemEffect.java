/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Ossuary: having stood vigil over the dead, dying is a little less sudden.
 *
 * <p>A different shape of "survive a hit" to {@link AegisEffect}'s bank of absorption - this is
 * spent instead of banked, healing on the spot and steadying what is left rather than pre-loading
 * a pool to be chipped away at later. A player presses it because a fight has already gone wrong,
 * not in anticipation of one, which is why it costs a charge outright rather than topping up: there
 * is no "still had some banked" case to reconcile against.
 *
 * <p>Heal and Resistance both, because a heal alone can be immediately undone by the next hit that
 * was already inbound - Resistance is what actually buys the room to make the heal count.
 */
public class RequiemEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.REQUIEM;

    /** 3 hearts at tier 1. */
    private static final double BASE_HEAL = 4d;
    private static final double HEAL_PER_MAGNITUDE = 1d;

    /** 4 seconds at tier 1. */
    private static final int BASE_DURATION_TICKS = 60;
    private static final int DURATION_PER_MAGNITUDE = 15;

    /** 100 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 2000;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 130;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "health restored on the spot, and how long the steadying Resistance holds";
    }

    @Override
    public int chargesFor(double magnitude)
    {
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
        if (magnitude <= 0d)
        {
            return false;
        }

        final float heal = (float) (BASE_HEAL + magnitude * HEAL_PER_MAGNITUDE);
        final int duration = BASE_DURATION_TICKS + (int) Math.round(magnitude * DURATION_PER_MAGNITUDE);

        // amplifier 0 throughout - Requiem is meant to buy a breath, not to make a player briefly
        // unkillable the way a higher Resistance level would
        player.heal(heal);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, true, true));

        player.serverLevel().playSound(
                null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 0.8f);

        return true;
    }
}
