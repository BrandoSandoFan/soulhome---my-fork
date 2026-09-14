/*
 * File created ~ 14 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * Treasury's magnitude turned into effective Fortune levels - see #195. The buff pays a chance (0
 * to 0.15 today, {@code treasury.json}), not a level, and this is where that chance becomes "+1
 * level" rather than "+1 copy of a random drop": a magnitude of 0.15 is a 15% chance per block
 * broken of Fortune I, stacking on top of whatever level the player's own tool already carries.
 *
 * <p>This is deliberately the smaller of the two conversions #195 considered. It keeps the shipped
 * magnitudes, the config ceilings and the tier maths exactly as they are, at the cost of saturating
 * at a 100% chance of +1 level with nowhere further to go - a problem for #85's rank scaling to
 * solve if a room's magnitude is ever pushed past 1.0.
 *
 * <p>Pure and stateless on purpose: the roll itself has to happen on the server thread, at the
 * moment a block break's loot is generated, in game code this package cannot see - but what a
 * magnitude and a die roll turn into is exactly the kind of thing that belongs here rather than
 * buried in an event handler no test can reach.
 */
public final class FortuneLevels
{
    private FortuneLevels()
    {
    }

    /**
     * {@code magnitude} is clamped to {@code [0, 1]} and read as the chance of a hit; {@code roll}
     * is expected to be uniform in {@code [0, 1)}, as {@code RandomSource#nextDouble()} returns. A
     * hit grants one effective Fortune level on top of the tool's own; anything else grants none -
     * there is no partial level, so a miss costs nothing and a hit is never doubled.
     */
    public static int extraLevels(double magnitude, double roll)
    {
        final double chance = Math.max(0d, Math.min(1d, magnitude));
        return roll < chance ? 1 : 0;
    }
}
