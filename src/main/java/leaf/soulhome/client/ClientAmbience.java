/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.network.SyncSoulAmbienceMessage;
import leaf.soulhome.structures.core.AmbienceSettings;
import leaf.soulhome.structures.core.SoulAmbience;
import leaf.soulhome.structures.core.SoulCharacter;
import leaf.soulhome.structures.core.SoulFeedback;
import leaf.soulhome.structures.core.SoulVoice;
import net.minecraft.client.Minecraft;

import java.util.EnumMap;
import java.util.Map;

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
 *
 * <h2>The duck</h2>
 *
 * <p>This also holds the ambience off this mod's own audio (#212): {@link #held()} is what stops a
 * one-shot landing on top of the ascension hum, and {@link #duckLevel()} is what the ambient bed is
 * multiplied by, which is why {@link #bedClose()} and {@link #bedOpen()} hand back a ducked level
 * rather than a raw one. The hold and the duck share one notion of when something of ours is
 * playing, deliberately: that is the half of #212 that goes wrong silently if they ever drift.
 *
 * <p>Both halves ease. {@code SoulAmbience.duckLevel} steps down the moment a hold begins and ramps
 * back over two seconds; the easing here is what turns that step into a movement, so a duck is
 * never a click.
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

    /**
     * The same for the duck (#212), and the fastest of the three. A duck has to be out of the way
     * before the sound it is making room for is over, but a duck that snaps is a click - so it is
     * quick rather than instant, and it is the reason {@code SoulAmbience.duckLevel}'s own step is
     * safe to be a step.
     */
    private static final float DUCK_EASE = 0.2f;

    /**
     * And the bed (#210), slowest of all: about five seconds to settle. Arriving in a soul fades it
     * up rather than starting it at level, and walking out fades it away. A rank that lands mid-
     * build slides the mix from the close layer to the open one over long enough that what a player
     * notices is the room having grown, not the sound having changed.
     */
    private static final float BED_EASE = 0.006f;

    private static String dimension = "";
    private static boolean active;
    private static boolean tinted;

    private static int holdTicksRemaining;
    private static int ticksSinceHoldEnded = SoulAmbience.DUCK_RECOVERY_TICKS;
    private static float duckLevel = 1f;

    private static float red = SoulAmbience.NEUTRAL[0];
    private static float green = SoulAmbience.NEUTRAL[1];
    private static float blue = SoulAmbience.NEUTRAL[2];
    private static float fogNear = SoulAmbience.NO_FOG_OVERRIDE;
    private static float fogFar = SoulAmbience.NO_FOG_OVERRIDE;
    private static float moteRate;

    private static float bedClose;
    private static float bedOpen;

    private static final Map<SoulVoice, Float> characterBed = zeroedCharacterBed();

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
            // no level means no sound engine to fade anything out of, so the bed goes with it
            bedClose = 0f;
            bedOpen = 0f;
            zeroCharacterBed();
            return;
        }

        final String current = minecraft.level.dimension().location().toString();
        final SyncSoulAmbienceMessage soul = SyncSoulAmbienceMessage.ClientSoulAmbience.forDimension(current);

        if (!SyncSoulAmbienceMessage.ClientSoulAmbience.isKnown(soul))
        {
            // not a soul, or one this client has not been told about: leave the world's own sky be
            clear();

            // the bed is the one thing that does not simply stop here. Walking out of a soulhome
            // should fade it away over a couple of seconds rather than cutting it off at the door,
            // and a fade needs the level to survive the moment the dimension stops being a soul.
            bedClose = fadeOut(bedClose);
            bedOpen = fadeOut(bedOpen);
            fadeOutCharacterBed();
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

        tickDuck();

        final SoulAmbience.BedMix rankBed = SoulAmbience.bedMix(soul.getRank(), soul.getMaxRank(), settings);

        tickBed(rankBed, settings);
        tickCharacterBed(SoulAmbience.characterBedMix(blend, rankBed.total(), settings), settings);

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

    /**
     * Something of this mod's own is playing (#212): hold the one-shots and duck the bed for this
     * long. Called off {@code AmbienceHoldMessage}, which is sent from this mod's own sound call
     * sites - see {@code SoulSounds} for why it is not sniffed off the sound engine instead.
     *
     * <p>Holds extend rather than replace: a ritual's own long hold is not cut short by an ability
     * fired during it.
     */
    public static void hold(SoulFeedback kind, int ticks)
    {
        if (kind == null || ticks <= 0)
        {
            return;
        }

        holdTicksRemaining = Math.max(holdTicksRemaining, ticks);
        ticksSinceHoldEnded = 0;
    }

    /** Whether a one-shot would land on top of something a player needs to hear. */
    public static boolean held()
    {
        return holdTicksRemaining > 0;
    }

    /**
     * What the ambient bed may be worth right now, 1 for undisturbed. Eased, so the entry into a
     * duck and the recovery out of it are both movements rather than jumps.
     */
    public static float duckLevel()
    {
        return duckLevel;
    }

    /**
     * The ambient bed's close layer, eased and already ducked (#210/#212).
     *
     * <p>The duck is applied here rather than at the sound instance so that there is one answer to
     * "how loud is the bed" and the hold and the duck cannot drift apart - which is the half of
     * #212 that goes wrong silently.
     */
    public static float bedClose()
    {
        return bedClose * duckLevel;
    }

    /** The open layer, the same way. */
    public static float bedOpen()
    {
        return bedOpen * duckLevel;
    }

    /**
     * One character layer of the bed (#214), eased and already ducked - the same contract as
     * {@link #bedClose()} and {@link #bedOpen()}, one voice at a time.
     */
    public static float characterBed(SoulVoice voice)
    {
        return characterBed.getOrDefault(voice, 0f) * duckLevel;
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

    private static void tickDuck()
    {
        if (holdTicksRemaining > 0)
        {
            holdTicksRemaining--;
            ticksSinceHoldEnded = 0;
        }
        else if (ticksSinceHoldEnded < SoulAmbience.DUCK_RECOVERY_TICKS)
        {
            ticksSinceHoldEnded++;
        }

        duckLevel = ease(duckLevel, SoulAmbience.duckLevel(holdTicksRemaining, ticksSinceHoldEnded), DUCK_EASE);
    }

    /**
     * Move the bed toward what this soul is worth - or cut it, if the reason it is worth nothing is
     * that somebody switched it off.
     *
     * <p>The same split {@code tick} makes for fog, and for the same reason: a switch being turned
     * off is a config change rather than something happening in the world, and easing it would
     * leave a player who asked for silence listening to a bed fade out over five seconds. Everything
     * that <i>is</i> something happening in the world - an ascension, arriving, leaving - eases.
     */
    private static void tickBed(SoulAmbience.BedMix target, AmbienceSettings settings)
    {
        if (!settings.soundActive())
        {
            bedClose = 0f;
            bedOpen = 0f;
            return;
        }

        bedClose = ease(bedClose, target.close(), BED_EASE);
        bedOpen = ease(bedOpen, target.open(), BED_EASE);
    }

    /** The character half of the bed (#214), eased the same way and on the same switch as the rest. */
    private static void tickCharacterBed(SoulAmbience.CharacterBedMix target, AmbienceSettings settings)
    {
        if (!settings.soundActive())
        {
            zeroCharacterBed();
            return;
        }

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            characterBed.put(voice, ease(characterBed.get(voice), target.level(voice), BED_EASE));
        }
    }

    private static void fadeOutCharacterBed()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice == SoulVoice.BASE)
            {
                continue;
            }

            characterBed.put(voice, fadeOut(characterBed.get(voice)));
        }
    }

    private static void zeroCharacterBed()
    {
        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice != SoulVoice.BASE)
            {
                characterBed.put(voice, 0f);
            }
        }
    }

    private static Map<SoulVoice, Float> zeroedCharacterBed()
    {
        final Map<SoulVoice, Float> levels = new EnumMap<>(SoulVoice.class);

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice != SoulVoice.BASE)
            {
                levels.put(voice, 0f);
            }
        }

        return levels;
    }

    /**
     * A layer on its way out, snapped once it is inaudible.
     *
     * <p>Easing alone only ever approaches zero, and a bed that never quite reaches it would hold a
     * streaming sound channel open for the rest of the session over a level nobody can hear.
     */
    private static float fadeOut(float level)
    {
        final float faded = ease(level, 0f, BED_EASE * 4f);

        return faded <= SoulAmbience.BED_SILENCE ? 0f : faded;
    }

    private static void clear()
    {
        dimension = "";
        active = false;
        tinted = false;
        holdTicksRemaining = 0;
        ticksSinceHoldEnded = SoulAmbience.DUCK_RECOVERY_TICKS;
        duckLevel = 1f;
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
