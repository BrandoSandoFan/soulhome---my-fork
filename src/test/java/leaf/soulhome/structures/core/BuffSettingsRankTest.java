/*
 * File created ~ 4 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BuffSettings#rankFactor} and {@link BuffSettings#capFor(String, int)} in isolation -
 * {@code BuffCalculatorTest} covers them wired into a full computation, this covers the formulas
 * themselves against negative and out-of-range ranks.
 */
class BuffSettingsRankTest
{
    @Test
    @DisplayName("rank 0 is always exactly x1, whatever ascensionPerRank is tuned to")
    void rankZeroIsAlwaysExactlyOne()
    {
        assertEquals(1d, BuffSettings.DEFAULTS.rankFactor(0), 1e-9);
    }

    @Test
    @DisplayName("rank V is x(1 + ascensionPerRank * 5) at the shipped default")
    void rankFactorMatchesTheDefaultCurve()
    {
        assertEquals(1.75d, BuffSettings.DEFAULTS.rankFactor(5), 1e-9);
    }

    @Test
    @DisplayName("a negative rank is treated as rank 0 rather than shrinking a buff below its base value")
    void negativeRankIsClampedToZero()
    {
        assertEquals(BuffSettings.DEFAULTS.rankFactor(0), BuffSettings.DEFAULTS.rankFactor(-3), 1e-9);
        assertEquals(BuffSettings.DEFAULTS.capFor(SoulBuffTypes.XP_GAIN, 0),
                BuffSettings.DEFAULTS.capFor(SoulBuffTypes.XP_GAIN, -3), 1e-9);
    }

    @Test
    @DisplayName("the raised cap matches capFor(type) * (1 + ascensionCapPerRank * rank)")
    void capForRaisesTheDeclaredCeiling()
    {
        final double declared = BuffSettings.DEFAULTS.capFor(SoulBuffTypes.XP_GAIN);

        assertEquals(declared * 1.5d, BuffSettings.DEFAULTS.capFor(SoulBuffTypes.XP_GAIN, 5), 1e-9);
    }

    @Test
    @DisplayName("a negative ascensionPerRank or ascensionCapPerRank is rejected")
    void negativeRankKnobsAreRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new BuffSettings(
                0.5d, 3, 1.0d, java.util.Map.of(), BuffSettings.DEFAULT_TYPE_CAPS,
                BuffSettings.DEFAULT_ENTRY_FRACTION, BuffSettings.DEFAULT_RAMP_EXPONENT, -0.1d, 0.1d));

        assertThrows(IllegalArgumentException.class, () -> new BuffSettings(
                0.5d, 3, 1.0d, java.util.Map.of(), BuffSettings.DEFAULT_TYPE_CAPS,
                BuffSettings.DEFAULT_ENTRY_FRACTION, BuffSettings.DEFAULT_RAMP_EXPONENT, 0.1d, -0.1d));
    }
}
