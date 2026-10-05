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
    private static VesselSettings withFragilities(float key, float cushion, float gaze)
    {
        return new VesselSettings(true, key, cushion, gaze, VesselSettings.DEFAULT_DROP_SCATTER);
    }

    @Test
    @DisplayName("the defaults do not scale a hit down to nothing")
    void defaultsAreFullDamage()
    {
        assertEquals(VesselSettings.DEFAULT_KEY_FRAGILITY, VesselSettings.DEFAULTS.keyFragility());
        assertTrue(VesselSettings.DEFAULTS.keyFragility() > 0f);
    }

    @Test
    @DisplayName("the cushion is reduced fragility, not the key's own")
    void cushionIsReducedFragility()
    {
        assertEquals(VesselSettings.DEFAULT_CUSHION_FRAGILITY, VesselSettings.DEFAULTS.cushionFragility());
        assertTrue(VesselSettings.DEFAULTS.cushionFragility() < VesselSettings.DEFAULTS.keyFragility());
    }

    @Test
    @DisplayName("gazing is no safer than meditating (#187)")
    void gazeIsNoSaferThanTheCushion()
    {
        assertTrue(VesselSettings.DEFAULTS.gazeFragility() >= VesselSettings.DEFAULTS.cushionFragility());
    }

    @Test
    @DisplayName("damage transfer ships on (#181's decisions)")
    void damageTransferDefaultsOn()
    {
        assertTrue(VesselSettings.DEFAULTS.damageTransfer());
    }

    @Test
    @DisplayName("a forwarded hit is the hit times the vessel's fragility")
    void forwardedDamageScalesByFragility()
    {
        assertEquals(4f, VesselSettings.DEFAULTS.forwardedDamage(8f, VesselSettings.DEFAULT_CUSHION_FRAGILITY), 1e-6);
        assertEquals(8f, VesselSettings.DEFAULTS.forwardedDamage(8f, VesselSettings.DEFAULT_KEY_FRAGILITY), 1e-6);
    }

    @Test
    @DisplayName("with damage transfer off a vessel forwards nothing, whatever its fragility")
    void switchedOffForwardsNothing()
    {
        final VesselSettings off = new VesselSettings(
                false, 1f, 0.5f, 1f, VesselSettings.DEFAULT_DROP_SCATTER);

        assertEquals(0f, off.forwardedDamage(20f, 1f));
        assertEquals(0f, off.forwardedDamage(20f, 5f));
    }

    @Test
    @DisplayName("a zero or negative hit forwards nothing rather than healing the owner")
    void nonPositiveHitsForwardNothing()
    {
        assertEquals(0f, VesselSettings.DEFAULTS.forwardedDamage(0f, 1f));
        assertEquals(0f, VesselSettings.DEFAULTS.forwardedDamage(-3f, 1f));
        assertEquals(0f, VesselSettings.DEFAULTS.forwardedDamage(Float.NaN, 1f));
    }

    @Test
    @DisplayName("a fragility that could never hurt anyone is rejected rather than stored")
    void nonPositiveFragilityIsRejected()
    {
        final float key = VesselSettings.DEFAULT_KEY_FRAGILITY;
        final float cushion = VesselSettings.DEFAULT_CUSHION_FRAGILITY;
        final float gaze = VesselSettings.DEFAULT_GAZE_FRAGILITY;

        assertThrows(IllegalArgumentException.class, () -> withFragilities(0f, cushion, gaze));
        assertThrows(IllegalArgumentException.class, () -> withFragilities(-1f, cushion, gaze));
        assertThrows(IllegalArgumentException.class, () -> withFragilities(key, 0f, gaze));
        assertThrows(IllegalArgumentException.class, () -> withFragilities(key, -1f, gaze));
        assertThrows(IllegalArgumentException.class, () -> withFragilities(key, cushion, 0f));
        assertThrows(IllegalArgumentException.class, () -> withFragilities(key, cushion, -1f));
    }

    @Test
    @DisplayName("a negative scatter is rejected")
    void negativeScatterIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new VesselSettings(true, 1f, 0.5f, 1f, -0.1d));
    }
}
