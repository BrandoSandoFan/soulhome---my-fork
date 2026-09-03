/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PillarInspectorTest
{
    @Test
    @DisplayName("a straight 3x3 column from floor to ceiling is a valid pillar")
    void straightColumnIsValid()
    {
        GridVolume volume = GridVolume.of(
                new String[] {"###", "###", "###"},
                new String[] {"###", "###", "###"},
                new String[] {"###", "###", "###"});

        PillarInspector.Result result = PillarInspector.inspect(volume, 1, 1, 0, 3, 2);

        assertTrue(result.valid());
        assertEquals(2, result.topY());
        assertEquals(9, result.capCells().size(), "the whole 3x3 top layer is standable");
    }

    @Test
    @DisplayName("a pillar may taper above its base and still reach the ceiling")
    void taperingColumnIsValid()
    {
        GridVolume volume = GridVolume.of(
                new String[] {"###", "###", "###"},
                new String[] {"...", ".#.", "..."},
                new String[] {"...", ".#.", "..."});

        PillarInspector.Result result = PillarInspector.inspect(volume, 1, 1, 0, 3, 2);

        assertTrue(result.valid());
        assertEquals(1, result.capCells().size(), "only the centre column reaches the cap");
        assertEquals(new PillarInspector.CapCell(1, 1), result.capCells().get(0));
    }

    @Test
    @DisplayName("a vertical gap stops the pillar from reaching the ceiling")
    void gapStopsThePillar()
    {
        GridVolume volume = GridVolume.of(
                new String[] {"###", "###", "###"},
                new String[] {"...", "...", "..."},
                new String[] {"###", "###", "###"});

        PillarInspector.Result result = PillarInspector.inspect(volume, 1, 1, 0, 3, 2);

        assertTrue(result.hasBase());
        assertFalse(result.reachesCeiling());
        assertEquals(0, result.topY(), "the flood cannot cross the gap at y=1");
    }

    @Test
    @DisplayName("a single post is not a pillar - the base must be a genuine 3x3")
    void singlePostIsNotABase()
    {
        GridVolume volume = GridVolume.of(
                new String[] {"...", ".#.", "..."},
                new String[] {"...", ".#.", "..."});

        PillarInspector.Result result = PillarInspector.inspect(volume, 1, 1, 0, 2, 2);

        assertFalse(result.hasBase());
        assertEquals(PillarInspector.Result.NO_BASE, result);
    }

    @Test
    @DisplayName("a base outside the search radius does not count - the anchor must be near it")
    void baseOutsideRadiusIsIgnored()
    {
        GridVolume volume = GridVolume.of(
                new String[] {
                        "###.......",
                        "###.......",
                        "###......."});

        PillarInspector.Result result = PillarInspector.inspect(volume, 8, 8, 0, 1, 2);

        assertFalse(result.hasBase());
    }

    @Test
    @DisplayName("a fenced-in square is not a pillar base - only full blocks count")
    void partialBlocksDoNotFormABase()
    {
        GridVolume volume = GridVolume.of(
                new String[] {"FFF", "FFF", "FFF"});

        PillarInspector.Result result = PillarInspector.inspect(volume, 1, 1, 0, 1, 2);

        assertFalse(result.hasBase());
    }
}
