/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.network;

import leaf.soulhome.feedback.LensRegionReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * #50: {@link SyncSoulLensReportMessage.ClientLensReport} is what the client-only screen opener
 * polls on every tick to decide whether to open a fresh screen. It has to hand back a report
 * exactly once per arrival - handing it back on every tick would re-open the screen over and over
 * while a player stands still holding the lens; never handing it back would mean the screen never
 * opens at all.
 */
class SyncSoulLensReportMessageTest
{
    @Test
    void freshReportIsHandedBackExactlyOnce()
    {
        List<LensRegionReport> reports = List.of(plainReport());

        SyncSoulLensReportMessage.ClientLensReport.accept(reports, 0);

        assertSame(reports, SyncSoulLensReportMessage.ClientLensReport.consumeIfNew());
        assertNull(SyncSoulLensReportMessage.ClientLensReport.consumeIfNew(),
                "the same report must not be handed back a second time");
    }

    @Test
    void aSecondArrivalIsHandedBackAgain()
    {
        SyncSoulLensReportMessage.ClientLensReport.accept(List.of(plainReport()), 0);
        SyncSoulLensReportMessage.ClientLensReport.consumeIfNew();

        List<LensRegionReport> second = List.of(plainReport(), plainReport());
        SyncSoulLensReportMessage.ClientLensReport.accept(second, 1);

        assertSame(second, SyncSoulLensReportMessage.ClientLensReport.consumeIfNew());
        assertEquals(1, SyncSoulLensReportMessage.ClientLensReport.standingIn());
    }

    private static LensRegionReport plainReport()
    {
        return new LensRegionReport(
                0, "UNCLASSIFIED", "", "", 0, 0d, LensRegionReport.NO_NEXT_TIER, "", 0d, false,
                List.of(), List.of(), List.of(), List.of());
    }
}
