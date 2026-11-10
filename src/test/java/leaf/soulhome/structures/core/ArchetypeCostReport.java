/*
 * File created ~ 10 - 11 - 2026
 */

package leaf.soulhome.structures.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * #304: what each shipped archetype's tiers cost in materials, next to what they pay. Run by hand,
 * never by CI - see {@code tools/archetype_cost/README.md}.
 *
 * <p>{@link ArchetypeCeilingTest} keeps difficulty consistent in score; this shows where that is
 * not consistent in blocks. Forms and bonds are ignored (optional credit), so a tier signals alone
 * cannot reach is reported as {@code forms} rather than given a cost that would be a lie.
 */
public final class ArchetypeCostReport
{
    private static final Path COSTS = Path.of("tools", "archetype_cost", "costs.json");

    private final JsonObject blocks;
    private final JsonObject tags;
    private final double fallback;
    private final Set<String> unpriced = new TreeSet<>();

    private ArchetypeCostReport(JsonObject table)
    {
        this.blocks = table.getAsJsonObject("blocks");
        this.tags = table.getAsJsonObject("tags");
        this.fallback = table.get("default").getAsDouble();
    }

    public static void main(String[] args) throws IOException
    {
        final JsonObject table;

        try (Reader reader = Files.newBufferedReader(COSTS))
        {
            table = JsonParser.parseReader(reader).getAsJsonObject();
        }

        final ArchetypeCostReport report = new ArchetypeCostReport(table);
        final ScoringSettings settings = ScoringSettings.DEFAULTS;

        System.out.println("Ignores forms, bonds, density, detractors and time costs; greedy, not optimal. A tier shown as"
                + " 'forms' cannot be reached from signals alone.");
        System.out.printf(Locale.ROOT, "%-24s %-22s %7s %9s %9s %9s %10s%n",
                "archetype", "buff", "max", "tier1", "tier2", "tier3", "max/100c");

        for (ArchetypeDefinition archetype : ArchetypeJsonReader.shipped())
        {
            if (archetype.tiers().isEmpty() || archetype.buffs().isEmpty())
            {
                continue;
            }

            final ArchetypeDefinition.BuffSpec buff = archetype.buffs().get(0);
            final String[] costs = new String[3];
            double topCost = Double.NaN;

            for (int i = 0; i < 3; i++)
            {
                if (i >= archetype.tiers().size())
                {
                    costs[i] = "-";
                    continue;
                }

                final double cost = report.costTo(archetype, archetype.tiers().get(i).minScore(), settings);
                costs[i] = Double.isNaN(cost) ? "forms" : String.format(Locale.ROOT, "%.0f", cost);

                if (i == archetype.tiers().size() - 1)
                {
                    topCost = cost;
                }
            }

            final String efficiency = Double.isNaN(topCost) || topCost <= 0d
                    ? "-" : String.format(Locale.ROOT, "%.2f", buff.max() / topCost * 100d);

            System.out.printf(Locale.ROOT, "%-24s %-22s %7.2f %9s %9s %9s %10s%n",
                    archetype.id().replace("soulhome:", ""), buff.type().replace("soulhome:", ""), buff.max(),
                    costs[0], costs[1], costs[2], efficiency);
        }

        if (!report.unpriced.isEmpty())
        {
            System.out.println();
            System.out.println("unpriced (using default " + report.fallback + "): " + String.join(", ", report.unpriced));
        }
    }

    /** Cheapest cost found to reach {@code target} from signals alone, or NaN if they cannot. */
    double costTo(ArchetypeDefinition archetype, double target, ScoringSettings settings)
    {
        final List<ArchetypeDefinition.Signal> signals = archetype.signals();
        final int[] counts = new int[signals.size()];
        final double[] price = new double[signals.size()];
        double spent = 0d;

        for (int i = 0; i < signals.size(); i++)
        {
            price[i] = priceOf(signals.get(i).match());
        }

        // requirements first: a gate that is not met scores nothing at all
        for (ArchetypeDefinition.Requirement requirement : archetype.requirements())
        {
            int owner = -1;

            for (int i = 0; i < signals.size() && owner < 0; i++)
            {
                if (overlaps(requirement.match(), signals.get(i).match()))
                {
                    owner = i;
                }
            }

            if (owner >= 0)
            {
                final int add = Math.max(0, requirement.minCount() - counts[owner]);
                counts[owner] += add;
                spent += add * price[owner];
            }
            else
            {
                spent += requirement.minCount() * priceOf(requirement.match());
            }
        }

        while (score(archetype, counts, settings) < target)
        {
            final double now = score(archetype, counts, settings);
            int best = -1;
            double bestRate = 0d;

            for (int i = 0; i < signals.size(); i++)
            {
                if (counts[i] >= signals.get(i).cap() || signals.get(i).weight() <= 0d)
                {
                    continue;
                }

                counts[i]++;
                final double gain = score(archetype, counts, settings) - now;
                counts[i]--;
                final double rate = gain / price[i];

                if (rate > bestRate)
                {
                    bestRate = rate;
                    best = i;
                }
            }

            if (best < 0)
            {
                return Double.NaN;
            }

            counts[best]++;
            spent += price[best];
        }

        return spent;
    }

    private static double score(ArchetypeDefinition archetype, int[] counts, ScoringSettings settings)
    {
        double raw = 0d;
        final Set<String> roles = new HashSet<>();

        for (int i = 0; i < counts.length; i++)
        {
            if (counts[i] > 0)
            {
                final ArchetypeDefinition.Signal signal = archetype.signals().get(i);
                raw += signal.weight() * ArchetypeClassifier.curve(Math.min(counts[i], signal.cap()));
                roles.add(signal.role());
            }
        }

        return raw * (1d + settings.diversityBonusPerRole() * Math.max(0, roles.size() - 1));
    }

    private static boolean overlaps(BlockMatcher a, BlockMatcher b)
    {
        return a.blocks().stream().anyMatch(b.blocks()::contains) || a.tags().stream().anyMatch(b.tags()::contains);
    }

    private double priceOf(BlockMatcher matcher)
    {
        double cheapest = Double.POSITIVE_INFINITY;
        final List<String> missing = new ArrayList<>();

        for (String id : matcher.blocks())
        {
            if (this.blocks.has(id))
            {
                cheapest = Math.min(cheapest, this.blocks.get(id).getAsDouble());
            }
            else
            {
                missing.add(id);
            }
        }

        for (String id : matcher.tags())
        {
            if (this.tags.has(id))
            {
                cheapest = Math.min(cheapest, this.tags.get(id).getAsDouble());
            }
            else
            {
                missing.add("#" + id);
            }
        }

        if (Double.isInfinite(cheapest))
        {
            this.unpriced.addAll(missing);
            return this.fallback;
        }

        return cheapest;
    }
}
