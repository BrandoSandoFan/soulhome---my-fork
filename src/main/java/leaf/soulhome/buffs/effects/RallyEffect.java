/*
 * File created ~ 3 - 9 - 2026
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
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Mead hall: a shout that steadies everyone within earshot, and you either way (#91).
 *
 * <p><b>The solo problem is addressed rather than ignored.</b> A room that does nothing
 * single-player is a room most players will never build, so Strength and Resistance land on the
 * caster whether or not anyone is nearby; the group is what makes it worth having built the hall
 * rather than what makes it work at all. Single-player it is a combat cooldown; multiplayer it is a
 * reason to be the one with the long table.
 *
 * <p><b>What is shared is the potion effects, not the soul buffs themselves.</b> #91 describes
 * sharing a fraction of your own buffs, and this deliberately does not: a shared soul buff would
 * have to be written into the recipient's capability, which is the same store their own rooms feed,
 * and unpicking "yours" from "borrowed" on every scan, relog and death is a large amount of
 * fragile bookkeeping for an effect a player experiences as "we all got stronger for a bit".
 * Strength and Resistance scaled by the caster's tier say the same thing and cannot leak. The
 * shared portion also cannot be re-shared, because there is nothing on the recipient to re-share -
 * which is #91's own acceptance criterion, met by construction rather than by a guard.
 */
public class RallyEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.RALLY;

    /** 6 blocks at tier 1. */
    private static final double BASE_RADIUS = 4d;
    private static final double RADIUS_PER_MAGNITUDE = 1d;

    /** 15 seconds at tier 1. */
    private static final int BASE_DURATION_TICKS = 200;
    private static final int DURATION_PER_MAGNITUDE = 50;

    /** 150 seconds at tier 1. */
    private static final int BASE_RECHARGE_TICKS = 3000;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 200;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Rally carries, how long it holds, and how strongly";
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

        // amplifier 0 at tier 1, 1 at the top - a shout that reaches Strength III would be the
        // hall out-hitting the armoury, which is not what this room is for
        final int amplifier = (int) Math.floor(Math.max(0d, magnitude - 1d) / 3d);

        applyTo(player, duration, amplifier);

        int rallied = 0;

        for (Player nearby : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(radius)))
        {
            if (nearby == player || nearby.isSpectator())
            {
                continue;
            }

            applyTo(nearby, duration, amplifier);
            rallied++;
        }

        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN, SoundSource.PLAYERS, 0.7f, 1.0f);

        player.displayClientMessage(
                rallied == 0
                        ? Component.translatable(Constants.StringKeys.ABILITY_RALLY_ALONE)
                        : Component.translatable(Constants.StringKeys.ABILITY_RALLY_SHARED, rallied),
                true);

        return true;
    }

    private void applyTo(Player target, int duration, int amplifier)
    {
        // shown in the HUD and ambient-particled, unlike most of this mod's effects: a rally that
        // nobody can see they received is a rally nobody knows to fight alongside
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, amplifier, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, amplifier, false, true, true));
    }
}
