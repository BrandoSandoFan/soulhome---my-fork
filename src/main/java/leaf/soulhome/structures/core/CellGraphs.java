/*
 * File created ~ 23 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connected-component and adjacency plumbing shared by the shape/relation clauses that need to
 * ask "which of these cells touch each other" - {@code loop} and {@code cluster} over the element
 * itself, {@code along} over the cells within range of another element, {@code surrounds} and
 * {@code inside} over the {@code to} element's own clusters.
 *
 * <p>Adjacency is always expressed as a fixed offset list rather than a distance predicate,
 * looked up through a position map rather than compared pairwise: a room's element counts are
 * small in practice, but a datapack element matching most of a large room should not turn a
 * clause evaluation into an {@code O(n^2)} scan over every pair of cells.
 */
final class CellGraphs
{
    /** 6-neighbour: one step along exactly one axis. What {@code loop}'s {@code orthogonal} means. */
    static final List<int[]> OFFSETS_6 = offsetsWithin(1, false);

    /**
     * 26-neighbour: any step of at most 1 on every axis. What {@code loop}'s {@code planar} means -
     * "8 horizontal neighbours, with a step of the rail's climb or descent folded in" is exactly
     * this set, since every one of the 26 combinations is some horizontal offset paired with a
     * vertical step of -1, 0 or 1. Also the default adjacency for {@code cluster} and for grouping
     * the cells {@code along} finds within range of another element into runs.
     */
    static final List<int[]> OFFSETS_26 = offsetsWithin(1, true);

    private CellGraphs()
    {
    }

    /** Every offset with Chebyshev distance at most {@code radius} - "cells within N of each other". */
    static List<int[]> chebyshevOffsets(int radius)
    {
        return offsetsWithin(radius, true);
    }

    /** Every offset with every coordinate in {@code [-radius, radius]}, excluding the origin. */
    static List<int[]> offsetsWithin(int radius, boolean includeDiagonals)
    {
        List<int[]> offsets = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                    {
                        continue;
                    }

                    if (!includeDiagonals && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1)
                    {
                        continue;
                    }

                    offsets.add(new int[]{dx, dy, dz});
                }
            }
        }

        return List.copyOf(offsets);
    }

    static Map<Pos, RegionGeometry.Cell> index(List<RegionGeometry.Cell> cells)
    {
        Map<Pos, RegionGeometry.Cell> byPos = new HashMap<>(cells.size() * 2);

        for (RegionGeometry.Cell cell : cells)
        {
            byPos.put(new Pos(cell.x(), cell.y(), cell.z()), cell);
        }

        return byPos;
    }

    /**
     * Connected components of {@code cells} under the given adjacency offsets, in no particular
     * order. A cell reachable from another only through cells outside {@code cells} is in a
     * different component - the graph is built purely from what is actually indexed.
     */
    static List<List<RegionGeometry.Cell>> components(List<RegionGeometry.Cell> cells, List<int[]> offsets)
    {
        Map<Pos, RegionGeometry.Cell> byPos = index(cells);
        Set<Pos> visited = new HashSet<>();
        List<List<RegionGeometry.Cell>> result = new ArrayList<>();

        for (RegionGeometry.Cell start : cells)
        {
            Pos startPos = new Pos(start.x(), start.y(), start.z());

            if (!visited.add(startPos))
            {
                continue;
            }

            List<RegionGeometry.Cell> component = new ArrayList<>();
            Deque<Pos> stack = new ArrayDeque<>();
            stack.push(startPos);

            while (!stack.isEmpty())
            {
                Pos pos = stack.pop();
                component.add(byPos.get(pos));

                for (int[] offset : offsets)
                {
                    Pos neighbour = new Pos(pos.x() + offset[0], pos.y() + offset[1], pos.z() + offset[2]);

                    if (byPos.containsKey(neighbour) && visited.add(neighbour))
                    {
                        stack.push(neighbour);
                    }
                }
            }

            result.add(component);
        }

        return result;
    }

    /** The largest component, or an empty list if {@code cells} is empty. */
    static List<RegionGeometry.Cell> largestComponent(List<RegionGeometry.Cell> cells, List<int[]> offsets)
    {
        List<RegionGeometry.Cell> largest = List.of();

        for (List<RegionGeometry.Cell> component : components(cells, offsets))
        {
            if (component.size() > largest.size())
            {
                largest = component;
            }
        }

        return largest;
    }

    /** The mean position of a set of cells - a cluster's centre, for {@code surrounds}/{@code inside}. */
    static Centroid centroidOf(List<RegionGeometry.Cell> cells)
    {
        double x = 0d;
        double y = 0d;
        double z = 0d;

        for (RegionGeometry.Cell cell : cells)
        {
            x += cell.x();
            y += cell.y();
            z += cell.z();
        }

        final int n = cells.size();
        return new Centroid(x / n, y / n, z / n);
    }

    /** A position with no signature - the key adjacency and blocked-cell lookups both need. */
    record Pos(int x, int y, int z)
    {
    }

    record Centroid(double x, double y, double z)
    {
    }
}
