/*
 * File created ~ 6 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalityClauseTest
{
    private static final VerticalityClauseType TYPE = new VerticalityClauseType();

    private static FormClause verticality(double minRatio, double idealRatio)
    {
        return TYPE.create(ClauseParams.builder()
                .put("min_ratio", minRatio).put("ideal_ratio", idealRatio).build());
    }

    private static BlockSignature filler()
    {
        return new TestBlocks.TestBlock("test:filler", Set.of(), Passability.BLOCKING);
    }

    private static RegionGeometry box(int sizeX, int sizeY, int sizeZ)
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(4);
        builder.add(0, 0, 0, filler());
        return builder.bounds(new RegionBounds(0, 0, 0, sizeX - 1, sizeY - 1, sizeZ - 1)).build();
    }

    @Test
    @DisplayName("a shaft at least ideal_ratio times as tall as it is wide scores 1.0")
    void tallShaftScoresFull()
    {
        // 3x3 footprint, 9 tall - 3x its own width, comfortably past a 2x ideal
        assertEquals(1.0, verticality(1.0, 2.0).evaluate(box(3, 9, 3), Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a room no taller than it is wide scores 0 - the clause's whole reason to exist")
    void flatRoomScoresZero()
    {
        assertEquals(0.0, verticality(1.0, 2.0).evaluate(box(6, 4, 6), Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("between the floor and the ideal, confidence ramps linearly")
    void betweenFloorAndIdealRamps()
    {
        // 4x4 footprint, 6 tall: ratio 1.5, half way from a 1.0 floor to a 2.0 ideal... but the
        // clause's ramp is ratio/ideal, not (ratio-min)/(ideal-min), so this is 0.75 of the way
        assertEquals(0.75, verticality(1.0, 2.0).evaluate(box(4, 6, 4), Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("footprint is the wider horizontal side - a corridor on its side does not read as a shaft")
    void footprintIsTheWiderAxis()
    {
        // 1 wide, 20 long, 5 tall: tall against the narrow axis, but the room's own footprint is
        // 20 wide the other way, so this is a corridor lying down, not a shaft standing up
        assertEquals(0.0, verticality(1.0, 2.0).evaluate(box(1, 5, 20), Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("a geometry with no bounds falls back to the bounding box of its indexed cells")
    void noBoundsFallsBackToIndexedCells()
    {
        RegionGeometry.Builder builder = RegionGeometry.builder(100);
        builder.add(0, 0, 0, filler());
        builder.add(0, 8, 0, filler());

        assertEquals(1.0, verticality(1.0, 2.0).evaluate(builder.build(), Map.of()).confidence(), 1e-9);
    }

    @Test
    @DisplayName("an empty geometry with no bounds and no cells scores 0 rather than dividing by nothing")
    void emptyGeometryScoresZero()
    {
        FormResult result = verticality(1.0, 2.0).evaluate(RegionGeometry.EMPTY, Map.of());

        assertEquals(0.0, result.confidence(), 1e-9);
        assertTrue(result.diagnostic().length() > 0);
    }

    @Test
    @DisplayName("params round-trip through encode")
    void encodeRoundTrips()
    {
        FormClause clause = verticality(1.2, 2.5);
        Map<String, Object> encoded = TYPE.encode(clause);

        assertEquals(1.2, (double) encoded.get("min_ratio"), 1e-9);
        assertEquals(2.5, (double) encoded.get("ideal_ratio"), 1e-9);
        assertEquals(clause, TYPE.create(ClauseParams.builder()
                .put("min_ratio", encoded.get("min_ratio")).put("ideal_ratio", encoded.get("ideal_ratio")).build()));
    }

    @Test
    @DisplayName("a negative ideal_ratio is rejected as unsatisfiable")
    void negativeIdealRatioIsInvalid()
    {
        assertTrue(verticality(1.0, -1.0).validationErrors(Set.of()).size() > 0);
    }
}
