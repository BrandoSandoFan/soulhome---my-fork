/*
 * File created ~ 17 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ambient bed's arithmetic (#210) - which is the whole of the bed that can be got wrong
 * silently, and the reason {@code SoulAmbience.bedMix} exists rather than the client working the
 * levels out for itself.
 *
 * <p>What is pinned here is the set of promises the issue makes: rank is the mix and never the
 * volume, the crossfade between the two layers holds its power all the way up the ladder, the bed
 * sits under the one-shots, and off is off through every control that can turn it off.
 */
class SoulAmbienceBedTest
{
    private static final AmbienceSettings FULL = new AmbienceSettings(true, true, true, true, 1d, 1d);

    @Test
    @DisplayName("rank 0 is all of the close layer and the last rank is all of the open one")
    void rankIsTheMix()
    {
        final SoulAmbience.BedMix ground = SoulAmbience.bedMix(0, SoulBounds.MAX_RANK, FULL);
        final SoulAmbience.BedMix summit = SoulAmbience.bedMix(SoulBounds.MAX_RANK, SoulBounds.MAX_RANK, FULL);

        assertEquals(0f, ground.open(), 1e-6f, "a rank 0 soul is a small room and nothing else");
        assertTrue(ground.close() > 0f, "and it is still audible");

        assertEquals(0f, summit.open() - summit.total(), 1e-6f, "the last rank is the open layer alone");
        assertEquals(0f, summit.close(), 1e-6f);
    }

    @Test
    @DisplayName("the crossfade holds its power at every rank - no rung sounds like a mistake")
    void theCrossfadeIsConstantPower()
    {
        final float expected = SoulAmbience.bedMix(0, SoulBounds.MAX_RANK, FULL).total();

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulAmbience.BedMix mix = SoulAmbience.bedMix(rank, SoulBounds.MAX_RANK, FULL);

            // in power, not in amplitude: two decorrelated beds played together are louder than
            // either by sqrt(2), so their levels added straight would be the wrong thing to bound
            assertEquals(expected, mix.total(), 1e-5f,
                    "rank " + rank + " is louder or quieter than the rest of the ladder");
        }
    }

    @Test
    @DisplayName("rank moves the mix and never the volume")
    void rankNeverChangesTheLevel()
    {
        float previousOpen = -1f;

        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final SoulAmbience.BedMix mix = SoulAmbience.bedMix(rank, SoulBounds.MAX_RANK, FULL);

            assertTrue(mix.open() > previousOpen, "the open layer only ever grows with rank");
            previousOpen = mix.open();
        }
    }

    @Test
    @DisplayName("a pack with a short ladder still reaches the fully-opened bed at the top of it")
    void aShortLadderStillReachesTheTop()
    {
        final SoulAmbience.BedMix summit = SoulAmbience.bedMix(3, 3, FULL);

        assertEquals(0f, summit.close(), 1e-6f);
        assertEquals(SoulAmbience.bedMix(SoulBounds.MAX_RANK, SoulBounds.MAX_RANK, FULL).open(),
                summit.open(), 1e-6f);
    }

    @Test
    @DisplayName("the bed sits under the one-shots, which sit under a block being placed")
    void theBedIsTheQuietestThingHere()
    {
        final SoulAmbience.BedMix mix = SoulAmbience.bedMix(2, SoulBounds.MAX_RANK, AmbienceSettings.DEFAULT);
        final double oneShotCeiling = AmbienceSettings.DEFAULT.soundVolume() * AmbienceSettings.DEFAULT.intensity();

        assertTrue(mix.total() < oneShotCeiling,
                "the bed is never allowed to reach what a one-shot is worth before distance is taken off it");
        assertTrue(mix.audible(), "and it is still there at the default settings");
    }

    @Test
    @DisplayName("off is off, through every control that turns it off")
    void offIsOff()
    {
        assertSame(SoulAmbience.BedMix.SILENT, SoulAmbience.bedMix(3, SoulBounds.MAX_RANK, null),
                "no settings at all is silence, not the defaults");

        assertSame(SoulAmbience.BedMix.SILENT,
                SoulAmbience.bedMix(3, SoulBounds.MAX_RANK, AmbienceSettings.OFF));

        assertSame(SoulAmbience.BedMix.SILENT, SoulAmbience.bedMix(
                3, SoulBounds.MAX_RANK, new AmbienceSettings(false, true, true, true, 1d, 1d)),
                "the master switch");

        assertSame(SoulAmbience.BedMix.SILENT, SoulAmbience.bedMix(
                3, SoulBounds.MAX_RANK, new AmbienceSettings(true, true, true, false, 1d, 1d)),
                "the ambient sound switch");

        assertSame(SoulAmbience.BedMix.SILENT, SoulAmbience.bedMix(
                3, SoulBounds.MAX_RANK, new AmbienceSettings(true, true, true, true, 0d, 1d)),
                "intensity at zero");

        assertSame(SoulAmbience.BedMix.SILENT, SoulAmbience.bedMix(
                3, SoulBounds.MAX_RANK, new AmbienceSettings(true, true, true, true, 1d, 0d)),
                "the volume knob at zero");

        assertFalse(SoulAmbience.BedMix.SILENT.audible());
        assertEquals(0f, SoulAmbience.BedMix.SILENT.total(), 1e-9f);
    }

    @Test
    @DisplayName("the rank-visuals switch does not silence audio")
    void rankVisualsIsNotASoundSwitch()
    {
        final SoulAmbience.BedMix mix = SoulAmbience.bedMix(
                2, SoulBounds.MAX_RANK, new AmbienceSettings(true, false, true, true, 1d, 1d));

        assertTrue(mix.audible(),
                "a player who turned the sky's answer to rank off did not ask for the room to stop "
                        + "sounding like a room");
    }

    @Test
    @DisplayName("both knobs scale the bed, and neither of them alone can make it loud")
    void bothKnobsScaleIt()
    {
        final float full = SoulAmbience.bedMix(2, SoulBounds.MAX_RANK, FULL).total();
        final float halfVolume = SoulAmbience.bedMix(
                2, SoulBounds.MAX_RANK, new AmbienceSettings(true, true, true, true, 1d, 0.5d)).total();
        final float halfIntensity = SoulAmbience.bedMix(
                2, SoulBounds.MAX_RANK, new AmbienceSettings(true, true, true, true, 0.5d, 1d)).total();

        assertEquals(full * 0.5f, halfVolume, 1e-5f);
        assertEquals(full * 0.5f, halfIntensity, 1e-5f);
        assertEquals(SoulAmbience.BED_CEILING, full, 1e-5f,
                "at both knobs full the bed is exactly its own ceiling and no more");
    }
}
