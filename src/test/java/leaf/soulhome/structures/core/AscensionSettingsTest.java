/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AscensionSettingsTest
{
    @Test
    void willpowerRisesByAFlatStepPerRank()
    {
        AscensionSettings settings = new AscensionSettings(4, 600, 50.0, 25.0, 4);

        assertEquals(50.0, settings.willpowerRequired(1));
        assertEquals(75.0, settings.willpowerRequired(2));
        assertEquals(150.0, settings.willpowerRequired(5));
    }

    @Test
    void rankZeroOrBelowCostsNoMoreThanTheBase()
    {
        AscensionSettings settings = new AscensionSettings(4, 600, 50.0, 25.0, 4);

        assertEquals(50.0, settings.willpowerRequired(0));
        assertEquals(50.0, settings.willpowerRequired(-1));
    }

    @Test
    void aSearchRadiusTooSmallForTheMinimumBaseIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new AscensionSettings(4, 600, 50.0, 25.0, 1));
    }

    @Test
    void nonPositiveEssenceOrDurationIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new AscensionSettings(0, 600, 50.0, 25.0, 4));
        assertThrows(IllegalArgumentException.class, () -> new AscensionSettings(4, 0, 50.0, 25.0, 4));
    }
}
