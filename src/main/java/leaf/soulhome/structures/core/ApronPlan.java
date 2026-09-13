/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.List;

/**
 * Where new ground goes, and what each column of it is made of - the whole of one run of terrain
 * growth (#158), decided before a single block is written.
 *
 * <p>Every column names the ground column it grew from. That is what carries the island's own
 * style outward without a table of three special cases: the surface block and the soil under it are
 * copied from {@code sourceX, sourceZ} at placement time, so a snowy soul grows snow and a fourth
 * template added later grows correctly with no Java change.
 */
public record ApronPlan(List<Column> columns, int groundLimit, int bandWidth)
{
    /** The empty plan - nothing to grow, which is the usual answer. */
    public static final ApronPlan NOTHING = new ApronPlan(List.of(), 0, 0);

    public ApronPlan
    {
        columns = List.copyOf(columns);
    }

    public boolean isEmpty()
    {
        return this.columns.isEmpty();
    }

    public int size()
    {
        return this.columns.size();
    }

    /**
     * One column of new ground.
     *
     * @param x        where it goes
     * @param z        where it goes
     * @param surfaceY the Y its top block lands on, inherited from its source so the apron
     *                 continues the island's surface rather than sitting at a datum of its own
     * @param sourceX  the ground column it grew from, and whose blocks it is made of
     * @param sourceZ  the ground column it grew from, and whose blocks it is made of
     */
    public record Column(int x, int z, int surfaceY, int sourceX, int sourceZ)
    {
    }
}
