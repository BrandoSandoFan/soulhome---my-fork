/*
 * File created ~ 6 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:verticality}: is the room taller than it is wide - a shaft, not a hall lying on
 * its side. Gale Roost is what this was written for: an updraft room reads as a chimney rather than
 * a floor, and nothing before this clause could say so, since every shape clause in the vocabulary
 * grades one named element's position - {@code apex}, {@code platform}, {@code line} - never the
 * region's own footprint against its own height.
 *
 * <p>No {@code of} element, deliberately. This is the one shape clause that is not "where does this
 * block sit" but "what shape is the box itself", so it reads {@link RegionGeometry#bounds()}
 * directly rather than a matcher's cells.
 *
 * <p>Ratio is height over the wider of the two horizontal sides, so a shaft that is deep in one
 * axis and narrow in the other still has to clear the bar on its worst axis - a room ten blocks
 * tall, one block wide and ten blocks long is a corridor stood on its side, not a shaft, and should
 * not read as one because its narrow axis happens to be vertical-looking on paper.
 */
public final class VerticalityClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:verticality";
    }

    @Override
    public Kind kind()
    {
        return Kind.SHAPE;
    }

    @Override
    public List<ClauseParamSpec> params()
    {
        return List.of(
                ClauseParamSpec.optional("min_ratio", ClauseParamSpec.Type.DOUBLE, 1.0d),
                ClauseParamSpec.required("ideal_ratio", ClauseParamSpec.Type.DOUBLE));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new VerticalityClause(params.getDouble("min_ratio"), params.getDouble("ideal_ratio"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        VerticalityClause verticality = (VerticalityClause) clause;
        return Map.of("min_ratio", verticality.minRatio(), "ideal_ratio", verticality.idealRatio());
    }
}

record VerticalityClause(double minRatio, double idealRatio) implements FormClause
{
    @Override
    public String typeId()
    {
        return "soulhome:verticality";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        RegionBounds bounds = geometry.bounds().orElse(null);

        if (bounds == null)
        {
            if (geometry.isEmpty())
            {
                return FormResult.of(0d, "nothing was indexed to measure the room's shape from");
            }

            bounds = ClauseMath.boundingBox(geometry.cells(), 0);
        }

        final double footprint = Math.max(bounds.sizeX(), bounds.sizeZ());
        final double ratio = bounds.sizeY() / footprint;

        final double confidence = ratio < this.minRatio ? 0d : ClauseMath.clamp01(ratio / this.idealRatio);

        return FormResult.of(confidence, diagnostic(ratio));
    }

    private String diagnostic(double ratio)
    {
        if (ratio >= this.idealRatio)
        {
            return "";
        }

        if (ratio < this.minRatio)
        {
            return "the room stands " + trim(ratio) + " times as tall as it is wide, not taller";
        }

        return "the room stands " + trim(ratio) + " times as tall as it is wide, short of the "
                + trim(this.idealRatio) + " wanted";
    }

    private static String trim(double value)
    {
        return Math.abs(value - Math.rint(value)) < 1.0e-6d
                ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override
    public String describe()
    {
        return "the room stands taller than it is wide, at least " + trim(this.minRatio)
                + "x, best at " + trim(this.idealRatio) + "x";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.idealRatio <= 0)
        {
            errors.add("'ideal_ratio' must be positive, got " + this.idealRatio);
        }

        if (this.minRatio < 0)
        {
            errors.add("'min_ratio' must not be negative, got " + this.minRatio);
        }

        return errors;
    }
}
