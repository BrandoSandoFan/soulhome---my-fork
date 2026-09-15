/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.structures.core.AmbienceSettings;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulCharacter;
import net.minecraft.client.Minecraft;

/**
 * The client's own copy of what the soul around it looks like, eased rather than applied (#163).
 *
 * <p>{@link SoulAmbience} says where the place should be; this says where it is. Everything moves
 * toward its target by a fixed share per tick, so a hearth finished on the far side of the island
 * warms the sky over a few seconds and an ascension opens it over several more. That is #165's
 * "interpolate toward the target rather than jumping to it", and it is also the whole of #167's
 * no-flashing rule: nothing here can move faster than the easing lets it, whatever arrives on the
 * wire and however often.
 *
 * <p>The one place it does jump is a change of dimension, where there is no motion to be smooth
 * about - a player stepping out of their soul and into the overworld should not watch their soul's
 * colour drain out of the overworld's sky.
 */
public final class ClientAmbience
{
    /**
     * Share of the remaining distance the colour closes per tick. About three seconds to settle,
     * which is slow enough to read as the place changing rather than as a light being switched.
     */
    private static final float COLOUR_EASE = 0.02f;

    /**
     * The same for the fog distance, slower still. This is what an ascension is felt through: the
     * rank arrives in one packet the instant the ritual completes, and the sky then takes the best
     * part of ten seconds to open. The ritual is thirty seconds long and this is its last beat.
     */
    private static final float FOG_EASE = 0.008f;

    private static String dimension = "";
    private static boolean active;
    private static boolean tinted;

    private static float red = SoulAmbience.NEUTRAL[0];
    private static float green = SoulAmbience.NEUTRAL[1];
    private static float blue = SoulAmbience.NEUTRAL[2];
    private static float fogNear = SoulAmbience.NO_FOG_OVERRIDE;
    private static float fogFar = SoulAmbience.NO_FOG_OVERRIDE;
    private static float moteRate;

    private static SoulCharacter character = SoulCharacter.EMPTY;

    private ClientAmbience()
    {
    }

    /** Advance one tick toward whatever the soul around us is currently supposed to look like. */
    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            clear();
            return;
        }

        final String current = minecraft.level.dimension().location().toString();
        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience.forDimension(current);

        if (!SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul))
        {
            // not a soul, or one this client has not been told about: leave the world's own sky be
            clear();
            return;
        }

        final AmbienceSettings settings = SoulHomeClientConfig.ambience();
        final SoulCharacter blend = soul.character();
        final SoulAmbience target = SoulAmbience.of(
                blend, soul.getRank(), soul.getMaxRank(), soul.getVergeHalfExtentOrLegacy(), settings);

        final boolean arrived = !current.equals(dimension);

        dimension = current;
        character = blend;
        active = settings.active();
        tinted = target.tinted();

        if (arrived)
        {
            red = target.red();
            green = target.green();
            blue = target.blue();
            fogNear = target.fogNear();
            fogFar = target.fogFar();
            moteRate = target.moteRate();
            return;
        }

        red = ease(red, target.red(), COLOUR_EASE);
        green = ease(green, target.green(), COLOUR_EASE);
        blue = ease(blue, target.blue(), COLOUR_EASE);
        moteRate = ease(moteRate, target.moteRate(), COLOUR_EASE);

        // a switch being turned off is a config change rather than something happening in the
        // world, and easing it would leave a player who asked for no fog looking at fog
        if (target.hasFog() == (fogFar > 0f))
        {
            fogNear = ease(fogNear, target.fogNear(), FOG_EASE);
            fogFar = ease(fogFar, target.fogFar(), FOG_EASE);
        }
        else
        {
            fogNear = target.fogNear();
            fogFar = target.fogFar();
        }
    }

    /** Whether anything at all should be drawn or played right now. */
    public static boolean active()
    {
        return active;
    }

    /** Whether the fog colour differs from the one this dimension has always had. */
    public static boolean tinted()
    {
        return active && tinted;
    }

    public static boolean hasFog()
    {
        return active && fogFar > 0f;
    }

    public static float red()
    {
        return red;
    }

    public static float green()
    {
        return green;
    }

    public static float blue()
    {
        return blue;
    }

    public static float fogNear()
    {
        return fogNear;
    }

    public static float fogFar()
    {
        return fogFar;
    }

    /** Firmament motes to try for this tick - see {@code SoulFirmamentMotes}. */
    public static float moteRate()
    {
        return active ? moteRate : 0f;
    }

    /** The blend the soul around us is made of, for whatever wants to read it. */
    public static SoulCharacter character()
    {
        return character;
    }

    private static void clear()
    {
        dimension = "";
        active = false;
        tinted = false;
        moteRate = 0f;
        fogNear = SoulAmbience.NO_FOG_OVERRIDE;
        fogFar = SoulAmbience.NO_FOG_OVERRIDE;
        character = SoulCharacter.EMPTY;
        red = SoulAmbience.NEUTRAL[0];
        green = SoulAmbience.NEUTRAL[1];
        blue = SoulAmbience.NEUTRAL[2];
    }

    private static float ease(float from, float to, float rate)
    {
        return from + (to - from) * rate;
    }
}
