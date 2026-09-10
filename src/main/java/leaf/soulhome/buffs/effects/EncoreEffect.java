/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Conservatory: a run worth playing carries past the stage it was played on.
 *
 * <p>The same solo problem {@link RallyEffect} names, answered the same way: Speed and Haste land
 * on the performer whether or not anyone is listening, so a Conservatory built alone is still a
 * combat cooldown rather than a room with nobody to use it on. What is shared is the potion
 * effects and not the soul buffs themselves, for the exact reason {@link RallyEffect}'s own
 * javadoc gives - there is nothing on a recipient to re-share, which meets #91's own acceptance
 * criterion by construction.
 *
 * <p>Speed and Haste rather than Rally's Strength and Resistance, because a performance energises
 * rather than steadies - the two rooms grant the same shape of thing for a different reason to
 * have built one.
 */
public class EncoreEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.ENCORE;

    /** 5 blocks at tier 1. */
    private static final double BASE_RADIUS = 4d;
    private static final double RADIUS_PER_MAGNITUDE = 0.5d;

    /** 12 seconds at tier 1. */
    private static final int BASE_DURATION_TICKS = 160;
    private static final int DURATION_PER_MAGNITUDE = 40;

    /** 120 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 2400;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 160;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Encore carries, how long it holds, and how strongly";
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
        final ServerLevel level = player.serverLevel();
        final double radius = BASE_RADIUS + magnitude * RADIUS_PER_MAGNITUDE;
        final int duration = BASE_DURATION_TICKS + (int) Math.round(magnitude * DURATION_PER_MAGNITUDE);

        // amplifier 0 at tier 1, 1 past magnitude 3 - a performance running Haste II is still an
        // encore, not the workshop's own reach out-fitting it
        final int amplifier = (int) Math.floor(Math.max(0d, magnitude - 1d) / 3d);

        applyTo(player, duration, amplifier);

        int carried = 0;

        for (Player nearby : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(radius)))
        {
            if (nearby == player || nearby.isSpectator())
            {
                continue;
            }

            applyTo(nearby, duration, amplifier);
            carried++;
        }

        level.playSound(
                null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);

        player.displayClientMessage(
                carried == 0
                        ? Component.translatable(Constants.StringKeys.ABILITY_ENCORE_ALONE)
                        : Component.translatable(Constants.StringKeys.ABILITY_ENCORE_SHARED, carried),
                true);

        return true;
    }

    private void applyTo(Player target, int duration, int amplifier)
    {
        // shown in the HUD and ambient-particled, unlike most of this mod's effects: an encore
        // nobody can see they are part of is one nobody knows to keep moving with
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, amplifier, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, amplifier, false, true, true));
    }
}
