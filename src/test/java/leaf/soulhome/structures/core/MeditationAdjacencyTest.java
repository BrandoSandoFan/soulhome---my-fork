/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact bug in #239: a player standing on top of a Meditation Cushion never registered,
 * because {@code MeditationService} checked the cell below the player instead of the player's own
 * cell. If {@code (0, 0, 0)} - "the cushion is where the player is standing" - ever drops out of
 * this list again, this test fails without anyone needing to boot the game to notice.
 */
public class MeditationAdjacencyTest
{
    @Test
    public void includesThePlayersOwnCell()
    {
        assertTrue(contains(MeditationAdjacency.offsets(false), 0, 0, 0),
                "standing on top of the cushion must be detected - see #239");
    }

    @Test
    public void withoutDiagonalsOnlyChecksCardinalsAndOwnCell()
    {
        final List<int[]> offsets = MeditationAdjacency.offsets(false);

        // own cell, plus each of the four cardinal neighbours at floor level and one step down
        assertEquals(9, offsets.size());

        assertFalse(contains(offsets, 1, 0, 1), "diagonal offset must not appear when diagonals are off");
    }

    @Test
    public void diagonalAdjacencyAddsTheFourCorners()
    {
        final List<int[]> offsets = MeditationAdjacency.offsets(true);

        assertEquals(17, offsets.size());

        assertTrue(contains(offsets, 1, 0, 1));
        assertTrue(contains(offsets, 1, -1, 1));
        assertTrue(contains(offsets, -1, 0, -1));
        assertTrue(contains(offsets, -1, -1, -1));
    }

    @Test
    public void everyCardinalIsCheckedAtFloorLevelAndOneStepDown()
    {
        final List<int[]> offsets = MeditationAdjacency.offsets(false);

        for (int[] cardinal : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
        {
            assertTrue(contains(offsets, cardinal[0], 0, cardinal[1]));
            assertTrue(contains(offsets, cardinal[0], -1, cardinal[1]));
        }
    }

    private static boolean contains(List<int[]> offsets, int dx, int dy, int dz)
    {
        for (int[] offset : offsets)
        {
            if (offset[0] == dx && offset[1] == dy && offset[2] == dz)
            {
                return true;
            }
        }

        return false;
    }
}
