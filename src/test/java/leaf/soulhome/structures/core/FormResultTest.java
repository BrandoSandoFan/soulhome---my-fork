/*
 * File created ~ 21 - 8 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormResultTest
{
    @Test
    @DisplayName("confidence is clamped to [0, 1]")
    void confidenceIsClamped()
    {
        assertEquals(1.0, new FormResult(1.5, "").confidence());
        assertEquals(0.0, new FormResult(-0.5, "").confidence());
        assertEquals(0.5, new FormResult(0.5, "").confidence());
    }

    @Test
    @DisplayName("a null diagnostic becomes empty, never null")
    void nullDiagnosticBecomesEmpty()
    {
        assertEquals("", new FormResult(1.0, null).diagnostic());
    }
}
