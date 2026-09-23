/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.structures.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ascension ritual's particles (#163). The playtest's finding was that the ritual barely made
 * any; what is pinned is that it now builds - more as the hold goes on, more for a higher rank, a
 * payoff far larger than any tick of the hold - and that none of it is large enough to stutter a
 * client, which would make the biggest moment in the mod worse rather than better.
 */
class AscensionSpectacleTest
{
    private static final int TOTAL = 600;

    @Test
    @DisplayName("the hold builds: later ticks carry more than early ones, at every rank")
    void theHoldBuilds()
    {
        for (int rank = 1; rank <= 5; rank++)
        {
            final int early = AscensionSpectacle.hold(10, TOTAL, rank, 6, 1L).size();
            final int late = AscensionSpectacle.hold(TOTAL - 10, TOTAL, rank, 6, 1L).size();

            assertTrue(late > early * 2, "rank " + rank + ": " + early + " early, " + late + " late");
        }
    }

    @Test
    @DisplayName("a higher rank is a bigger event, in the hold and in the payoff")
    void rankScalesIt()
    {
        assertTrue(AscensionSpectacle.hold(TOTAL - 10, TOTAL, 5, 6, 1L).size()
                > AscensionSpectacle.hold(TOTAL - 10, TOTAL, 1, 6, 1L).size());
        assertTrue(AscensionSpectacle.completion(5, 1L).size() > AscensionSpectacle.completion(1, 1L).size());
    }

    @Test
    @DisplayName("no tick is large enough to stutter a client, at the busiest rank")
    void bounded()
    {
        for (int rank = 1; rank <= 5; rank++)
        {
            for (int elapsed = 0; elapsed < TOTAL; elapsed++)
            {
                final int count = AscensionSpectacle.hold(elapsed, TOTAL, rank, 40, 7L).size();
                final boolean step = isStep(elapsed);

                assertTrue(count <= (step ? AscensionSpectacle.MAX_STEP_TICK : AscensionSpectacle.MAX_PER_TICK),
                        "rank " + rank + " tick " + elapsed + " sends " + count);
            }

            final int completion = AscensionSpectacle.completion(rank, 7L).size();

            assertTrue(completion <= AscensionSpectacle.MAX_COMPLETION, "rank " + rank + " completes with " + completion);
            assertTrue(completion > 5 * AscensionSpectacle.MAX_PER_TICK, "and the payoff dwarfs any tick of the hold");
        }
    }

    @Test
    @DisplayName("each quarter throws a ring, on the same ticks the hum steps up")
    void stepsThrowRings()
    {
        for (double fraction : AscensionSpectacle.STEPS)
        {
            final int step = (int) (TOTAL * fraction);

            assertTrue(AscensionSpectacle.hold(step, TOTAL, 3, 6, 1L).size()
                    > AscensionSpectacle.hold(step + 1, TOTAL, 3, 6, 1L).size() + 30);
        }
    }

    @Test
    @DisplayName("the helix climbs the pillar, and nothing strays far from the cap sideways")
    void placement()
    {
        final int pillar = 12;
        boolean reachedBase = false;

        for (int elapsed = 0; elapsed < TOTAL; elapsed++)
        {
            for (AscensionSpectacle.Mote mote : AscensionSpectacle.hold(elapsed, TOTAL, 5, pillar, 3L))
            {
                if (mote.y() < -pillar + 1d)
                {
                    reachedBase = true;
                }

                assertTrue(mote.y() >= -pillar - 0.01d, "below the pillar's own base");
                assertTrue(Math.hypot(mote.x(), mote.z()) < 2d, "placed off to the side of the pillar");
            }
        }

        assertTrue(reachedBase, "the helix starts from the bottom of the pillar");
    }

    @Test
    @DisplayName("the same ritual tick is the same particles")
    void deterministic()
    {
        final List<AscensionSpectacle.Mote> first = AscensionSpectacle.hold(123, TOTAL, 3, 8, 42L);

        assertEquals(first, AscensionSpectacle.hold(123, TOTAL, 3, 8, 42L));
        assertEquals(AscensionSpectacle.completion(4, 9L), AscensionSpectacle.completion(4, 9L));
    }

    private static boolean isStep(int elapsed)
    {
        for (double fraction : AscensionSpectacle.STEPS)
        {
            if (elapsed == (int) (TOTAL * fraction))
            {
                return true;
            }
        }

        return false;
    }
}
