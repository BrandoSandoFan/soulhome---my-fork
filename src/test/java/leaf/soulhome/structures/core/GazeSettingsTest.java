/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins rule 5 of #181 the way {@code ArchetypeCeilingTest} pins the tier bands: a better observatory
 * makes a gaze quieter, and nothing makes it silent.
 */
class GazeSettingsTest
{
    private static final GazeSettings DEFAULTS = GazeSettings.DEFAULTS;

    @Test
    @DisplayName("obviousness never reaches zero, even at the maximum configured magnitude")
    void obviousnessFloorHolds()
    {
        final double[] magnitudes = {0d, 1d, 3d, 10d, 100d, 1e9, Double.MAX_VALUE, Double.POSITIVE_INFINITY};

        for (double magnitude : magnitudes)
        {
            final double obviousness = DEFAULTS.obviousnessFor(magnitude);

            assertTrue(obviousness > 0d, "silent at magnitude " + magnitude);
            assertTrue(obviousness >= DEFAULTS.obviousnessFloor(), "below the floor at magnitude " + magnitude);
        }
    }

    @Test
    @DisplayName("the floor holds however steep a pack makes the falloff")
    void floorHoldsForAnyFalloff()
    {
        final GazeSettings steep = new GazeSettings(
                true, true, 24d, 8d, 300, 150, 6000, 600, 1.25d, 0.01d, 1e6d, 200);

        assertTrue(steep.obviousnessFor(Double.MAX_VALUE) >= 0.01d);
        assertTrue(steep.obviousnessFor(3d) > 0d);
    }

    @Test
    @DisplayName("a better observatory is quieter: obviousness falls strictly as magnitude rises")
    void obviousnessFallsWithMagnitude()
    {
        double previous = DEFAULTS.obviousnessFor(0d);
        assertEquals(1d, previous, 1e-9);

        for (double magnitude = 0.25d; magnitude <= 12d; magnitude += 0.25d)
        {
            final double next = DEFAULTS.obviousnessFor(magnitude);
            assertTrue(next < previous, "not quieter at " + magnitude);
            previous = next;
        }
    }

    @Test
    @DisplayName("a tier 3 observatory is still noticeable, and noticeably quieter than tier 1")
    void tierThreeIsQuieterNotSilent()
    {
        final double tierOne = DEFAULTS.obviousnessFor(1d);
        final double tierThree = DEFAULTS.obviousnessFor(3d);

        assertTrue(tierThree < tierOne);
        assertTrue(tierThree > DEFAULTS.obviousnessFloor());
    }

    @Test
    @DisplayName("a floor of zero is rejected: no configuration can make a gaze undetectable")
    void zeroFloorIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new GazeSettings(
                true, true, 24d, 8d, 300, 150, 6000, 600, 1.25d, 0d, 1d, 200));
        assertThrows(IllegalArgumentException.class, () -> new GazeSettings(
                true, true, 24d, 8d, 300, 150, 6000, 600, 1.25d, -0.1d, 1d, 200));
    }

    @Test
    @DisplayName("range and duration grow with magnitude; recharge shrinks but never to nothing")
    void curvesMoveTheRightWay()
    {
        assertTrue(DEFAULTS.rangeFor(3d) > DEFAULTS.rangeFor(1d));
        assertTrue(DEFAULTS.durationFor(3d, false) > DEFAULTS.durationFor(1d, false));
        assertTrue(DEFAULTS.rechargeTicksFor(3d) < DEFAULTS.rechargeTicksFor(1d));
        assertTrue(DEFAULTS.rechargeTicksFor(1_000d) >= 1);
    }

    @Test
    @DisplayName("an open soul is a longer gaze, by the one small bonus and no more")
    void openSoulBonusIsSmall()
    {
        final int closed = DEFAULTS.durationFor(2d, false);
        final int open = DEFAULTS.durationFor(2d, true);

        assertTrue(open > closed);
        assertEquals(Math.round(closed * GazeSettings.DEFAULT_OPEN_SOUL_DURATION_BONUS), open);
        assertTrue(GazeSettings.DEFAULT_OPEN_SOUL_DURATION_BONUS <= 1.5d);
    }
}
