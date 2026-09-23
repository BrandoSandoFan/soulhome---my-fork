/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins rule 4 of #181: their rank sets the amount, your rank sets the legibility, and the two are
 * never collapsed into one number that would render a strong opponent you can handle identically to
 * a weak one.
 */
class SuppressionSettingsTest
{
    private static final SuppressionSettings DEFAULTS = SuppressionSettings.DEFAULTS;
    private static final int MAX = SoulBounds.MAX_RANK;

    @Test
    @DisplayName("no two distinct (theirRank, yourRank) pairs render the same signature")
    void everyPairIsDistinguishable()
    {
        for (int maxRank = 1; maxRank <= 12; maxRank++)
        {
            final Map<SuppressionSettings.Signature, String> seen = new HashMap<>();

            for (int theirs = 1; theirs <= maxRank; theirs++)
            {
                for (int yours = 0; yours <= maxRank; yours++)
                {
                    final SuppressionSettings.Signature signature = DEFAULTS.signature(theirs, yours, maxRank);
                    final String pair = "(" + theirs + ", " + yours + ")";
                    final String clash = seen.put(signature, pair);

                    if (clash != null)
                    {
                        fail("max rank " + maxRank + ": " + pair + " renders the same as " + clash);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a rank 0 player renders nothing at all, whoever is looking")
    void rankZeroRendersNothing()
    {
        for (int yours = 0; yours <= MAX; yours++)
        {
            assertNull(DEFAULTS.signature(0, yours, MAX));
        }

        assertEquals(0d, DEFAULTS.radiusFor(0));
        assertEquals(0d, DEFAULTS.strengthFor(0));
    }

    @Test
    @DisplayName("the amount is theirs: your rank never changes radius, strength, rings or range")
    void amountIgnoresTheObserver()
    {
        for (int theirs = 1; theirs <= MAX; theirs++)
        {
            final SuppressionSettings.Signature low = DEFAULTS.signature(theirs, 0, MAX);

            for (int yours = 1; yours <= MAX; yours++)
            {
                final SuppressionSettings.Signature other = DEFAULTS.signature(theirs, yours, MAX);

                assertEquals(low.radius(), other.radius());
                assertEquals(low.strength(), other.strength());
                assertEquals(low.rings(), other.rings());
            }
        }
    }

    @Test
    @DisplayName("the legibility is yours: their rank never changes how readable the field is")
    void legibilityIgnoresTheWatched()
    {
        for (int yours = 0; yours <= MAX; yours++)
        {
            final double first = DEFAULTS.signature(1, yours, MAX).legibility();

            for (int theirs = 2; theirs <= MAX; theirs++)
            {
                assertEquals(first, DEFAULTS.signature(theirs, yours, MAX).legibility());
            }
        }
    }

    @Test
    @DisplayName("the ring count is their rank")
    void ringsAreTheirRank()
    {
        for (int theirs = 1; theirs <= MAX; theirs++)
        {
            assertEquals(theirs, DEFAULTS.signature(theirs, MAX, MAX).rings());
        }
    }

    @Test
    @DisplayName("every ascension makes the field a little more legible, from a smear to crisp rings")
    void legibilityStrictlyRises()
    {
        assertEquals(0d, SuppressionSettings.legibilityFor(0, MAX));
        assertEquals(1d, SuppressionSettings.legibilityFor(MAX, MAX));

        for (int yours = 1; yours <= MAX; yours++)
        {
            assertTrue(SuppressionSettings.legibilityFor(yours, MAX) > SuppressionSettings.legibilityFor(yours - 1, MAX));
        }
    }

    @Test
    @DisplayName("a legible field is one you can aim through; the same field unread spoils your aim")
    void legibilityReducesDisplacement()
    {
        for (int theirs = 1; theirs <= MAX; theirs++)
        {
            assertTrue(DEFAULTS.displacementFor(theirs, MAX, MAX) < DEFAULTS.displacementFor(theirs, 0, MAX));
            assertEquals(DEFAULTS.strengthFor(theirs), DEFAULTS.displacementFor(theirs, 0, MAX), 1e-9);
        }
    }

    @Test
    @DisplayName("a higher rank is perceived from further off, with a larger field")
    void amountGrowsWithRank()
    {
        for (int theirs = 1; theirs <= MAX; theirs++)
        {
            assertTrue(DEFAULTS.radiusFor(theirs) > DEFAULTS.radiusFor(theirs - 1));
            assertTrue(DEFAULTS.perceptionRangeFor(theirs) > DEFAULTS.perceptionRangeFor(theirs - 1));
            assertNotNull(DEFAULTS.signature(theirs, 0, MAX));
        }
    }

    @Test
    @DisplayName("a radius that stood still between ranks is rejected, since it would merge two ranks")
    void flatRadiusIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new SuppressionSettings(
                true, true, true, 16d, 4d, 0.8d, 0d, 0.1d, 0.3d));
    }
}
