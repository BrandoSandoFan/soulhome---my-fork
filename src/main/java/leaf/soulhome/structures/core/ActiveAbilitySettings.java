/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * The knobs every active ability (#87) is bounded by, whatever it does.
 *
 * <p>Per-ability numbers - how far Soul Step blinks, how wide Rupture's cone opens - belong to the
 * effect that owns them, because only that effect knows what its magnitude means. What lives here
 * is what a server owner needs to be able to say about <i>all</i> of them at once: whether they run
 * at all, how much longer their cooldowns should be, and the floor those cooldowns may never fall
 * through.
 *
 * <p><b>{@link #minCooldownTicks} is the load-bearing one.</b> #85 makes rank amplify magnitude,
 * and magnitude shortens a recharge; without a floor, a high enough rank turns every ability into
 * a key that can be held down. The floor is applied last, after the magnitude scaling and after
 * {@link #cooldownMultiplier}, so no combination of the two can get underneath it.
 */
public record ActiveAbilitySettings(boolean enabled, double cooldownMultiplier, int minCooldownTicks, int maxCharges)
{
    public static final double DEFAULT_COOLDOWN_MULTIPLIER = 1.0d;

    /** Two seconds. Short enough never to be felt on a normal cooldown, long enough to stop a held key. */
    public static final int DEFAULT_MIN_COOLDOWN_TICKS = 40;

    /** No shipped ability reaches this at rank V; it exists so a datapack's cannot run away either. */
    public static final int DEFAULT_MAX_CHARGES = 8;

    public static final ActiveAbilitySettings DEFAULTS =
            new ActiveAbilitySettings(true, DEFAULT_COOLDOWN_MULTIPLIER, DEFAULT_MIN_COOLDOWN_TICKS, DEFAULT_MAX_CHARGES);

    public ActiveAbilitySettings
    {
        if (cooldownMultiplier <= 0d)
        {
            throw new IllegalArgumentException("cooldownMultiplier must be positive, got " + cooldownMultiplier);
        }

        if (minCooldownTicks < 1)
        {
            throw new IllegalArgumentException("minCooldownTicks must be at least 1, got " + minCooldownTicks);
        }

        if (maxCharges < 1)
        {
            throw new IllegalArgumentException("maxCharges must be at least 1, got " + maxCharges);
        }
    }

    /**
     * An ability's own recharge time, put through the server's multiplier and then through the
     * floor. Rounded up rather than down, so a pack that asks for longer cooldowns never gets a
     * shorter one back out of the rounding.
     */
    public int effectiveCooldown(int abilityCooldownTicks)
    {
        final long scaled = (long) Math.ceil(Math.max(0, abilityCooldownTicks) * this.cooldownMultiplier);
        return (int) Math.max(this.minCooldownTicks, Math.min(Integer.MAX_VALUE, scaled));
    }

    /** An ability's own charge count, bounded by the server's ceiling. Never below one. */
    public int effectiveCharges(int abilityCharges)
    {
        return Math.max(1, Math.min(this.maxCharges, abilityCharges));
    }
}
