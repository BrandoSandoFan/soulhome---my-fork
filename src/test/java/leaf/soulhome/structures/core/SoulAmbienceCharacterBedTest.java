/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character half of the bed's arithmetic (#214) - what pins the rules the issue states in
 * prose: bounded against the rank bed, contested replaces rather than joins its poles, and an
 * empty soul carries no character layers at all.
 */
class SoulAmbienceCharacterBedTest
{
    private static final AmbienceSettings FULL = new AmbienceSettings(true, true, true, true, 1d, 1d);

    private static final float CEILING = 0.4f;

    @Test
    @DisplayName("an unbuilt soul has no character layers at all")
    void emptySoulIsSilent()
    {
        assertSame(SoulAmbience.CharacterBedMix.SILENT,
                SoulAmbience.characterBedMix(SoulCharacter.EMPTY, CEILING, FULL));

        assertFalse(SoulAmbience.CharacterBedMix.SILENT.audible());
        assertEquals(0f, SoulAmbience.CharacterBedMix.SILENT.total(), 1e-9f);
    }

    @Test
    @DisplayName("off is off, through every control that turns it off")
    void offIsOff()
    {
        final SoulCharacter warmSoul = characterOf(Map.of(SoulTrait.WARM, 200d));

        assertSame(SoulAmbience.CharacterBedMix.SILENT,
                SoulAmbience.characterBedMix(warmSoul, CEILING, null),
                "no settings at all is silence, not the defaults");

        assertSame(SoulAmbience.CharacterBedMix.SILENT,
                SoulAmbience.characterBedMix(warmSoul, CEILING, AmbienceSettings.OFF));

        assertSame(SoulAmbience.CharacterBedMix.SILENT, SoulAmbience.characterBedMix(
                warmSoul, CEILING, new AmbienceSettings(false, true, true, true, 1d, 1d)),
                "the master switch");

        assertSame(SoulAmbience.CharacterBedMix.SILENT, SoulAmbience.characterBedMix(
                warmSoul, CEILING, new AmbienceSettings(true, true, false, true, 1d, 1d)),
                "the character switch");

        assertSame(SoulAmbience.CharacterBedMix.SILENT, SoulAmbience.characterBedMix(
                warmSoul, CEILING, new AmbienceSettings(true, true, true, false, 1d, 1d)),
                "the ambient sound switch - character rides under sound, not rank visuals");

        assertSame(SoulAmbience.CharacterBedMix.SILENT,
                SoulAmbience.characterBedMix(warmSoul, 0f, FULL),
                "a rank bed worth nothing gives the character half nothing to sit under");
    }

    @Test
    @DisplayName("the rank-visuals switch does not silence the character bed")
    void rankVisualsIsNotASoundSwitch()
    {
        final SoulCharacter warmSoul = characterOf(Map.of(SoulTrait.WARM, 200d));
        final AmbienceSettings settings = new AmbienceSettings(true, false, true, true, 1d, 1d);

        assertTrue(SoulAmbience.characterBedMix(warmSoul, CEILING, settings).audible());
    }

    @Test
    @DisplayName("a soul built one way pole is louder as depth grows, and never past the ceiling")
    void poleGrowsWithDepthAndStaysBounded()
    {
        final SoulAmbience.CharacterBedMix shallow =
                SoulAmbience.characterBedMix(characterOf(Map.of(SoulTrait.WARM, 20d)), CEILING, FULL);
        final SoulAmbience.CharacterBedMix deep =
                SoulAmbience.characterBedMix(characterOf(Map.of(SoulTrait.WARM, 2_000d)), CEILING, FULL);

        assertTrue(deep.level(SoulVoice.WARM) > shallow.level(SoulVoice.WARM));
        assertEquals(0f, shallow.level(SoulVoice.COLD), 1e-6f);
        assertTrue(deep.total() <= CEILING + 1e-5f, "never louder than the rank bed it sits under");
    }

    @Test
    @DisplayName("built on both poles at once, the contested layer replaces them rather than joining them")
    void contestedReplacesPoles()
    {
        final SoulCharacter contested =
                characterOf(Map.of(SoulTrait.WARM, 1_000d, SoulTrait.COLD, 1_000d));

        final SoulAmbience.CharacterBedMix mix = SoulAmbience.characterBedMix(contested, CEILING, FULL);

        assertTrue(mix.level(SoulVoice.STEAM) > 0f, "steam should be the loudest thing here");
        assertEquals(0f, mix.level(SoulVoice.WARM), 1e-6f,
                "full tension leaves nothing for the warm pole - steam replaces it, not joins it");
        assertEquals(0f, mix.level(SoulVoice.COLD), 1e-6f);
    }

    @Test
    @DisplayName("nine layers at once are still never louder in total than the rank bed alone")
    void everyAxisAtOnceStaysBounded()
    {
        final SoulCharacter allAxes = characterOf(Map.ofEntries(
                Map.entry(SoulTrait.WARM, 500d),
                Map.entry(SoulTrait.ARCANE, 500d),
                Map.entry(SoulTrait.VERDANT, 500d)));

        final SoulAmbience.CharacterBedMix mix = SoulAmbience.characterBedMix(allAxes, CEILING, FULL);

        assertTrue(mix.total() <= CEILING + 1e-5f);
        assertTrue(mix.audible());
    }

    private static SoulCharacter characterOf(Map<SoulTrait, Double> pulls)
    {
        return new SoulCharacter(new EnumMap<>(pulls));
    }
}
