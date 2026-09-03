/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EssenceSettingsTest
{
    private static final EssenceSettings SETTINGS = new EssenceSettings(1.0d, 100.0d);

    @Test
    @DisplayName("an empty soulhome accrues nothing, however long it sits there")
    void zeroScoreIsZeroGain()
    {
        assertEquals(0, SETTINGS.residueGained(0, 60_000L));
        assertEquals(0, SETTINGS.residueGained(-5, 60_000L));
    }

    @Test
    @DisplayName("no time elapsed is no residue, whatever the score")
    void zeroElapsedIsZeroGain()
    {
        assertEquals(0, SETTINGS.residueGained(400, 0L));
        assertEquals(0, SETTINGS.residueGained(400, -1_000L));
    }

    @Test
    @DisplayName("residue grows linearly with elapsed time at a fixed score")
    void linearInTime()
    {
        final double oneSecond = SETTINGS.residueGained(400, 1_000L);
        final double tenSeconds = SETTINGS.residueGained(400, 10_000L);

        assertEquals(oneSecond * 10, tenSeconds, 1e-9);
    }

    @Test
    @DisplayName("nineteen tier-3 rooms are not worth twenty times four tier-3 rooms - the curve is sublinear")
    void sublinearInScore()
    {
        // score is a stand-in for "roughly proportional to room count"; the shape under test is
        // that a ~5x score difference does not turn into a ~5x residue difference
        final double fourRooms = SETTINGS.residueGained(4, 60_000L);
        final double nineteenRooms = SETTINGS.residueGained(19, 60_000L);

        assertTrue(nineteenRooms > fourRooms, "more score must still earn more residue");
        assertTrue(nineteenRooms < fourRooms * 4, "the gain must not scale linearly with score");
    }

    @Test
    @DisplayName("rejects a rate that would pay out negative residue, or a conversion rate of zero")
    void rejectsInvalidSettings()
    {
        assertThrows(IllegalArgumentException.class, () -> new EssenceSettings(-1.0d, 100.0d));
        assertThrows(IllegalArgumentException.class, () -> new EssenceSettings(1.0d, 0.0d));
        assertThrows(IllegalArgumentException.class, () -> new EssenceSettings(1.0d, -10.0d));
    }
}
