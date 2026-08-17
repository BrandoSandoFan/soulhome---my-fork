/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides <i>when</i> a soulhome is worth rescanning.
 *
 * <p>Classification is a chunk sweep, so it must never run on a tick loop - but buffs still have
 * to feel responsive. The compromise is a debounce: a soulhome goes quiet for
 * {@code quietPeriod} before it is scanned, so placing forty bookshelves in a row costs one scan
 * rather than forty, with a {@code maxDelay} backstop so that someone building continuously still
 * sees their buffs update.
 *
 * <p>Keyed generically and driven by an explicit clock so the policy can be tested without a
 * server. Callers supply {@code now}; nothing here reads the system clock.
 *
 * <p>Not thread safe. Drive it from the server thread.
 */
public final class ScanDebouncer<K>
{
    /** Quiet time before a dirty soulhome is scanned. */
    public static final long DEFAULT_QUIET_PERIOD_MILLIS = 5_000L;

    /** Longest a continuously-edited soulhome will go without a scan. */
    public static final long DEFAULT_MAX_DELAY_MILLIS = 30_000L;

    private final long quietPeriodMillis;
    private final long maxDelayMillis;

    private final Map<K, Pending> pending = new LinkedHashMap<>();
    private final Set<K> inFlight = new LinkedHashSet<>();

    public ScanDebouncer(long quietPeriodMillis, long maxDelayMillis)
    {
        if (quietPeriodMillis < 0 || maxDelayMillis < 0)
        {
            throw new IllegalArgumentException("Debounce periods must not be negative");
        }

        this.quietPeriodMillis = quietPeriodMillis;
        this.maxDelayMillis = maxDelayMillis;
    }

    public ScanDebouncer()
    {
        this(DEFAULT_QUIET_PERIOD_MILLIS, DEFAULT_MAX_DELAY_MILLIS);
    }

    /** A block was placed or broken. Resets the quiet timer but not the backstop. */
    public void markDirty(K key, long now)
    {
        Pending entry = this.pending.get(key);

        if (entry == null)
        {
            this.pending.put(key, new Pending(now, now, false));
            return;
        }

        entry.lastDirtyAt = now;
    }

    /**
     * Scan at the next opportunity, skipping the quiet period. Used when a player leaves their
     * soulhome - the natural moment to say "here is what you built" - and on level load.
     */
    public void requestNow(K key, long now)
    {
        Pending entry = this.pending.get(key);

        if (entry == null)
        {
            this.pending.put(key, new Pending(now, now, true));
            return;
        }

        entry.immediate = true;
    }

    /**
     * Take everything ready to be scanned, marking it in flight. A key stays in flight until
     * {@link #release} so a slow scan cannot be started twice.
     *
     * <p>Edits made while a scan is in flight re-dirty the key, so it will be picked up again on a
     * later call rather than being lost.
     */
    public List<K> claimDue(long now)
    {
        List<K> due = new ArrayList<>();

        for (Map.Entry<K, Pending> entry : this.pending.entrySet())
        {
            if (this.inFlight.contains(entry.getKey()))
            {
                continue;
            }

            if (entry.getValue().isDue(now, this.quietPeriodMillis, this.maxDelayMillis))
            {
                due.add(entry.getKey());
            }
        }

        for (K key : due)
        {
            this.pending.remove(key);
            this.inFlight.add(key);
        }

        return due;
    }

    /** A scan finished. The key becomes eligible again. */
    public void release(K key)
    {
        this.inFlight.remove(key);
    }

    /** Drop a key entirely, for a soulhome whose level has unloaded. */
    public void forget(K key)
    {
        this.pending.remove(key);
        this.inFlight.remove(key);
    }

    public boolean isPending(K key)
    {
        return this.pending.containsKey(key);
    }

    public boolean isInFlight(K key)
    {
        return this.inFlight.contains(key);
    }

    public boolean isIdle()
    {
        return this.pending.isEmpty() && this.inFlight.isEmpty();
    }

    private static final class Pending
    {
        private final long firstDirtyAt;
        private long lastDirtyAt;
        private boolean immediate;

        private Pending(long firstDirtyAt, long lastDirtyAt, boolean immediate)
        {
            this.firstDirtyAt = firstDirtyAt;
            this.lastDirtyAt = lastDirtyAt;
            this.immediate = immediate;
        }

        private boolean isDue(long now, long quietPeriod, long maxDelay)
        {
            return this.immediate
                    || now - this.lastDirtyAt >= quietPeriod
                    || now - this.firstDirtyAt >= maxDelay;
        }
    }
}
