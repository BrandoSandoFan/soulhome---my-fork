/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeditationSettingsTest
{
    @Test
    @DisplayName("meditation is the shorter of the two channels, on purpose")
    void meditationIsShorterThanTheKey()
    {
        assertEquals(MeditationSettings.DEFAULT_CUSHION_CHANNEL_TICKS, MeditationSettings.DEFAULTS.cushionChannelTicks());
        assertEquals(MeditationSettings.DEFAULT_KEY_CHANNEL_TICKS, MeditationSettings.DEFAULTS.keyChannelTicks());
        assertTrue(MeditationSettings.DEFAULTS.cushionChannelTicks() < MeditationSettings.DEFAULTS.keyChannelTicks());

        // the key held for 80 ticks while it was the only way in; now it is the fallback (#190)
        assertTrue(MeditationSettings.DEFAULTS.keyChannelTicks() > 80);
    }

    @Test
    @DisplayName("a channel of zero or fewer ticks could never complete, so it is rejected rather than stored")
    void nonPositiveDurationsAreRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new MeditationSettings(0, MeditationSettings.DEFAULT_KEY_CHANNEL_TICKS, true));
        assertThrows(IllegalArgumentException.class, () -> new MeditationSettings(-1, MeditationSettings.DEFAULT_KEY_CHANNEL_TICKS, true));
        assertThrows(IllegalArgumentException.class, () -> new MeditationSettings(MeditationSettings.DEFAULT_CUSHION_CHANNEL_TICKS, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new MeditationSettings(MeditationSettings.DEFAULT_CUSHION_CHANNEL_TICKS, -1, true));
    }

    @Test
    @DisplayName("the adjacency rule is read back exactly as given")
    void adjacencyRuleIsStoredVerbatim()
    {
        final MeditationSettings cardinalOnly = new MeditationSettings(60, 80, false);

        assertTrue(MeditationSettings.DEFAULTS.cushionDiagonalAdjacency());
        assertEquals(false, cardinalOnly.cushionDiagonalAdjacency());
    }
}
