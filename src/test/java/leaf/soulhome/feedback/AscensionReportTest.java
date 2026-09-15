/*
 * File created ~ 14 - 9 - 2026
 */

package leaf.soulhome.feedback;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape the Soul Anchor's screen reads the climb off (#83). Built on the server and sent as-is,
 * so what the screen says and what the ritual would actually do cannot drift apart - which only
 * holds if it survives the round trip through its own codec.
 */
class AscensionReportTest
{
    private static AscensionReport ready()
    {
        return new AscensionReport(
                true, true, 2, 3, false, false, true, true, 0, 40, 40, 4, 4,
                new AscensionReport.Residue(true, 12.5, 2, 0));
    }

    @Test
    @DisplayName("a report survives its own codec unchanged")
    void roundTrips()
    {
        final AscensionReport before = ready();

        final AscensionReport after = AscensionReport.CODEC
                .parse(JsonOps.INSTANCE, AscensionReport.CODEC.encodeStart(JsonOps.INSTANCE, before).result().orElseThrow())
                .result()
                .orElseThrow();

        assertEquals(before, after);
    }

    @Test
    @DisplayName("an empty report survives the round trip too, since every field defaults")
    void emptyRoundTrips()
    {
        final AscensionReport after = AscensionReport.CODEC
                .parse(JsonOps.INSTANCE,
                        AscensionReport.CODEC.encodeStart(JsonOps.INSTANCE, AscensionReport.EMPTY).result().orElseThrow())
                .result()
                .orElseThrow();

        assertEquals(AscensionReport.EMPTY, after);
        assertFalse(after.ready());
    }

    @Test
    @DisplayName("ready means every requirement met, and nothing else standing in the way")
    void readyIsEveryRequirement()
    {
        assertTrue(ready().ready());

        assertFalse(withPillar(ready(), false, true).ready(), "a pillar short of the firmament is not ready");
        assertFalse(withPillar(ready(), false, false).ready(), "no base at all is not ready");

        final AscensionReport thin = new AscensionReport(
                true, true, 2, 3, false, false, true, true, 0, 39, 40, 4, 4, AscensionReport.Residue.NONE);
        assertFalse(thin.ready(), "one point of willpower short is short");
        assertFalse(thin.willpowerMet());

        final AscensionReport broke = new AscensionReport(
                true, true, 2, 3, false, false, true, true, 0, 40, 40, 3, 4, AscensionReport.Residue.NONE);
        assertFalse(broke.ready(), "one essence short is short");
        assertFalse(broke.essenceMet());
    }

    @Test
    @DisplayName("a maxed soul, a ritual somebody else is holding, and ascension switched off are each not ready")
    void notReadyWithoutAClimb()
    {
        final AscensionReport base = ready();

        assertFalse(new AscensionReport(
                true, true, 5, 6, true, false, true, true, 0, 40, 40, 4, 4, base.residue()).ready());

        assertFalse(new AscensionReport(
                true, true, 2, 3, false, true, true, true, 0, 40, 40, 4, 4, base.residue()).ready());

        assertFalse(new AscensionReport(
                false, true, 2, 3, false, false, true, true, 0, 40, 40, 4, 4, base.residue()).ready());
    }

    @Test
    @DisplayName("residue is worth saying while it accrues, while any is banked, and when some was just taken")
    void residueWorthSaying()
    {
        assertFalse(AscensionReport.Residue.NONE.worthSaying(), "a dead tap with nothing banked has nothing to say");
        assertTrue(new AscensionReport.Residue(true, 0d, 0, 0).worthSaying(), "a fresh soul is told residue exists");
        assertTrue(new AscensionReport.Residue(false, 4.5, 0, 0).worthSaying(), "residue banked before the tap was switched off is still there");
        assertTrue(new AscensionReport.Residue(false, 0d, 0, 3).worthSaying(), "a conversion that just emptied it still reports it");
    }

    @Test
    @DisplayName("what a conversion yielded rides on the report answering it, and on no other")
    void collectedIsCarriedByTheAnswer()
    {
        final AscensionReport before = ready();

        assertEquals(0, before.residue().collected());
        assertEquals(2, before.withCollected(2).residue().collected());

        // everything else about the climb is untouched by a conversion having happened
        assertEquals(before.withCollected(0), before);
    }

    private static AscensionReport withPillar(AscensionReport report, boolean valid, boolean hasBase)
    {
        return new AscensionReport(
                report.enabled(), report.owner(), report.rank(), report.targetRank(), report.maxed(),
                report.ritualElsewhere(), valid, hasBase, hasBase ? 3 : 0, report.willpowerHave(),
                report.willpowerRequired(), report.essenceHave(), report.essenceRequired(), report.residue());
    }
}
