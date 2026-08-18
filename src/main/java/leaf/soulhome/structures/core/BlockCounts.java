/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A multiset of blocks. This is what a region hands to the classifier.
 *
 * <p>Iteration order is insertion order, and {@link #sortedEntries()} gives a stable order
 * independent of how the world was walked - region identity hashes depend on that stability.
 */
public final class BlockCounts
{
    private static final BlockCounts EMPTY = new BlockCounts(Map.of());

    private final Map<BlockSignature, Integer> counts;

    private BlockCounts(Map<BlockSignature, Integer> counts)
    {
        this.counts = counts;
    }

    public static BlockCounts empty()
    {
        return EMPTY;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Total number of blocks matching the predicate. This is the primitive both requirements and
     * signals are counted with.
     */
    public int count(BlockMatcher matcher)
    {
        int total = 0;

        for (Map.Entry<BlockSignature, Integer> entry : this.counts.entrySet())
        {
            if (matcher.test(entry.getKey()))
            {
                total += entry.getValue();
            }
        }

        return total;
    }

    public int countOf(BlockSignature signature)
    {
        return this.counts.getOrDefault(signature, 0);
    }

    /** Total number of blocks, counting duplicates. */
    public int total()
    {
        int total = 0;

        for (int count : this.counts.values())
        {
            total += count;
        }

        return total;
    }

    /** Number of distinct block types. */
    public int distinct()
    {
        return this.counts.size();
    }

    public boolean isEmpty()
    {
        return this.counts.isEmpty();
    }

    public Map<BlockSignature, Integer> asMap()
    {
        return Collections.unmodifiableMap(this.counts);
    }

    /**
     * Entries ordered by block id, so that two scans of the same build produce the same sequence.
     */
    public List<Map.Entry<BlockSignature, Integer>> sortedEntries()
    {
        List<Map.Entry<BlockSignature, Integer>> entries = new ArrayList<>(this.counts.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().id()));
        return entries;
    }

    public BlockCounts plus(BlockCounts other)
    {
        if (other.isEmpty())
        {
            return this;
        }

        if (this.isEmpty())
        {
            return other;
        }

        Builder builder = builder();
        builder.addAll(this);
        builder.addAll(other);
        return builder.build();
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("BlockCounts{");
        boolean first = true;

        for (Map.Entry<BlockSignature, Integer> entry : sortedEntries())
        {
            if (!first)
            {
                builder.append(", ");
            }

            builder.append(entry.getKey().id()).append('=').append(entry.getValue());
            first = false;
        }

        return builder.append('}').toString();
    }

    public static final class Builder
    {
        private final Map<BlockSignature, Integer> counts = new LinkedHashMap<>();

        public Builder add(BlockSignature signature)
        {
            return add(signature, 1);
        }

        public Builder add(BlockSignature signature, int amount)
        {
            if (signature == null || amount <= 0)
            {
                return this;
            }

            this.counts.merge(signature, amount, Integer::sum);
            return this;
        }

        public Builder addAll(BlockCounts other)
        {
            for (Map.Entry<BlockSignature, Integer> entry : other.counts.entrySet())
            {
                add(entry.getKey(), entry.getValue());
            }

            return this;
        }

        public boolean isEmpty()
        {
            return this.counts.isEmpty();
        }

        public BlockCounts build()
        {
            return this.counts.isEmpty() ? EMPTY : new BlockCounts(new LinkedHashMap<>(this.counts));
        }
    }
}
