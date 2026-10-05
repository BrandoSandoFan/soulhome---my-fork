/*
 * File created ~ 21 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Every relative offset a Meditation Cushion can occupy for a player to be "on or beside" it
 * (#183, #239), kept Minecraft-free so the set of offsets itself - not just the numbers around it
 * - is something a test can pin. {@code MeditationService} walks these against the player's own
 * block position; nothing here touches a {@code Level} or a {@code BlockPos}.
 *
 * <p>The player's own cell, {@code (0, 0, 0)}, has to be in the list. A Meditation Cushion is a
 * short block (see {@code MeditationCushionBlock}'s 5/16-tall shape), so standing on one keeps a
 * player's feet - and so {@code Entity#blockPosition()} - inside the cushion's own cell, not one
 * cell above it the way a full block would put them. #239 checked one cell *below* the player
 * instead, which meant standing on the cushion - the obvious way to use it - was never detected
 * at all; every other approach (beside it, one step down from it) still worked, which is why the
 * bug read as "does nothing" rather than "sometimes wrong".
 */
public final class MeditationAdjacency
{
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private MeditationAdjacency()
    {
    }

    /**
     * Offsets {@code (dx, dy, dz)} from the player's own block position worth checking for a
     * cushion. Each horizontal direction is checked at floor level and one step down, since a
     * cushion beside the player can sit either on the same floor or one step below it; the
     * diagonals are added only when {@code MeditationSettings#cushionDiagonalAdjacency} asks for
     * them, so a pack that turns that off gets exactly the four cardinal neighbours it asked for.
     */
    public static List<int[]> offsets(boolean diagonalAdjacency)
    {
        final List<int[]> offsets = new ArrayList<>();

        offsets.add(new int[] {0, 0, 0});

        for (int[] cardinal : CARDINAL)
        {
            offsets.add(new int[] {cardinal[0], 0, cardinal[1]});
            offsets.add(new int[] {cardinal[0], -1, cardinal[1]});
        }

        if (diagonalAdjacency)
        {
            for (int[] diagonal : DIAGONAL)
            {
                offsets.add(new int[] {diagonal[0], 0, diagonal[1]});
                offsets.add(new int[] {diagonal[0], -1, diagonal[1]});
            }
        }

        return offsets;
    }
}
