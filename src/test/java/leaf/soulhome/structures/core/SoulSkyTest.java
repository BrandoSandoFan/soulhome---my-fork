/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sky over a soul, and what its air carries (#163). The first playtest found both bland and
 * undifferentiated: a soul full of rooms that looked like an empty one. What is pinned here is the
 * shape of the fix - two souls built differently look different, rank is visible standing still -
 * and the old promises it must not break: never dark, zero means off, nothing assigned a kind.
 */
class SoulSkyTest
{
    private static final AmbienceSettings FULL = new AmbienceSettings(true, true, true, true, 1d, 1d);

    @Test
    @DisplayName("off is off, through every control that turns it off")
    void offIsOff()
    {
        final SoulCharacter warm = character(SoulTrait.WARM, 600d);

        assertSame(SoulSky.NONE, SoulSky.of(warm, 3, 5, null));
        assertSame(SoulSky.NONE, SoulSky.of(warm, 3, 5, AmbienceSettings.OFF));
        assertSame(SoulSky.NONE, SoulSky.of(warm, 3, 5, new AmbienceSettings(true, true, true, true, 0d, 1d)));
        assertSame(SoulSky.NONE, SoulSky.of(warm, 3, 5, new AmbienceSettings(true, false, false, true, 1d, 1d)),
                "with both visual switches off there is nothing for a sky to answer");
        assertFalse(SoulSky.NONE.active());

        assertTrue(SoulAmbience.weatherRates(warm, AmbienceSettings.OFF).isEmpty());
        assertTrue(SoulAmbience.weatherRates(warm, new AmbienceSettings(true, true, false, true, 1d, 1d)).isEmpty(),
                "the air shows the character, so the character switch is its switch");
    }

    @Test
    @DisplayName("rank is visible standing still: the sky deepens and the stars come out as a soul climbs")
    void rankOpensTheSky()
    {
        float previousHeight = -1f;
        float previousStars = -1f;

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulSky sky = SoulSky.of(SoulCharacter.EMPTY, rank, SoulBounds.MAX_RANK, FULL);

            assertTrue(sky.active(), "an empty soul still has a sky");
            assertTrue(sky.height() > previousHeight);
            assertTrue(sky.stars() > previousStars || rank == 0);

            previousHeight = sky.height();
            previousStars = sky.stars();
        }

        assertEquals(0f, SoulSky.of(SoulCharacter.EMPTY, 0, SoulBounds.MAX_RANK, FULL).stars(), 1e-6f,
                "no stars at rank 0: they are what climbing earns");
    }

    @Test
    @DisplayName("no zenith anyone can build is darker than the floor")
    void neverBlack()
    {
        for (SoulTrait first : SoulTrait.values())
        {
            for (SoulTrait second : SoulTrait.values())
            {
                final SoulSky sky = SoulSky.of(character(first, 900d, second, 400d), 5, 5, FULL);

                assertTrue(SoulSky.luminance(sky.zenith()) >= SoulSky.ZENITH_FLOOR - 1e-3f,
                        first + " with " + second + " has a zenith at " + SoulSky.luminance(sky.zenith()));
            }
        }
    }

    @Test
    @DisplayName("souls built differently have different skies")
    void soulsDiffer()
    {
        final SoulSky warm = SoulSky.of(character(SoulTrait.WARM, 800d), 2, 5, FULL);
        final SoulSky cold = SoulSky.of(character(SoulTrait.COLD, 800d), 2, 5, FULL);
        final SoulSky arcane = SoulSky.of(character(SoulTrait.ARCANE, 800d), 2, 5, FULL);
        final SoulSky empty = SoulSky.of(SoulCharacter.EMPTY, 2, 5, FULL);

        assertTrue(distance(warm.zenith(), cold.zenith()) > 0.08f);
        assertTrue(distance(warm.zenith(), arcane.zenith()) > 0.08f);
        assertTrue(distance(cold.zenith(), arcane.zenith()) > 0.05f);
        assertTrue(distance(warm.zenith(), empty.zenith()) > 0.08f);
    }

    @Test
    @DisplayName("a soul of two things shows its second in the veil, rather than averaging it away")
    void theSecondTraitIsInTheVeil()
    {
        final SoulCharacter hearthsAndLibraries = character(SoulTrait.WARM, 700d, SoulTrait.ARCANE, 300d);
        final SoulSky sky = SoulSky.of(hearthsAndLibraries, 2, 5, FULL);
        final float[] arcane = SoulAmbience.Palette.of(SoulTrait.ARCANE);
        final float[] warm = SoulAmbience.Palette.of(SoulTrait.WARM);

        assertEquals(SoulAxis.ESSENCE, SoulSky.secondAxis(hearthsAndLibraries));
        assertTrue(sky.veilStrength() > 0.1f);
        assertTrue(distance(sky.veil(), arcane) < distance(sky.veil(), warm),
                "the veil is the arcane half of this soul, not more of its warmth");
    }

    @Test
    @DisplayName("a soul committed to one axis has no second, and an empty soul has no veil at all")
    void veilEdges()
    {
        assertNull(SoulSky.secondAxis(character(SoulTrait.COLD, 500d)));
        assertEquals(0f, SoulSky.of(SoulCharacter.EMPTY, 3, 5, FULL).veilStrength(), 1e-6f);
        assertTrue(SoulSky.of(character(SoulTrait.COLD, 500d), 3, 5, FULL).veilStrength() > 0f,
                "a single-minded soul still has something moving in its sky");
    }

    @Test
    @DisplayName("a soul full of rooms is strongly coloured - #163's complaint, answered as a property")
    void manyRoomsReadStrongly()
    {
        // the playtest's soul: a great many rooms, of several kinds
        final SoulCharacter busy = character(SoulTrait.WARM, 900d, SoulTrait.ARCANE, 500d, SoulTrait.VERDANT, 300d);
        final SoulAmbience ambience = SoulAmbience.of(busy, 2, 5, 56, AmbienceSettings.DEFAULT);
        final float[] fog = {ambience.red(), ambience.green(), ambience.blue()};

        assertTrue(distance(fog, SoulAmbience.NEUTRAL) > 0.25f,
                "the fog of a soul of many rooms moved only " + distance(fog, SoulAmbience.NEUTRAL) + " from pale blue");
        assertTrue(distance(fog, SoulAmbience.Palette.of(SoulTrait.WARM))
                        < distance(fog, SoulAmbience.Palette.of(SoulTrait.VERDANT)),
                "and it reads as what it is mostly made of, not as the grey of everything averaged");
    }

    @Test
    @DisplayName("the air carries what the soul is made of, in proportion, and never more than the ceiling")
    void weather()
    {
        final Map<SoulVoice, Float> warm = SoulAmbience.weatherRates(character(SoulTrait.WARM, 900d), FULL);

        assertTrue(warm.getOrDefault(SoulVoice.WARM, 0f) > 0.5f);
        assertFalse(warm.containsKey(SoulVoice.COLD));
        assertFalse(warm.containsKey(SoulVoice.BASE), "the place's own drift is the firmament motes");

        final Map<SoulVoice, Float> steam = SoulAmbience.weatherRates(
                character(SoulTrait.WARM, 500d, SoulTrait.COLD, 500d), FULL);

        assertTrue(steam.getOrDefault(SoulVoice.STEAM, 0f)
                        > steam.getOrDefault(SoulVoice.WARM, 0f) + steam.getOrDefault(SoulVoice.COLD, 0f),
                "forge and freezer together fill the air with steam, not embers and snow in turn");

        for (SoulTrait first : SoulTrait.values())
        {
            for (SoulTrait second : SoulTrait.values())
            {
                float total = 0f;

                for (float rate : SoulAmbience.weatherRates(character(first, 5_000d, second, 5_000d), FULL).values())
                {
                    total += rate;
                }

                assertTrue(total <= SoulAmbience.MAX_WEATHER_RATE + 1e-4f, first + "/" + second + ": " + total);
            }
        }

        assertTrue(SoulAmbience.weatherRates(SoulCharacter.EMPTY, FULL).isEmpty());
    }

    private static float distance(float[] a, float[] b)
    {
        final float dr = a[0] - b[0];
        final float dg = a[1] - b[1];
        final float db = a[2] - b[2];

        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static SoulCharacter character(Object... traitsAndPulls)
    {
        final Map<SoulTrait, Double> pulls = new EnumMap<>(SoulTrait.class);

        for (int index = 0; index < traitsAndPulls.length; index += 2)
        {
            pulls.merge((SoulTrait) traitsAndPulls[index], (Double) traitsAndPulls[index + 1], Double::sum);
        }

        return new SoulCharacter(pulls);
    }
}
