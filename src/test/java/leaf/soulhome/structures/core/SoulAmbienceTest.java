/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the blend is turned into (#163/#164/#167), and the promises the arithmetic is what keeps:
 * the fog never reaches the build, the colour never goes dark, the switches really switch it off,
 * and a contested soul looks like neither of its halves.
 */
class SoulAmbienceTest
{
    private static final int VERGE_RANK_0 = SoulBounds.DEFAULT_BASE_VERGE;

    private static final int VERGE_RANK_5 =
            SoulBounds.DEFAULT_BASE_VERGE + SoulBounds.MAX_RANK * SoulBounds.DEFAULT_VERGE_PER_RANK;

    private static SoulCharacter character(Object... traitsAndPulls)
    {
        Map<SoulTrait, Double> pulls = new EnumMap<>(SoulTrait.class);

        for (int i = 0; i < traitsAndPulls.length; i += 2)
        {
            pulls.put((SoulTrait) traitsAndPulls[i], (Double) traitsAndPulls[i + 1]);
        }

        return new SoulCharacter(pulls);
    }

    private static float luminance(SoulAmbience ambience)
    {
        return 0.2126f * ambience.red() + 0.7152f * ambience.green() + 0.0722f * ambience.blue();
    }

    @Test
    void everySwitchOffIsTheModBeforeThisEpic()
    {
        assertSame(SoulAmbience.NONE, SoulAmbience.of(
                character(SoulTrait.WARM, 400d), 5, 5, VERGE_RANK_5, AmbienceSettings.OFF));

        assertSame(SoulAmbience.NONE, SoulAmbience.of(
                character(SoulTrait.WARM, 400d), 5, 5, VERGE_RANK_5,
                new AmbienceSettings(true, true, true, true, 0d, 1d)),
                "intensity zero is the same thing as the master switch, through one control (#167)");

        assertFalse(SoulAmbience.NONE.hasFog());
        assertFalse(SoulAmbience.NONE.tinted());
        assertEquals(0f, SoulAmbience.NONE.moteRate());
    }

    @Test
    void eachPartCanBeTurnedOffOnItsOwn()
    {
        final SoulCharacter warm = character(SoulTrait.WARM, 400d);

        final SoulAmbience noRank = SoulAmbience.of(warm, 3, 5, 72,
                new AmbienceSettings(true, false, true, true, 1d, 1d));

        assertFalse(noRank.hasFog(), "rank visuals off leaves Minecraft's own fog alone");
        assertEquals(0f, noRank.moteRate());
        assertTrue(noRank.tinted());

        final SoulAmbience noColour = SoulAmbience.of(warm, 3, 5, 72,
                new AmbienceSettings(true, true, false, true, 1d, 1d));

        assertFalse(noColour.tinted());
        assertEquals(SoulAmbience.NEUTRAL[0], noColour.red());
        assertTrue(noColour.hasFog());
    }

    /**
     * The one that would be broken during tuning and noticed by a player rather than by a test -
     * fog standing between someone and the wall they are building.
     */
    @Test
    void fogNeverReachesAnythingAPlayerMayHaveBuilt()
    {
        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulBounds bounds = SoulBounds.forRank(rank);
            final int verge = bounds.vergeHalfExtent();

            for (double intensity = 0.1d; intensity <= 1d; intensity += 0.1d)
            {
                final SoulAmbience ambience = SoulAmbience.of(
                        character(SoulTrait.HOLLOW, 500d), rank, SoulBounds.MAX_RANK, verge,
                        new AmbienceSettings(true, true, true, true, intensity, 1d));

                // the furthest corner of the box a player may build in, measured from the middle
                final double corner = Math.sqrt(2d) * verge;

                assertTrue(ambience.fogFar() > corner,
                        "rank " + rank + " at intensity " + intensity + ": fog at " + ambience.fogFar()
                                + " would stand in front of a corner at " + corner);
            }
        }
    }

    @Test
    void theSkyOpensWithRankAndNeverCloses()
    {
        float previous = 0f;

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulAmbience ambience = SoulAmbience.of(
                    SoulCharacter.EMPTY, rank, SoulBounds.MAX_RANK, SoulBounds.forRank(rank).vergeHalfExtent(),
                    AmbienceSettings.DEFAULT);

            assertTrue(ambience.fogFar() > previous, "rank " + rank + " must read as larger than rank " + (rank - 1));

            previous = ambience.fogFar();
        }

        final SoulAmbience low = SoulAmbience.of(
                SoulCharacter.EMPTY, 0, SoulBounds.MAX_RANK, VERGE_RANK_0, AmbienceSettings.DEFAULT);
        final SoulAmbience high = SoulAmbience.of(
                SoulCharacter.EMPTY, 5, SoulBounds.MAX_RANK, VERGE_RANK_5, AmbienceSettings.DEFAULT);

        assertTrue(high.fogFar() > low.fogFar() * 3f, "a rank V soul is a different-sized place, not a slightly bigger one");
        assertTrue(high.moteRate() > low.moteRate());
    }

    @Test
    void aPackWithAShorterLadderStillReachesTheOpenSky()
    {
        final SoulAmbience topOfThree = SoulAmbience.of(SoulCharacter.EMPTY, 3, 3, 72, AmbienceSettings.DEFAULT);
        final SoulAmbience topOfFive = SoulAmbience.of(SoulCharacter.EMPTY, 5, 5, 72, AmbienceSettings.DEFAULT);

        assertEquals(topOfFive.fogFar(), topOfThree.fogFar(), 1e-4f);
    }

    @Test
    void anUnbuiltSoulIsUntinted()
    {
        final SoulAmbience ambience = SoulAmbience.of(
                SoulCharacter.EMPTY, 2, 5, 56, AmbienceSettings.DEFAULT);

        assertFalse(ambience.tinted());
        assertEquals(SoulAmbience.NEUTRAL[0], ambience.red(), 1e-6f);
        assertEquals(SoulAmbience.NEUTRAL[1], ambience.green(), 1e-6f);
        assertEquals(SoulAmbience.NEUTRAL[2], ambience.blue(), 1e-6f);
    }

    @Test
    void aSoulFullOfOneThingIsStronglyColouredAndOneRoomIsNot()
    {
        final SoulAmbience barely = SoulAmbience.of(
                character(SoulTrait.WARM, 20d), 2, 5, 56, AmbienceSettings.DEFAULT);
        final SoulAmbience committed = SoulAmbience.of(
                character(SoulTrait.WARM, 900d), 2, 5, 56, AmbienceSettings.DEFAULT);

        final float[] warm = SoulAmbience.Palette.of(SoulTrait.WARM);

        assertTrue(distance(committed, warm) < distance(barely, warm),
                "the more there is of it, the more the place looks like it");
        assertTrue(distance(barely, SoulAmbience.NEUTRAL) < distance(committed, SoulAmbience.NEUTRAL));
    }

    /** #165's own words: a soul with one of each sits between them rather than being sorted into one. */
    @Test
    void aMixedSoulIsNeitherOfItsHalves()
    {
        final SoulAmbience mixed = SoulAmbience.of(
                character(SoulTrait.WARM, 300d, SoulTrait.COLD, 300d), 2, 5, 56, AmbienceSettings.DEFAULT);
        final SoulAmbience onlyWarm = SoulAmbience.of(
                character(SoulTrait.WARM, 600d), 2, 5, 56, AmbienceSettings.DEFAULT);
        final SoulAmbience onlyCold = SoulAmbience.of(
                character(SoulTrait.COLD, 600d), 2, 5, 56, AmbienceSettings.DEFAULT);

        assertTrue(distance(mixed, colourOf(onlyWarm)) > 0.05f);
        assertTrue(distance(mixed, colourOf(onlyCold)) > 0.05f);

        // and specifically: not the average of the two, which is what "sits between them" would
        // mean if the mod did the obvious thing rather than the right one
        final float[] average = {
                (onlyWarm.red() + onlyCold.red()) / 2f,
                (onlyWarm.green() + onlyCold.green()) / 2f,
                (onlyWarm.blue() + onlyCold.blue()) / 2f};

        assertTrue(distance(mixed, average) > 0.02f, "a contested axis reads as its own thing, not as a midpoint");
        assertTrue(distance(mixed, SoulAmbience.Palette.contested(SoulAxis.THERMAL))
                < distance(mixed, average));
    }

    @Test
    void nothingAnyoneCanBuildMakesTheSoulDark()
    {
        for (SoulTrait first : SoulTrait.values())
        {
            for (SoulTrait second : SoulTrait.values())
            {
                final SoulAmbience ambience = SoulAmbience.of(
                        character(first, 700d, second, 500d), 5, 5, VERGE_RANK_5,
                        new AmbienceSettings(true, true, true, true, 1d, 1d));

                assertTrue(luminance(ambience) >= SoulAmbience.LUMINANCE_FLOOR - 1e-4f,
                        first + " with " + second + " reads at " + luminance(ambience));
            }
        }
    }

    @Test
    void aContestedAxisSpeaksWithItsOwnVoice()
    {
        final SoulCharacter steam = character(SoulTrait.WARM, 300d, SoulTrait.COLD, 300d);
        final SoulCharacter warm = character(SoulTrait.WARM, 600d);

        assertEquals(SoulVoice.STEAM, SoulAmbience.voiceFor(steam, 0.99d));
        assertEquals(SoulVoice.WARM, SoulAmbience.voiceFor(warm, 0.99d));
        assertEquals(SoulVoice.BASE, SoulAmbience.voiceFor(warm, 0d),
                "a soul is a place before it is a mix of rooms, so the base voice keeps a share of every draw");
        assertEquals(SoulVoice.BASE, SoulAmbience.voiceFor(SoulCharacter.EMPTY, 0.99d));
    }

    @Test
    void everyVoiceIsReachableForTheSoulThatEarnsIt()
    {
        for (SoulAxis axis : SoulAxis.values())
        {
            final SoulCharacter positive = character(axis.positive(), 900d);
            final SoulCharacter contested = character(axis.positive(), 450d, axis.negative(), 450d);

            assertNotEquals(SoulVoice.BASE, SoulAmbience.voiceFor(positive, 0.999d));
            assertEquals(SoulAmbience.Palette.voice(axis.positive()), SoulAmbience.voiceFor(positive, 0.999d));
            assertEquals(SoulAmbience.Palette.contestedVoice(axis), SoulAmbience.voiceFor(contested, 0.999d));
        }
    }

    private static float[] colourOf(SoulAmbience ambience)
    {
        return new float[] {ambience.red(), ambience.green(), ambience.blue()};
    }

    private static float distance(SoulAmbience ambience, float[] colour)
    {
        final float dr = ambience.red() - colour[0];
        final float dg = ambience.green() - colour[1];
        final float db = ambience.blue() - colour[2];

        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
