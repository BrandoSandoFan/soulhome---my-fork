/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * A {@link GroundSurvey} built from ASCII art, so terrain-growth cases read as the island they
 * describe rather than as coordinate arithmetic - the same bargain {@link GridVolume} makes for the
 * scanner.
 *
 * <p>Rows are Z and columns are X, and the grid is centred on the origin, because everything about
 * growth is measured from there: the verge, the ground limit and {@code /soulhome ascent}'s reach
 * are all half-extents.
 *
 * <pre>{@code
 * GroundGrid.of(70,
 *     ".....",
 *     ".###.",
 *     ".###.",     // a three-by-three island at the origin
 *     ".###.",
 *     ".....");
 * }</pre>
 *
 * <p>Symbols: {@code #} ground, {@code B} built, {@code .} void, {@code ?} unreadable. A digit
 * {@code 0-9} is ground whose surface sits that many blocks above the floor, for the cases about an
 * apron inheriting its height.
 */
public final class GroundGrid
{
    private GroundGrid()
    {
    }

    public static GroundSurvey of(int floorY, String... rows)
    {
        if (rows.length == 0)
        {
            throw new IllegalArgumentException("A ground grid needs at least one row");
        }

        final int sizeZ = rows.length;
        final int sizeX = rows[0].length();

        for (int z = 0; z < sizeZ; z++)
        {
            if (rows[z].length() != sizeX)
            {
                throw new IllegalArgumentException(
                        "Row " + z + " is " + rows[z].length() + " wide, expected " + sizeX);
            }
        }

        final int originX = sizeX / 2;
        final int originZ = sizeZ / 2;

        final GroundSurvey survey = new GroundSurvey(-originX, -originZ, sizeX - 1 - originX, sizeZ - 1 - originZ);

        for (int z = 0; z < sizeZ; z++)
        {
            for (int x = 0; x < sizeX; x++)
            {
                final char symbol = rows[z].charAt(x);
                final int worldX = x - originX;
                final int worldZ = z - originZ;

                switch (symbol)
                {
                    case '#' -> survey.set(worldX, worldZ, GroundSurvey.Kind.GROUND, floorY);
                    case 'B' -> survey.set(worldX, worldZ, GroundSurvey.Kind.BUILT, floorY);
                    case '.' -> survey.set(worldX, worldZ, GroundSurvey.Kind.VOID, 0);
                    case '?' -> survey.set(worldX, worldZ, GroundSurvey.Kind.UNKNOWN, 0);
                    default ->
                    {
                        if (symbol < '0' || symbol > '9')
                        {
                            throw new IllegalArgumentException("Unknown ground grid symbol '" + symbol + "'");
                        }

                        survey.set(worldX, worldZ, GroundSurvey.Kind.GROUND, floorY + (symbol - '0'));
                    }
                }
            }
        }

        return survey;
    }

    /**
     * A square island of ground centred on the origin, inside a square of void reaching {@code
     * extent} - for the cases that care about how far a band reaches rather than what shape it
     * follows.
     */
    public static GroundSurvey island(int floorY, int islandHalfExtent, int extent)
    {
        final GroundSurvey survey = new GroundSurvey(-extent, -extent, extent, extent);

        for (int x = -extent; x <= extent; x++)
        {
            for (int z = -extent; z <= extent; z++)
            {
                final boolean ground = Math.abs(x) <= islandHalfExtent && Math.abs(z) <= islandHalfExtent;
                survey.set(x, z, ground ? GroundSurvey.Kind.GROUND : GroundSurvey.Kind.VOID, ground ? floorY : 0);
            }
        }

        return survey;
    }

    /** The plan as a grid of characters, so a failure prints the coastline it actually grew. */
    public static String render(GroundSurvey survey, ApronPlan plan)
    {
        final char[][] canvas = new char[survey.sizeZ()][survey.sizeX()];

        for (int x = survey.minX(); x <= survey.maxX(); x++)
        {
            for (int z = survey.minZ(); z <= survey.maxZ(); z++)
            {
                canvas[z - survey.minZ()][x - survey.minX()] = switch (survey.kindAt(x, z))
                {
                    case GROUND -> '#';
                    case BUILT -> 'B';
                    case VOID -> '.';
                    case UNKNOWN -> '?';
                };
            }
        }

        for (ApronPlan.Column column : plan.columns())
        {
            canvas[column.z() - survey.minZ()][column.x() - survey.minX()] = 'o';
        }

        final StringBuilder text = new StringBuilder();

        for (char[] row : canvas)
        {
            text.append(new String(row)).append('\n');
        }

        return text.toString();
    }

    /** Whether the plan covers this column, for a case that asks about one place in particular. */
    public static boolean planned(ApronPlan plan, int x, int z)
    {
        return plan.columns().stream().anyMatch(column -> column.x() == x && column.z() == z);
    }
}
