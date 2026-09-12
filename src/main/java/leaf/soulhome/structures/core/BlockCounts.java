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
 * <p>Since #136 and #137 a block's credit to a region is not always a whole block: a wall shared
 * between two rooms is worth half to each, so that partitioning a space does not manufacture
 * evidence, and a slab between two stacked rooms belongs to the room that stands on it. See
 * {@link RegionScanner}'s "A shared wall is one wall" for the rule. The classifier scores
 * {@link #credit} exactly; {@link #count} is the whole-block view for reports, tests and anything
 * that has to print "x6" rather than "x3.5", and it rounds down so a region can never be told it
 * has more of something than it was credited with.
 *
 * <p>Iteration order is insertion order, and {@link #sortedEntries()} gives a stable order
 * independent of how the world was walked - region identity hashes depend on that stability.
 */
public final class BlockCounts
{
    private static final BlockCounts EMPTY = new BlockCounts(Map.of());

    /**
     * A shared cell's credit is a unit fraction of at most one sixth - six faces, so at most six
     * rooms can touch one cell - and a region's total is a short sum of those, so this is far
     * below anything a real value could differ by and well above the noise a sum of them
     * accumulates.
     */
    private static final double EPSILON = 1.0e-6d;

    private final Map<BlockSignature, Double> credit;

    private BlockCounts(Map<BlockSignature, Double> credit)
    {
        this.credit = credit;
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
     * Exact credit for every block matching the predicate. This is the primitive both
     * requirements and signals are scored with.
     */
    public double credit(BlockMatcher matcher)
    {
        double total = 0d;

        for (Map.Entry<BlockSignature, Double> entry : this.credit.entrySet())
        {
            if (matcher.test(entry.getKey()))
            {
                total += entry.getValue();
            }
        }

        return total;
    }

    /**
     * Whole blocks matching the predicate - {@link #credit} rounded down. Half a shared wall of
     * seven bookshelves reads as three, never four: a report must not promise more than the
     * classifier will score.
     */
    public int count(BlockMatcher matcher)
    {
        return wholeBlocks(credit(matcher));
    }

    public double creditOf(BlockSignature signature)
    {
        return this.credit.getOrDefault(signature, 0d);
    }

    public int countOf(BlockSignature signature)
    {
        return wholeBlocks(creditOf(signature));
    }

    /** Total credit across every block, counting duplicates. */
    public double totalCredit()
    {
        double total = 0d;

        for (double value : this.credit.values())
        {
            total += value;
        }

        return total;
    }

    /** Total whole blocks, counting duplicates - {@link #totalCredit} rounded down. */
    public int total()
    {
        return wholeBlocks(totalCredit());
    }

    /** Number of distinct block types. */
    public int distinct()
    {
        return this.credit.size();
    }

    public boolean isEmpty()
    {
        return this.credit.isEmpty();
    }

    public Map<BlockSignature, Double> asMap()
    {
        return Collections.unmodifiableMap(this.credit);
    }

    /**
     * Entries ordered by block id, so that two scans of the same build produce the same sequence.
     */
    public List<Map.Entry<BlockSignature, Double>> sortedEntries()
    {
        List<Map.Entry<BlockSignature, Double>> entries = new ArrayList<>(this.credit.entrySet());
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

    /**
     * A credit as whole blocks, tolerating the last bit of a sum of sixths - {@code 0.5 + 0.5}
     * lands on {@code 1.0} exactly, but {@code 1/3 + 1/3 + 1/3} need not, and a wall three rooms
     * share should still read as one block, not zero.
     */
    static int wholeBlocks(double credit)
    {
        return (int) Math.floor(credit + EPSILON);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("BlockCounts{");
        boolean first = true;

        for (Map.Entry<BlockSignature, Double> entry : sortedEntries())
        {
            if (!first)
            {
                builder.append(", ");
            }

            builder.append(entry.getKey().id()).append('=').append(formatCredit(entry.getValue()));
            first = false;
        }

        return builder.append('}').toString();
    }

    private static String formatCredit(double value)
    {
        final int whole = wholeBlocks(value);
        return Math.abs(value - whole) < EPSILON ? Integer.toString(whole) : Double.toString(value);
    }

    public static final class Builder
    {
        private final Map<BlockSignature, Double> credit = new LinkedHashMap<>();

        public Builder add(BlockSignature signature)
        {
            return add(signature, 1d);
        }

        public Builder add(BlockSignature signature, int amount)
        {
            return add(signature, (double) amount);
        }

        /**
         * @param amount how much of a block this is worth to the region - one for a block the
         *               region owns outright, a fraction for one it shares. See
         *               {@link RegionScanner}.
         */
        public Builder add(BlockSignature signature, double amount)
        {
            if (signature == null || amount <= 0d)
            {
                return this;
            }

            this.credit.merge(signature, amount, Double::sum);
            return this;
        }

        public Builder addAll(BlockCounts other)
        {
            for (Map.Entry<BlockSignature, Double> entry : other.credit.entrySet())
            {
                add(entry.getKey(), entry.getValue());
            }

            return this;
        }

        public boolean isEmpty()
        {
            return this.credit.isEmpty();
        }

        public BlockCounts build()
        {
            return this.credit.isEmpty() ? EMPTY : new BlockCounts(new LinkedHashMap<>(this.credit));
        }
    }
}
