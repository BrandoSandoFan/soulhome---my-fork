/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActiveAbilitySettingsTest
{
    @Test
    @DisplayName("the multiplier scales a cooldown in both directions")
    void multiplierScalesCooldown()
    {
        assertEquals(200, new ActiveAbilitySettings(true, 2.0d, 1, 8).effectiveCooldown(100));
        assertEquals(50, new ActiveAbilitySettings(true, 0.5d, 1, 8).effectiveCooldown(100));
    }

    @Test
    @DisplayName("the floor holds whatever the multiplier and the magnitude did - #85's rule")
    void floorHoldsBelowEverything()
    {
        ActiveAbilitySettings settings = new ActiveAbilitySettings(true, 0.01d, 40, 8);

        assertEquals(40, settings.effectiveCooldown(100));
        assertEquals(40, settings.effectiveCooldown(1));
        assertEquals(40, settings.effectiveCooldown(0));
    }

    @Test
    @DisplayName("scaling rounds up, so a pack asking for longer cooldowns never gets a shorter one")
    void scalingRoundsUp()
    {
        assertEquals(11, new ActiveAbilitySettings(true, 1.01d, 1, 8).effectiveCooldown(10));
    }

    @Test
    @DisplayName("charges are bounded by the ceiling but never fall below one")
    void chargesAreBounded()
    {
        ActiveAbilitySettings settings = new ActiveAbilitySettings(true, 1.0d, 40, 4);

        assertEquals(4, settings.effectiveCharges(9));
        assertEquals(3, settings.effectiveCharges(3));
        assertEquals(1, settings.effectiveCharges(0));
        assertEquals(1, settings.effectiveCharges(-5));
    }

    @Test
    @DisplayName("the defaults are usable as they ship")
    void defaultsAreSane()
    {
        assertEquals(ActiveAbilitySettings.DEFAULT_MIN_COOLDOWN_TICKS,
                ActiveAbilitySettings.DEFAULTS.effectiveCooldown(1));
        assertEquals(100, ActiveAbilitySettings.DEFAULTS.effectiveCooldown(100));
    }

    @Test
    @DisplayName("values that would make an ability unusable are rejected rather than stored")
    void invalidValuesAreRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new ActiveAbilitySettings(true, 0d, 40, 8));
        assertThrows(IllegalArgumentException.class, () -> new ActiveAbilitySettings(true, 1d, 0, 8));
        assertThrows(IllegalArgumentException.class, () -> new ActiveAbilitySettings(true, 1d, 40, 0));
    }
}
