/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanDebouncerTest
{
    private static final long QUIET = 5_000L;
    private static final long MAX_DELAY = 30_000L;

    private static ScanDebouncer<String> debouncer()
    {
        return new ScanDebouncer<>(QUIET, MAX_DELAY);
    }

    @Test
    @DisplayName("a dirty soulhome waits out the quiet period")
    void quietPeriodIsRespected()
    {
        ScanDebouncer<String> debouncer = debouncer();
        debouncer.markDirty("soul", 0L);

        assertEquals(List.of(), debouncer.claimDue(4_999L), "still within the quiet period");
        assertEquals(List.of("soul"), debouncer.claimDue(5_000L));
    }

    @Test
    @DisplayName("a burst of edits costs one scan, not forty")
    void repeatedEditsCollapseIntoOneScan()
    {
        // placing a wall of bookshelves one block at a time is the case this exists for
        ScanDebouncer<String> debouncer = debouncer();

        for (long tick = 0; tick <= 20_000L; tick += 1_000L)
        {
            debouncer.markDirty("soul", tick);
            assertEquals(List.of(), debouncer.claimDue(tick), "no scan while the player is still building");
        }

        assertEquals(List.of("soul"), debouncer.claimDue(25_000L));
    }

    @Test
    @DisplayName("continuous building still gets scanned eventually")
    void maxDelayIsABackstop()
    {
        ScanDebouncer<String> debouncer = debouncer();

        for (long tick = 0; tick < MAX_DELAY; tick += 1_000L)
        {
            debouncer.markDirty("soul", tick);
        }

        // never quiet for five seconds, but thirty have passed since the first edit
        assertEquals(List.of("soul"), debouncer.claimDue(MAX_DELAY));
    }

    @Test
    @DisplayName("leaving your soulhome scans it straight away")
    void immediateRequestSkipsTheWait()
    {
        ScanDebouncer<String> debouncer = debouncer();

        debouncer.markDirty("soul", 0L);
        debouncer.requestNow("soul", 100L);

        assertEquals(List.of("soul"), debouncer.claimDue(100L));
    }

    @Test
    @DisplayName("a scan in flight is not started twice")
    void inFlightKeysAreNotReclaimed()
    {
        ScanDebouncer<String> debouncer = debouncer();
        debouncer.markDirty("soul", 0L);

        assertEquals(List.of("soul"), debouncer.claimDue(10_000L));
        assertTrue(debouncer.isInFlight("soul"));

        debouncer.markDirty("soul", 10_001L);
        assertEquals(List.of(), debouncer.claimDue(20_000L), "the first scan has not finished yet");

        debouncer.release("soul");
        assertEquals(List.of("soul"), debouncer.claimDue(20_000L), "and now the newer edit is picked up");
    }

    @Test
    @DisplayName("an edit during a scan is not lost")
    void editsDuringAScanSurvive()
    {
        ScanDebouncer<String> debouncer = debouncer();
        debouncer.markDirty("soul", 0L);
        debouncer.claimDue(10_000L);

        debouncer.markDirty("soul", 10_500L);
        debouncer.release("soul");

        assertTrue(debouncer.isPending("soul"), "the block placed mid-scan still needs a rescan");
        assertEquals(List.of("soul"), debouncer.claimDue(20_000L));
    }

    @Test
    @DisplayName("soulhomes are tracked independently")
    void keysAreIndependent()
    {
        ScanDebouncer<String> debouncer = debouncer();

        debouncer.markDirty("alice", 0L);
        debouncer.markDirty("bob", 4_000L);

        assertEquals(List.of("alice"), debouncer.claimDue(5_000L));
        assertEquals(List.of("bob"), debouncer.claimDue(9_000L));
    }

    @Test
    @DisplayName("an unloaded soulhome is forgotten entirely")
    void forgetDropsEverything()
    {
        ScanDebouncer<String> debouncer = debouncer();

        debouncer.markDirty("soul", 0L);
        debouncer.claimDue(10_000L);
        debouncer.markDirty("soul", 10_000L);

        debouncer.forget("soul");

        assertFalse(debouncer.isPending("soul"));
        assertFalse(debouncer.isInFlight("soul"));
        assertTrue(debouncer.isIdle());
        assertEquals(List.of(), debouncer.claimDue(100_000L));
    }

    @Test
    @DisplayName("claiming a soulhome outright takes it in flight and out of the queue")
    void claimTakesTheKeyImmediately()
    {
        ScanDebouncer<String> debouncer = debouncer();

        debouncer.markDirty("soul", 0L);

        assertTrue(debouncer.claim("soul"), "nothing was in flight, so this scan may start");
        assertTrue(debouncer.isInFlight("soul"));
        assertFalse(debouncer.isPending("soul"), "claiming takes it out of the pending queue");

        // the tick loop must not then start a second scan of the same soulhome
        assertEquals(List.of(), debouncer.claimDue(1_000_000L));
    }

    @Test
    @DisplayName("a soulhome already being scanned cannot be claimed a second time")
    void claimRefusesWhileInFlight()
    {
        ScanDebouncer<String> debouncer = debouncer();

        debouncer.markDirty("soul", 0L);
        debouncer.claimDue(10_000L);

        assertFalse(debouncer.claim("soul"), "a scan is already running");

        debouncer.release("soul");

        assertTrue(debouncer.claim("soul"), "and may be claimed once that one finishes");
    }

    @Test
    @DisplayName("nothing to do means nothing is returned")
    void idleByDefault()
    {
        ScanDebouncer<String> debouncer = debouncer();

        assertTrue(debouncer.isIdle());
        assertEquals(List.of(), debouncer.claimDue(1_000_000L));
    }
}
