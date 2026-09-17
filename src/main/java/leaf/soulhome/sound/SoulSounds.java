/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.sound;

import leaf.soulhome.network.AmbienceHoldMessage;
import leaf.soulhome.network.Network;
import leaf.soulhome.structures.core.SoulFeedback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The one door every sound this mod plays for its own sake goes through (#212).
 *
 * <p>Playing the sound is the smaller half of what this does. The larger half is telling everyone
 * near enough to hear it that the soul's ambience should get out of the way for a moment, so that
 * #166's rule - "nothing here may make it harder to hear the ascension hum, the ability HUD
 * feedback, or a soul key" - is something the code keeps rather than something the volume happens
 * to keep on its behalf.
 *
 * <h2>Why the mark is sent rather than sniffed</h2>
 *
 * <p>A client-side {@code PlaySoundEvent} hook was the obvious design and does not survive contact
 * with the palette: every sound this mod plays is a vanilla event with a meaning of its own, so
 * ducking on {@code BEACON_AMBIENT} ducks for a real beacon and ducking on {@code ANVIL_LAND} ducks
 * for an anvil. Widening it to "any {@code SoundSource.PLAYERS} sound inside a soul" catches
 * footsteps and every block placed, which is exactly what the owner of #212 asked never to happen -
 * and a soul dimension is a place whose entire purpose is placing blocks.
 *
 * <p>Here the knowledge is already in hand: this method is only ever called from a call site that
 * knows it is playing this mod's own audio and what kind it is. A sound nobody routed through here
 * holds nothing, which makes footsteps and block-placing correct by construction instead of by a
 * filter somebody has to keep correct.
 *
 * <h2>What it costs</h2>
 *
 * <p>One small packet per moment - an ability firing, a key turning, the two ends of a ritual - to
 * the players already close enough to be sent the sound itself, and nothing whatsoever the rest of
 * the time. Nothing here is scheduled and nothing is stored.
 */
public final class SoulSounds
{
    /**
     * How far a hold reaches, in blocks. Generously past vanilla's own 16-block attenuation radius
     * for these events: hearing a hum faintly and having the ambience talk over it is the fault,
     * and a hold costs a player who could not hear it anyway nothing at all.
     */
    private static final int HOLD_RADIUS = 48;

    private SoulSounds()
    {
    }

    /**
     * Play one of this mod's own sounds, and hold the ambience off it for the kind's own length.
     *
     * <p>A drop-in for {@code level.playSound(null, pos, ...)} at every call site that is this mod
     * speaking rather than the world doing something.
     */
    public static void playFeedback(
            ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source,
            float volume, float pitch, SoulFeedback kind)
    {
        playFeedback(level, pos, sound, source, volume, pitch, kind, kind.defaultHoldTicks());
    }

    /**
     * The same, for a caller that knows how long its own moment lasts - the ascension ritual, whose
     * length is a server config value and so is not something {@link SoulFeedback} could carry.
     */
    public static void playFeedback(
            ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source,
            float volume, float pitch, SoulFeedback kind, int holdTicks)
    {
        if (level == null)
        {
            return;
        }

        level.playSound(null, pos, sound, source, volume, pitch);
        hold(level, pos, kind, holdTicks);
    }

    /**
     * Hold the ambience off without playing anything - for a moment that is this mod's but whose
     * audio is somebody else's, like a soul key's own teleport.
     */
    public static void hold(ServerLevel level, BlockPos pos, SoulFeedback kind, int holdTicks)
    {
        if (level == null || pos == null || holdTicks <= 0)
        {
            return;
        }

        Network.sendToAllAround(AmbienceHoldMessage.of(kind, holdTicks), level.dimension(), pos, HOLD_RADIUS);
    }

    /** The same, for a moment that belongs to one player rather than to a place. */
    public static void hold(ServerPlayer player, SoulFeedback kind, int holdTicks)
    {
        if (player == null || holdTicks <= 0)
        {
            return;
        }

        Network.sendTo(AmbienceHoldMessage.of(kind, holdTicks), player);
    }
}
