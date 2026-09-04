/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.feedback;

import leaf.soulhome.structures.core.BuffBreakdown;
import leaf.soulhome.structures.core.SoulBuffSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #50: the Soul Lens buffs screen, shown outside a soul, reads straight off
 * {@link LensBuffReport} - the network shape of {@link BuffBreakdown}, which is itself the same
 * totals {@code SoulReport#buffs} prints to chat and the buff registry actually grants.
 */
class LensBuffReportTest
{
    @Test
    @DisplayName("one buff type with two sources carries both, and the capped flag when the ceiling bit")
    void carriesSourcesAndTheCappedFlag()
    {
        BuffBreakdown breakdown = new BuffBreakdown(
                SoulBuffSet.of(Map.of("soulhome:xp_gain", 0.5d)),
                List.of(
                        new BuffBreakdown.Source("soulhome:xp_gain", "soulhome:library", "archetype.soulhome.library", 2, 3, 0.3d, 0d),
                        new BuffBreakdown.Source("soulhome:xp_gain", "soulhome:enchanting_room", "archetype.soulhome.enchanting_room", 1, 1, 0.3d, 0d)));

        List<LensBuffReport> reports = LensBuffReport.of(breakdown);

        assertEquals(1, reports.size());

        LensBuffReport report = reports.get(0);
        assertEquals("soulhome:xp_gain", report.buffType());
        assertEquals(0.5d, report.magnitude());
        assertTrue(report.capped(), "0.3 + 0.3 claimed against a 0.5 total means the ceiling held it back");
        assertEquals(2, report.sources().size());
    }

    @Test
    @DisplayName("no rooms yet is an empty list, not a placeholder entry")
    void emptyBreakdownIsAnEmptyList()
    {
        assertTrue(LensBuffReport.of(BuffBreakdown.EMPTY).isEmpty());
    }

    @Test
    @DisplayName("a buff nowhere near its cap is not flagged as capped")
    void uncappedBuffIsNotFlagged()
    {
        BuffBreakdown breakdown = new BuffBreakdown(
                SoulBuffSet.of(Map.of("soulhome:saturation", 0.1d)),
                List.of(new BuffBreakdown.Source("soulhome:saturation", "soulhome:farm", "archetype.soulhome.farm", 1, 1, 0.1d, 0d)));

        LensBuffReport report = LensBuffReport.of(breakdown).get(0);

        assertFalse(report.capped());
    }
}
