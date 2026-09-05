/*
 * File created ~ 5 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The soft ceiling (#86): four buffs stop being worth more long before they stop growing, so each
 * gets a fixed point past which more of the number stops being a reward. Speed and swim speed have
 * somewhere to put the overflow; reach and mining speed - simplified after direct feedback from the
 * room's own designer - do not, and simply stop.
 */
class SoftCeilingTest
{
    @Test
    @DisplayName("speed stops at forty percent and converts the rest")
    void speedConvertsOverflow()
    {
        SpeedEffect effect = new SpeedEffect();

        assertEquals(0.4d, effect.softCeiling());
        assertNotNull(effect.describeOverflow(0.1d));
    }

    @Test
    @DisplayName("swim speed stops at forty percent and converts the rest")
    void swimSpeedConvertsOverflow()
    {
        SwimSpeedEffect effect = new SwimSpeedEffect();

        assertEquals(0.4d, effect.softCeiling());
        assertNotNull(effect.describeOverflow(0.1d));
    }

    @Test
    @DisplayName("reach stops at two blocks and simply drops the rest")
    void reachDropsOverflow()
    {
        ReachEffect effect = new ReachEffect();

        assertEquals(2.0d, effect.softCeiling());
        assertNull(effect.describeOverflow(0.5d));
    }

    @Test
    @DisplayName("mining speed has a ceiling too, and simply drops the rest")
    void miningSpeedDropsOverflow()
    {
        MiningSpeedEffect effect = new MiningSpeedEffect();

        assertEquals(0.75d, effect.softCeiling());
        assertNull(effect.describeOverflow(0.5d));
    }

    @Test
    @DisplayName("every other buff keeps no ceiling at all")
    void everythingElseHasNoCeiling()
    {
        assertEquals(Double.MAX_VALUE, new XpGainEffect().softCeiling());
    }
}
