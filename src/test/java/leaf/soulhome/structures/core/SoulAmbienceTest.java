/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the blend is turned into (#163/#164/#167), and the promises the arithmetic is what keeps:
 * the fog never reaches the build, the colour never goes dark, the switches really switch it off,
 * a contested soul looks like neither of its halves - and, since #215/#216, that a one-shot comes
 * from the room that earned it and that a larger soul is never a louder one.
 */
class SoulAmbienceTest
{
    private static final int VERGE_RANK_0 = SoulBounds.DEFAULT_BASE_VERGE;

    private static final int VERGE_RANK_5 =
            SoulBounds.DEFAULT_BASE_VERGE + SoulBounds.MAX_RANK * SoulBounds.DEFAULT_VERGE_PER_RANK;

    /** Two rooms pulling opposite ways on one axis, which is all the origin maths reads. */
    private static final Map<String, ArchetypeDefinition> ROOMS_BY_ID = Map.of(
            "soulhome:hearth", archetype("soulhome:hearth", Map.of("warm", 1.5d)),
            "soulhome:aquarium", archetype("soulhome:aquarium", Map.of("cold", 1.5d)));

    private static ArchetypeDefinition archetype(String id, Map<String, Double> character)
    {
        return new ArchetypeDefinition(
                id, "archetype." + id, List.of(RegionType.ENCLOSED), 1, List.of(), List.of(), List.of(),
                List.of(new ArchetypeDefinition.Tier(1, 1)), List.of(), List.of(), List.of(), List.of(),
                character);
    }

    private static SoulAmbience.RoomBox box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
    {
        return new SoulAmbience.RoomBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

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

    /**
     * #208: every draw has to land short of vanilla's own attenuation cliff, or the "distant" sound
     * plays at a gain of nothing (or is skipped outright as too far from the listener). Swept over
     * every rank and intensity now that #216 moves the band with rank - which is exactly the kind of
     * tuning pass that could put it back.
     */
    @Test
    void oneShotPlacementNeverReachesTheAttenuationCliff()
    {
        final double limit = SoulAmbience.OneShotPlacement.AUDIBLE_RADIUS - SoulAmbience.OneShotPlacement.SAFETY_MARGIN;

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final int verge = SoulBounds.forRank(rank).vergeHalfExtent();

            for (double intensity = 0.1d; intensity <= 1d; intensity += 0.1d)
            {
                final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                        rank, SoulBounds.MAX_RANK, verge,
                        new AmbienceSettings(true, true, true, true, intensity, 1d));

                for (double horizontalRoll = 0d; horizontalRoll <= 1d; horizontalRoll += 0.05d)
                {
                    for (double verticalRoll = 0d; verticalRoll <= 1d; verticalRoll += 0.05d)
                    {
                        final SoulAmbience.OneShotPlacement placement =
                                SoulAmbience.oneShotPlacement(profile, horizontalRoll, verticalRoll);

                        assertTrue(placement.distanceFromListener() <= limit + 1e-9d,
                                "rank " + rank + " roll (" + horizontalRoll + ", " + verticalRoll
                                        + ") placed a one-shot " + placement.distanceFromListener()
                                        + " blocks from the listener, past the safe limit of " + limit);

                        assertTrue(placement.horizontalDistance() <= verge,
                                "rank " + rank + " threw a one-shot past its own verge");
                    }
                }
            }
        }
    }

    /** #216: a rank V soul's sounds come from out toward the verge, a rank 0 soul's from close in. */
    @Test
    void theOneShotBandMovesOutwardWithRank()
    {
        double previousNear = -1d;
        double previousFar = -1d;

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                    rank, SoulBounds.MAX_RANK, SoulBounds.forRank(rank).vergeHalfExtent(),
                    AmbienceSettings.DEFAULT);

            assertTrue(profile.near() >= previousNear, "rank " + rank + " reached back inward");
            assertTrue(profile.far() >= previousFar, "rank " + rank + " reached back inward");
            assertTrue(profile.far() >= profile.near());

            previousNear = profile.near();
            previousFar = profile.far();
        }

        final SoulAmbience.OneShotProfile lowest = SoulAmbience.oneShotProfile(
                0, SoulBounds.MAX_RANK, VERGE_RANK_0, AmbienceSettings.DEFAULT);
        final SoulAmbience.OneShotProfile highest = SoulAmbience.oneShotProfile(
                SoulBounds.MAX_RANK, SoulBounds.MAX_RANK, VERGE_RANK_5, AmbienceSettings.DEFAULT);

        assertTrue(highest.far() > lowest.far());
        assertTrue(highest.pitch() < lowest.pitch(), "pitch is still one of the cues, only the smallest");
    }

    /**
     * The one #216 says to pin: a bigger soul is a <b>larger</b> place, not a louder one. The echoes
     * are what make it read as large, and the first sound is scaled down to pay for them.
     */
    @Test
    void anEchoedOneShotIsNoLouderThanAnUnechoedOne()
    {
        int previousEchoes = -1;

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                    rank, SoulBounds.MAX_RANK, SoulBounds.forRank(rank).vergeHalfExtent(),
                    new AmbienceSettings(true, true, true, true, 1d, 1d));

            assertTrue(profile.echoes() >= previousEchoes, "rank " + rank + " lost a repeat");
            previousEchoes = profile.echoes();

            assertEquals(1f, profile.totalEnergy(), 1e-5f,
                    "rank " + rank + " sums to " + profile.totalEnergy() + " of a single one-shot");

            for (int i = 1; i <= profile.echoes(); i++)
            {
                assertTrue(profile.volumeOf(i) < profile.volumeOf(i - 1), "repeat " + i + " did not fall away");
            }
        }

        assertEquals(0, SoulAmbience.oneShotProfile(
                0, SoulBounds.MAX_RANK, VERGE_RANK_0, AmbienceSettings.DEFAULT).echoes(),
                "a rank 0 soul is a small room and has no tail at all");

        assertEquals(1f, SoulAmbience.oneShotProfile(
                0, SoulBounds.MAX_RANK, VERGE_RANK_0, AmbienceSettings.DEFAULT).leadVolume(), 1e-6f);
    }

    /** #215: a voice speaks from a room that pulls toward it, and never from one that does not. */
    @Test
    void aTraitVoiceComesFromARoomThatPullsTowardIt()
    {
        final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                2, SoulBounds.MAX_RANK, 56, AmbienceSettings.DEFAULT);

        // a hearth due east of the listener, an aquarium due west, and nothing else built
        final List<SoulAmbience.VoiceRoom> rooms = List.of(
                new SoulAmbience.VoiceRoom("soulhome:hearth", box(30, 64, -2, 36, 70, 4)),
                new SoulAmbience.VoiceRoom("soulhome:aquarium", box(-36, 64, -2, -30, 70, 4)));

        for (double pickRoll = 0d; pickRoll < 1d; pickRoll += 0.05d)
        {
            final SoulAmbience.OneShotPlacement warm = SoulAmbience.oneShotOrigin(
                    SoulVoice.WARM, rooms, ROOMS_BY_ID, 0d, 65d, 0d, pickRoll, 0.5d, profile);

            assertNotNull(warm, "a warm voice with a hearth in the soul has somewhere to come from");
            assertTrue(warm.directionX() > 0.9d, "the crackle came from the aquarium's side");

            final SoulAmbience.OneShotPlacement cold = SoulAmbience.oneShotOrigin(
                    SoulVoice.COLD, rooms, ROOMS_BY_ID, 0d, 65d, 0d, pickRoll, 0.5d, profile);

            assertNotNull(cold);
            assertTrue(cold.directionX() < -0.9d, "the cold voice came from the hearth's side");
        }
    }

    @Test
    void aVoiceWithNoRoomBehindItFallsBackToTheRandomPlacement()
    {
        final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                2, SoulBounds.MAX_RANK, 56, AmbienceSettings.DEFAULT);

        final List<SoulAmbience.VoiceRoom> rooms = List.of(
                new SoulAmbience.VoiceRoom("soulhome:hearth", box(30, 64, -2, 36, 70, 4)));

        assertNull(SoulAmbience.oneShotOrigin(
                SoulVoice.ARCANE, rooms, ROOMS_BY_ID, 0d, 65d, 0d, 0.5d, 0.5d, profile),
                "nothing in this soul is arcane, so the direction is simply unknown");

        assertNull(SoulAmbience.oneShotOrigin(
                SoulVoice.BASE, rooms, ROOMS_BY_ID, 0d, 65d, 0d, 0.5d, 0.5d, profile),
                "the base voice is the place, not a room in it");

        assertNull(SoulAmbience.oneShotOrigin(
                SoulVoice.WARM, List.of(), ROOMS_BY_ID, 0d, 65d, 0d, 0.5d, 0.5d, profile));

        assertNull(SoulAmbience.oneShotOrigin(
                SoulVoice.WARM, rooms, ROOMS_BY_ID, 33d, 65d, 1d, 0.5d, 0.5d, profile),
                "standing inside the hearth there is no direction to point at");
    }

    /** A contested voice may come from either pole's rooms - it is what both of them together made. */
    @Test
    void aContestedVoiceDrawsFromEitherPole()
    {
        final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                2, SoulBounds.MAX_RANK, 56, AmbienceSettings.DEFAULT);

        final List<SoulAmbience.VoiceRoom> rooms = List.of(
                new SoulAmbience.VoiceRoom("soulhome:hearth", box(30, 64, -2, 36, 70, 4)),
                new SoulAmbience.VoiceRoom("soulhome:aquarium", box(-36, 64, -2, -30, 70, 4)));

        boolean east = false;
        boolean west = false;

        for (double pickRoll = 0d; pickRoll < 1d; pickRoll += 0.05d)
        {
            final SoulAmbience.OneShotPlacement steam = SoulAmbience.oneShotOrigin(
                    SoulVoice.STEAM, rooms, ROOMS_BY_ID, 0d, 65d, 0d, pickRoll, 0.5d, profile);

            assertNotNull(steam);
            east |= steam.directionX() > 0d;
            west |= steam.directionX() < 0d;
        }

        assertTrue(east && west, "steam is what the forge and the freezer made together");
    }

    /**
     * A room nearer than the band is heard where it is. Pushing it outward to satisfy the profile
     * would be the same class of fault #208 was: a distance chosen by arithmetic rather than by
     * anything in the world.
     */
    @Test
    void aRoomNearerThanTheBandIsHeardWhereItStands()
    {
        final SoulAmbience.OneShotProfile profile = SoulAmbience.oneShotProfile(
                SoulBounds.MAX_RANK, SoulBounds.MAX_RANK, VERGE_RANK_5, AmbienceSettings.DEFAULT);

        final List<SoulAmbience.VoiceRoom> close = List.of(
                new SoulAmbience.VoiceRoom("soulhome:hearth", box(3, 64, -1, 5, 68, 1)));
        final List<SoulAmbience.VoiceRoom> distant = List.of(
                new SoulAmbience.VoiceRoom("soulhome:hearth", box(40, 64, -1, 46, 68, 1)));

        final SoulAmbience.OneShotPlacement near = SoulAmbience.oneShotOrigin(
                SoulVoice.WARM, close, ROOMS_BY_ID, 0d, 65d, 0d, 0.5d, 1d, profile);
        final SoulAmbience.OneShotPlacement far = SoulAmbience.oneShotOrigin(
                SoulVoice.WARM, distant, ROOMS_BY_ID, 0d, 65d, 0d, 0.5d, 1d, profile);

        assertNotNull(near);
        assertNotNull(far);

        assertEquals(3d, near.horizontalDistance(), 1e-6d, "the hearth is three blocks away, so that is where it is");
        assertEquals(profile.far(), far.horizontalDistance(), 1e-6d,
                "a hearth forty blocks off is heard from that direction at the edge of hearing");

        final double limit = SoulAmbience.OneShotPlacement.AUDIBLE_RADIUS - SoulAmbience.OneShotPlacement.SAFETY_MARGIN;

        assertTrue(far.distanceFromListener() <= limit + 1e-9d);
    }

    /** #212: the bed gets out of the way of this mod's own audio, and comes back without a swell. */
    @Test
    void theDuckIsBelowTheUndisturbedLevelAndRecoversMonotonically()
    {
        assertEquals(SoulAmbience.DUCK_LEVEL, SoulAmbience.duckLevel(1, 0), 1e-6f);
        assertEquals(SoulAmbience.DUCK_LEVEL, SoulAmbience.duckLevel(200, 999), 1e-6f,
                "a hold still running keeps the bed down however long ago the last one ended");

        assertTrue(SoulAmbience.duckLevel(1, 0) < 1f);

        float previous = -1f;

        for (int since = 0; since <= SoulAmbience.DUCK_RECOVERY_TICKS + 20; since++)
        {
            final float level = SoulAmbience.duckLevel(0, since);

            assertTrue(level >= previous, "the recovery went backwards at tick " + since);
            assertTrue(level >= SoulAmbience.DUCK_LEVEL - 1e-6f);
            assertTrue(level <= 1f + 1e-6f);

            previous = level;
        }

        assertEquals(1f, SoulAmbience.duckLevel(0, SoulAmbience.DUCK_RECOVERY_TICKS), 1e-6f);
    }

    @Test
    void everyVoiceKnowsWhichTraitsItSpeaksFor()
    {
        assertTrue(SoulAmbience.traitsOf(SoulVoice.BASE).isEmpty(), "the base voice is the place itself");

        for (SoulTrait trait : SoulTrait.values())
        {
            assertEquals(java.util.Set.of(trait), SoulAmbience.traitsOf(SoulAmbience.Palette.voice(trait)));
        }

        for (SoulAxis axis : SoulAxis.values())
        {
            assertEquals(
                    java.util.Set.of(axis.positive(), axis.negative()),
                    SoulAmbience.traitsOf(SoulAmbience.Palette.contestedVoice(axis)));
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
