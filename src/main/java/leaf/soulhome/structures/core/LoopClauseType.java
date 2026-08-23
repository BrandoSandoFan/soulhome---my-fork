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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code soulhome:loop}: is the element a closed circuit - a track that runs back into itself,
 * not just a lot of rail. See #31.
 *
 * <h2>Never check for a circle, check for a closed circuit</h2>
 *
 * The element's cells become a graph (see {@link CellGraphs}), and the largest connected component
 * is repeatedly peeled: every vertex of degree 1 - a leaf hanging off the loop - is removed, and
 * removing it can turn a neighbour into a new leaf, so this repeats to a fixed point (a standard
 * 2-core computation). What survives is exactly the cells lying on some cycle; what does not is
 * spurs and dead ends. {@code closure} is the surviving fraction, {@code extent} is how large that
 * circuit is against {@code ideal_cells} (floored to 0 below {@code min_cells} - a tiny dense clump
 * such as a rail heap piled into a cube is technically "cyclic" under this test but is nowhere near
 * a real circuit's size), and {@code confidence = closure * extent}.
 *
 * <h2>A near-miss is not the same as never trying</h2>
 *
 * Peeling a graph with no cycle at all - a straight line, or a ring with one block pulled out of
 * it - removes every vertex, leaving nothing. Read literally, both would grade as "0 closure",
 * which is correct for the straight line but wrong for the almost-ring: a rail loop missing one
 * block reads to a player as "nearly a circuit", not as "not attempting to be one", and #31 is
 * explicit that this case must drop sharply rather than vanish.
 *
 * <p>The two are told apart by geometry, not by the graph alone: when peeling empties the
 * component, the two positions it peeled down to last (the diameter of what is, at that point,
 * necessarily a tree - see the graph-theory note on {@link #peelToCore}) are found by a standard
 * double-BFS. Their straight-line distance is the size of the gap that would close the loop. A
 * one-block gap (the two ends of a broken ring, distance 2 apart) earns real but reduced credit; a
 * gap larger than a handful of cells - the straight line's two ends are the extreme case - earns
 * none. This is a deliberate design choice, not a derivation from the grammar: the issue leaves the
 * exact falloff to engineering judgement, and a {@code 1/gap} curve clamped to a small window is
 * the simplest curve that satisfies both required cases without inventing a shape-specific special
 * case for "heap" or "horseshoe".
 */
public final class LoopClauseType implements FormClauseType
{
    @Override
    public String id()
    {
        return "soulhome:loop";
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
                ClauseParamSpec.required("of", ClauseParamSpec.Type.ELEMENT),
                ClauseParamSpec.optional("min_cells", ClauseParamSpec.Type.INT, 8),
                ClauseParamSpec.required("ideal_cells", ClauseParamSpec.Type.INT),
                ClauseParamSpec.optional("connectivity", ClauseParamSpec.Type.STRING, "planar"));
    }

    @Override
    public FormClause create(ClauseParams params)
    {
        return new LoopClause(
                params.getElement("of"), params.getInt("min_cells"), params.getInt("ideal_cells"),
                params.getString("connectivity"));
    }

    @Override
    public Map<String, Object> encode(FormClause clause)
    {
        LoopClause loop = (LoopClause) clause;
        return Map.of(
                "of", loop.of(), "min_cells", loop.minCells(), "ideal_cells", loop.idealCells(),
                "connectivity", loop.connectivity());
    }
}

record LoopClause(String of, int minCells, int idealCells, String connectivity) implements FormClause
{
    /** Past this many cells of straight-line gap, a near-miss earns no closure credit at all. */
    private static final int GAP_CREDIT_LIMIT = 4;

    @Override
    public String typeId()
    {
        return "soulhome:loop";
    }

    @Override
    public FormResult evaluate(RegionGeometry geometry, Map<String, BlockMatcher> elements)
    {
        BlockMatcher matcher = elements.get(this.of);

        if (matcher == null)
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        List<RegionGeometry.Cell> cells = geometry.cellsMatching(matcher);

        if (cells.isEmpty())
        {
            return FormResult.of(0d, ClauseMath.missingElementDiagnostic(this.of));
        }

        List<int[]> offsets = "orthogonal".equalsIgnoreCase(this.connectivity) ? CellGraphs.OFFSETS_6 : CellGraphs.OFFSETS_26;
        List<RegionGeometry.Cell> component = CellGraphs.largestComponent(cells, offsets);

        Map<CellGraphs.Pos, List<CellGraphs.Pos>> adjacency = buildAdjacency(component, offsets);
        Set<CellGraphs.Pos> core = peelToCore(adjacency);

        final int componentSize = component.size();
        final int strictCyclicCells = core.size();

        double closure;
        int cyclicForExtent;
        int gap = -1;

        if (strictCyclicCells > 0)
        {
            closure = (double) strictCyclicCells / componentSize;
            cyclicForExtent = strictCyclicCells;
        }
        else if (componentSize < 2)
        {
            closure = 0d;
            cyclicForExtent = componentSize;
        }
        else
        {
            Map<CellGraphs.Pos, RegionGeometry.Cell> byPos = CellGraphs.index(component);
            CellGraphs.Pos[] ends = diameterEndpoints(adjacency);
            gap = (int) Math.round(ClauseMath.distance(byPos.get(ends[0]), byPos.get(ends[1]), "chebyshev"));
            closure = gapCredit(gap);
            cyclicForExtent = componentSize;
        }

        double extent = cyclicForExtent < this.minCells ? 0d : ClauseMath.clamp01((double) cyclicForExtent / this.idealCells);
        double confidence = closure * extent;

        return FormResult.of(confidence, diagnostic(cells.size(), componentSize, strictCyclicCells, gap, closure, extent));
    }

    private static double gapCredit(int gap)
    {
        if (gap <= 1)
        {
            return 1d;
        }

        return gap > GAP_CREDIT_LIMIT ? 0d : 1d / gap;
    }

    private String diagnostic(int totalCells, int componentSize, int strictCyclicCells, int gap, double closure, double extent)
    {
        if (strictCyclicCells > 0)
        {
            if (strictCyclicCells < componentSize)
            {
                return totalCells + " '" + this.of + "', on a circuit of " + strictCyclicCells + " - the rest are spurs";
            }

            if (extent < 1d)
            {
                return "the circuit is " + strictCyclicCells + " '" + this.of + "', short of the " + this.idealCells + " wanted";
            }

            return "";
        }

        if (closure > 0d)
        {
            return totalCells + " '" + this.of + "', but the longest closed circuit is 0 - a " + gap
                    + "-cell gap would close it";
        }

        return totalCells + " '" + this.of + "', but the longest closed circuit is 0 - join the two ends";
    }

    private static Map<CellGraphs.Pos, List<CellGraphs.Pos>> buildAdjacency(List<RegionGeometry.Cell> component, List<int[]> offsets)
    {
        Map<CellGraphs.Pos, RegionGeometry.Cell> byPos = CellGraphs.index(component);
        Map<CellGraphs.Pos, List<CellGraphs.Pos>> adjacency = new HashMap<>();

        for (CellGraphs.Pos pos : byPos.keySet())
        {
            List<CellGraphs.Pos> neighbours = new ArrayList<>();

            for (int[] offset : offsets)
            {
                CellGraphs.Pos candidate = new CellGraphs.Pos(pos.x() + offset[0], pos.y() + offset[1], pos.z() + offset[2]);

                if (byPos.containsKey(candidate))
                {
                    neighbours.add(candidate);
                }
            }

            adjacency.put(pos, neighbours);
        }

        return adjacency;
    }

    /**
     * The 2-core: repeatedly strip every vertex of degree at most 1 until none remain. What is left
     * is exactly the cells lying on a cycle - a graph-theory fact this leans on rather than proves
     * here: removing a degree-1 vertex can never disconnect a cycle from the rest of the graph, so
     * every cycle's vertices survive every round untouched, while any tree (a graph with no cycle at
     * all) is peeled down to nothing.
     */
    private static Set<CellGraphs.Pos> peelToCore(Map<CellGraphs.Pos, List<CellGraphs.Pos>> adjacency)
    {
        Map<CellGraphs.Pos, Integer> degree = new HashMap<>();

        for (Map.Entry<CellGraphs.Pos, List<CellGraphs.Pos>> entry : adjacency.entrySet())
        {
            degree.put(entry.getKey(), entry.getValue().size());
        }

        Deque<CellGraphs.Pos> queue = new ArrayDeque<>();

        for (Map.Entry<CellGraphs.Pos, Integer> entry : degree.entrySet())
        {
            if (entry.getValue() <= 1)
            {
                queue.add(entry.getKey());
            }
        }

        Set<CellGraphs.Pos> removed = new HashSet<>();

        while (!queue.isEmpty())
        {
            CellGraphs.Pos pos = queue.poll();

            if (removed.contains(pos) || degree.get(pos) > 1)
            {
                continue;
            }

            removed.add(pos);

            for (CellGraphs.Pos neighbour : adjacency.getOrDefault(pos, List.of()))
            {
                if (removed.contains(neighbour))
                {
                    continue;
                }

                int newDegree = degree.get(neighbour) - 1;
                degree.put(neighbour, newDegree);

                if (newDegree <= 1)
                {
                    queue.add(neighbour);
                }
            }
        }

        Set<CellGraphs.Pos> core = new HashSet<>(adjacency.keySet());
        core.removeAll(removed);
        return core;
    }

    /**
     * The two farthest-apart vertices of a tree, by the standard double-BFS: BFS from any vertex to
     * find the farthest one, then BFS again from there. Only ever called once {@link #peelToCore}
     * has emptied the component, at which point it is provably a tree (see that method's doc), so a
     * single BFS pair is well-defined regardless of which vertex the first search started from.
     */
    private static CellGraphs.Pos[] diameterEndpoints(Map<CellGraphs.Pos, List<CellGraphs.Pos>> adjacency)
    {
        CellGraphs.Pos any = adjacency.keySet().iterator().next();
        CellGraphs.Pos first = farthestFrom(any, adjacency);
        CellGraphs.Pos second = farthestFrom(first, adjacency);
        return new CellGraphs.Pos[]{first, second};
    }

    private static CellGraphs.Pos farthestFrom(CellGraphs.Pos start, Map<CellGraphs.Pos, List<CellGraphs.Pos>> adjacency)
    {
        Map<CellGraphs.Pos, Integer> distance = new HashMap<>();
        Deque<CellGraphs.Pos> queue = new ArrayDeque<>();
        distance.put(start, 0);
        queue.add(start);
        CellGraphs.Pos farthest = start;

        while (!queue.isEmpty())
        {
            CellGraphs.Pos pos = queue.poll();

            if (distance.get(pos) > distance.get(farthest))
            {
                farthest = pos;
            }

            for (CellGraphs.Pos neighbour : adjacency.getOrDefault(pos, List.of()))
            {
                if (!distance.containsKey(neighbour))
                {
                    distance.put(neighbour, distance.get(pos) + 1);
                    queue.add(neighbour);
                }
            }
        }

        return farthest;
    }

    @Override
    public String describe()
    {
        return "'" + this.of + "' forms a closed circuit";
    }

    @Override
    public List<String> validationErrors(Set<String> elementNames)
    {
        List<String> errors = new ArrayList<>();

        if (this.idealCells < 1)
        {
            errors.add("'ideal_cells' must be at least 1, got " + this.idealCells);
        }

        String connectivityLower = this.connectivity == null ? "" : this.connectivity.toLowerCase(Locale.ROOT);

        if (!connectivityLower.equals("planar") && !connectivityLower.equals("orthogonal"))
        {
            errors.add("'connectivity' must be 'planar' or 'orthogonal', got '" + this.connectivity + "'");
        }

        return errors;
    }
}
