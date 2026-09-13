/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGrowthSettingsTest
{
    private static final TerrainGrowthSettings DEFAULTS = TerrainGrowthSettings.DEFAULTS;

    @Test
    @DisplayName("ground lags the walls at every rank, so there is always verge left to build into")
    void groundAlwaysLagsTheWalls()
    {
        for (int rank = 0; rank <= SoulBounds.MAX_RANK; rank++)
        {
            final int verge = SoulBounds.forRank(rank).vergeHalfExtent();
            final int ground = DEFAULTS.groundLimit(rank, verge);

            assertTrue(
                    ground < verge,
                    "rank " + rank + ": ground reaches " + ground + " and the wall is at " + verge);
        }
    }

    @Test
    @DisplayName("the shipped ladder: 30 against 40 at rank I, 78 against 104 at rank V")
    void theShippedLadder()
    {
        assertEquals(30, DEFAULTS.groundLimit(1, SoulBounds.forRank(1).vergeHalfExtent()));
        assertEquals(42, DEFAULTS.groundLimit(2, SoulBounds.forRank(2).vergeHalfExtent()));
        assertEquals(54, DEFAULTS.groundLimit(3, SoulBounds.forRank(3).vergeHalfExtent()));
        assertEquals(66, DEFAULTS.groundLimit(4, SoulBounds.forRank(4).vergeHalfExtent()));
        assertEquals(78, DEFAULTS.groundLimit(5, SoulBounds.forRank(5).vergeHalfExtent()));
    }

    @Test
    @DisplayName("a pack whose verge margin is wider than its own verge gets a one-block island, not an exception")
    void anAbsurdMarginClampsRatherThanThrows()
    {
        TerrainGrowthSettings settings = new TerrainGrowthSettings(true, 18, 12, 500, 3, 3, 4, 3, 4);

        assertEquals(1, settings.groundLimit(5, 104));
    }

    @Test
    @DisplayName("the band is measured from the rank growth last completed, so catching up is one wider band")
    void theBandCatchesUp()
    {
        assertEquals(12, DEFAULTS.bandWidth(1, 0));
        assertEquals(12, DEFAULTS.bandWidth(3, 2));
        assertEquals(36, DEFAULTS.bandWidth(3, 0));
    }

    @Test
    @DisplayName("a rank already grown has no band at all - a repeated trigger is a no-op by arithmetic")
    void arankAlreadyGrownHasNoBand()
    {
        assertEquals(0, DEFAULTS.bandWidth(3, 3));
        assertEquals(0, DEFAULTS.bandWidth(2, 3));
    }

    @Test
    @DisplayName("the box floor clamps how deep the apron is cut; a surface on the floor datum is one layer")
    void theFloorClampsTheSoil()
    {
        assertEquals(1, DEFAULTS.layersAt(70, 70));
        assertEquals(3, DEFAULTS.layersAt(72, 70));
        assertEquals(4, DEFAULTS.layersAt(80, 70));
        assertEquals(0, DEFAULTS.layersAt(69, 70));
    }

    @Test
    @DisplayName("a nonsensical setting is refused rather than quietly producing no ground")
    void nonsensicalSettingsAreRefused()
    {
        assertThrows(IllegalArgumentException.class, () -> new TerrainGrowthSettings(true, -1, 12, 6, 3, 3, 4, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> new TerrainGrowthSettings(true, 18, 12, 6, 3, 3, 0, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> new TerrainGrowthSettings(true, 18, 12, 6, 3, 3, 4, 3, 0));
    }
}
