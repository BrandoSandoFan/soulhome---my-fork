/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.structures.core.SoulVoice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The sound events the soul speaks with (#209).
 *
 * <p>Until this existed, every ambient one-shot was drawn from the vanilla palette, and that was
 * wrong twice over. Half the table was the sound of a block being placed or broken - {@code
 * MOSS_PLACE}, {@code DEEPSLATE_PLACE}, {@code CHAIN_PLACE} - in a dimension whose entire purpose is
 * placing blocks, so a player hearing deepslate go down behind them would turn round to see who did
 * it. That is an event, and the ambience is meant to be a place. The other half were cues with
 * meanings of their own: {@code PORTAL_AMBIENT} says a portal is near, {@code BEACON_AMBIENT} is the
 * sound #114 gave the ascension ritual, so the base voice played the ritual's own hum at random.
 *
 * <p>The second cost was quieter and worse: a resource pack could not replace "the soul's warm
 * sound" without replacing every campfire in the game. Own events fix that for nothing.
 *
 * <h2>One event per voice, by name</h2>
 *
 * <p>Every {@link SoulVoice} gets {@code soulhome:ambience.voice.<name>} derived from the enum
 * constant rather than listed out here, so a voice added to the enum is a voice registered here with
 * no second edit - and {@code AmbienceAssetsTest} is what turns the missing {@code sounds.json}
 * entry into a failing build rather than a silent {@code SoundEvent} that plays nothing.
 *
 * <h2>Where the variants come from</h2>
 *
 * <p>Each event names three files in {@code sounds.json}, and vanilla picks between them itself.
 * That is why {@code SoulAmbienceSounds.soundFor} no longer takes a random: the variety used to come
 * from a coin flip between two vanilla events in Java, and it now comes from the place the game
 * already had for it.
 *
 * <h2>Variable range, not fixed</h2>
 *
 * <p>{@link SoundEvent#createVariableRangeEvent} rather than the fixed-range factory, because the
 * per-sound {@code attenuation_distance} in {@code sounds.json} is doing real work here: it is how
 * large the *source* is, which is a different question from how far off it was thrown. A hollow
 * room's thud carries; an ember does not. A fixed range would throw that away and put the whole
 * palette back on one curve.
 */
public final class SoundsRegistry
{
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SoulHome.MODID);

    /** One-shot per voice. Keyed by the enum, so nothing has to keep a list in step with it. */
    private static final Map<SoulVoice, RegistryObject<SoundEvent>> VOICES = registerVoices();

    /**
     * The two rank layers of the ambient bed (#210) - a small dry room and a large airy one, mixed
     * against each other by how far the soul has climbed.
     */
    public static final RegistryObject<SoundEvent> BED_CLOSE = register("ambience.bed.close");

    public static final RegistryObject<SoundEvent> BED_OPEN = register("ambience.bed.open");

    /**
     * The character half of the bed (#214): one loop per non-{@link SoulVoice#BASE} voice, mixed
     * continuously against the two rank layers above rather than rolled for like the one-shots.
     */
    private static final Map<SoulVoice, RegistryObject<SoundEvent>> CHARACTER_BEDS = registerCharacterBeds();

    private SoundsRegistry()
    {
    }

    /** The event a voice speaks with. */
    public static SoundEvent voice(SoulVoice voice)
    {
        return VOICES.get(voice).get();
    }

    /** The registry name a voice's event has, which is also how {@code sounds.json} is keyed. */
    public static String voiceKey(SoulVoice voice)
    {
        return "ambience.voice." + voice.name().toLowerCase(Locale.ROOT);
    }

    /** The loop a voice's character layer plays (#214). Never called for {@link SoulVoice#BASE}. */
    public static SoundEvent characterBed(SoulVoice voice)
    {
        return CHARACTER_BEDS.get(voice).get();
    }

    /** The registry name a voice's character layer has, which is also how {@code sounds.json} is keyed. */
    public static String characterBedKey(SoulVoice voice)
    {
        return "ambience.bed.character." + voice.name().toLowerCase(Locale.ROOT);
    }

    private static Map<SoulVoice, RegistryObject<SoundEvent>> registerVoices()
    {
        final Map<SoulVoice, RegistryObject<SoundEvent>> events = new EnumMap<>(SoulVoice.class);

        for (SoulVoice voice : SoulVoice.values())
        {
            events.put(voice, register(voiceKey(voice)));
        }

        return Map.copyOf(events);
    }

    private static Map<SoulVoice, RegistryObject<SoundEvent>> registerCharacterBeds()
    {
        final Map<SoulVoice, RegistryObject<SoundEvent>> events = new EnumMap<>(SoulVoice.class);

        for (SoulVoice voice : SoulVoice.values())
        {
            if (voice != SoulVoice.BASE)
            {
                events.put(voice, register(characterBedKey(voice)));
            }
        }

        return Map.copyOf(events);
    }

    private static RegistryObject<SoundEvent> register(String key)
    {
        return SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(SoulHome.MODID, key)));
    }
}
