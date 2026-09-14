/*
 * File created ~ 14 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The die roll behind #195's fix: a treasury's magnitude is a chance of +1 effective Fortune
 * level, not a chance of +1 random drop.
 */
class FortuneLevelsTest
{
    @Test
    @DisplayName("zero magnitude never hits, whatever the roll")
    void zeroMagnitudeNeverHits()
    {
        assertEquals(0, FortuneLevels.extraLevels(0d, 0d));
        assertEquals(0, FortuneLevels.extraLevels(0d, 0.5d));
        assertEquals(0, FortuneLevels.extraLevels(0d, 0.999999d));
    }

    @Test
    @DisplayName("a negative magnitude reads as zero rather than as a guaranteed miss becoming a hit")
    void negativeMagnitudeNeverHits()
    {
        assertEquals(0, FortuneLevels.extraLevels(-1d, 0d));
    }

    @Test
    @DisplayName("a roll strictly below the magnitude is a hit")
    void rollBelowMagnitudeHits()
    {
        assertEquals(1, FortuneLevels.extraLevels(0.15d, 0d));
        assertEquals(1, FortuneLevels.extraLevels(0.15d, 0.1499d));
    }

    @Test
    @DisplayName("a roll at or above the magnitude is a miss")
    void rollAtOrAboveMagnitudeMisses()
    {
        assertEquals(0, FortuneLevels.extraLevels(0.15d, 0.15d));
        assertEquals(0, FortuneLevels.extraLevels(0.15d, 0.9d));
    }

    @Test
    @DisplayName("a magnitude above 1 is clamped to a certain hit, never more than one level")
    void magnitudeAboveOneIsClampedToACertainHit()
    {
        assertEquals(1, FortuneLevels.extraLevels(5d, 0d));
        assertEquals(1, FortuneLevels.extraLevels(5d, 0.999999d));
    }

    @Test
    @DisplayName("over a large sample, the hit rate converges on the magnitude")
    void distributionMatchesMagnitudeOverManySamples()
    {
        final double magnitude = 0.12d;
        final int samples = 200_000;
        final java.util.Random random = new java.util.Random(42);

        int hits = 0;
        for (int i = 0; i < samples; i++)
        {
            hits += FortuneLevels.extraLevels(magnitude, random.nextDouble());
        }

        final double observed = (double) hits / samples;
        assertEquals(magnitude, observed, 0.01d, "observed hit rate " + observed + " should track the magnitude");
    }
}
