/*
 * File created ~ 22 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.sound.SoulSounds;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.structures.core.SoulFeedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Music room: a chord that carries everyone within earshot, and you either way (#307).
 *
 * <p>Regeneration and Speed for a few seconds, to the player and every player near them. Music is
 * the one thing in a soul that is plainly meant to be shared, so this is the issue's first option -
 * an active that lands on allies - rather than a passive aura, which would be a second potion-
 * duration buff beside the alchemy lab's, or a modifier on Rally, which would tie two rooms
 * together in Java when bonds already exist to do that in data.
 *
 * <p>It follows {@link RallyEffect} where the questions are the same, and for the same reasons:
 * the caster always gets it, so a room built single-player is not a room that does nothing, and
 * what is shared is a potion effect rather than a soul buff, so nothing borrowed ever has to be
 * unpicked from a recipient's own capability. Where Rally steadies a fight, this carries a group
 * between them - healing and pace rather than strength and armour - so the two rooms do not
 * compete for the same slot.
 */
public class ChorusEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.CHORUS;

    /** 7 blocks at tier 1, 11 at the top - a chord carries further than a shout. */
    private static final double BASE_RADIUS = 5d;
    private static final double RADIUS_PER_MAGNITUDE = 1d;

    /** 12 seconds at tier 1, 20 at the top. */
    private static final int BASE_DURATION_TICKS = 160;
    private static final int DURATION_PER_MAGNITUDE = 40;

    /** 120 seconds at tier 1, 90 at the top. */
    private static final int BASE_RECHARGE_TICKS = 2700;
    private static final int RECHARGE_SAVED_PER_MAGNITUDE = 150;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Chorus carries, how long it lasts, and how quickly it is ready again";
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

    static double radiusFor(double magnitude)
    {
        return BASE_RADIUS + Math.max(0d, magnitude) * RADIUS_PER_MAGNITUDE;
    }

    static int durationFor(double magnitude)
    {
        return BASE_DURATION_TICKS + (int) Math.round(Math.max(0d, magnitude) * DURATION_PER_MAGNITUDE);
    }

    /**
     * Regeneration I throughout, never II: Regeneration II on a ninety-second cooldown for a
     * whole party would out-heal the purifying font, which is a room built for exactly that.
     */
    static final int REGENERATION_AMPLIFIER = 0;

    /** Speed I below the top tier and Speed II at it - the one number the top of the room buys. */
    static int speedAmplifier(double magnitude)
    {
        return magnitude >= 6d ? 1 : 0;
    }

    @Override
    public boolean activate(ServerPlayer player, double magnitude)
    {
        final ServerLevel level = player.serverLevel();
        final double radius = radiusFor(magnitude);
        final int duration = durationFor(magnitude);

        applyTo(player, duration, magnitude);

        int carried = 0;

        for (Player nearby : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(radius)))
        {
            if (nearby == player || nearby.isSpectator())
            {
                continue;
            }

            applyTo(nearby, duration, magnitude);
            carried++;
        }

        // a chord rather than one note: the same instrument at the root, the fifth and the octave,
        // so it reads as the room's music and not as a stray note block somewhere nearby. The note
        // block sounds are Holder.References in this version, like RAID_HORN in RallyEffect
        for (float pitch : new float[]{1.0f, 1.498f, 2.0f})
        {
            SoulSounds.playFeedback(
                    level, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS,
                    0.6f, pitch, SoulFeedback.ABILITY);
        }

        player.displayClientMessage(
                carried == 0
                        ? Component.translatable(Constants.StringKeys.ABILITY_CHORUS_ALONE)
                        : Component.translatable(Constants.StringKeys.ABILITY_CHORUS_SHARED, carried),
                true);

        return true;
    }

    private void applyTo(Player target, int duration, double magnitude)
    {
        // visible, like Rally's: an effect a friend cannot see they were given is one they do not
        // know to thank you for
        target.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, duration, REGENERATION_AMPLIFIER, false, true, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, duration, speedAmplifier(magnitude), false, true, true));
    }
}
