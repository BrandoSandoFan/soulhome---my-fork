/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VesselSettingsTest
{
    @Test
    @DisplayName("the defaults do not scale a hit down to nothing")
    void defaultsAreFullDamage()
    {
        assertEquals(VesselSettings.DEFAULT_KEY_FRAGILITY, VesselSettings.DEFAULTS.keyFragility());
        assertTrue(VesselSettings.DEFAULTS.keyFragility() > 0f);
    }

    @Test
    @DisplayName("a fragility that could never hurt anyone is rejected rather than stored")
    void nonPositiveFragilityIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new VesselSettings(0f));
        assertThrows(IllegalArgumentException.class, () -> new VesselSettings(-1f));
    }
}
