/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.structures.core;

/**
 * One active ability's banked charges and the clock running toward the next one (#87).
 *
 * <p>Immutable, and Minecraft-free, so the awkward part of an ability - the bookkeeping, not the
 * fireworks - can be exercised without a server. Every transition returns a new value; the
 * capability holding these swaps the reference.
 *
 * <p><b>The recharge clock runs only while there is something to recharge.</b> A player sitting at
 * full charges has no clock, so the first spend after a long walk is not quietly credited with the
 * whole walk - the wait starts when the charge is spent, which is what a player expects a cooldown
 * to mean. The alternative (a clock always ticking) makes two spends in quick succession feel free
 * and every spend after a pause feel arbitrary.
 *
 * @param charges           how many uses are banked right now
 * @param ticksToNextCharge ticks until the next charge lands, or 0 when nothing is recharging
 */
public record AbilityCharges(int charges, int ticksToNextCharge)
{
    public static final AbilityCharges EMPTY = new AbilityCharges(0, 0);

    public AbilityCharges
    {
        if (charges < 0)
        {
            throw new IllegalArgumentException("charges must not be negative, got " + charges);
        }

        if (ticksToNextCharge < 0)
        {
            throw new IllegalArgumentException("ticksToNextCharge must not be negative, got " + ticksToNextCharge);
        }
    }

    /** A freshly granted ability: banked to the brim, with no clock running. */
    public static AbilityCharges full(int maxCharges)
    {
        return new AbilityCharges(Math.max(0, maxCharges), 0);
    }

    public boolean canSpend()
    {
        return this.charges > 0;
    }

    /**
     * One server tick's worth of recharging. A state already at {@code maxCharges} is returned with
     * its clock cleared rather than left running, which is what makes a ceiling lowered by a config
     * reload settle instead of ticking forever against a bound it has already passed.
     */
    public AbilityCharges tick(int maxCharges, int cooldownTicks)
    {
        if (this.charges >= maxCharges)
        {
            return this.ticksToNextCharge == 0 ? this : new AbilityCharges(Math.min(this.charges, maxCharges), 0);
        }

        final int remaining = this.ticksToNextCharge - 1;

        if (remaining > 0)
        {
            return new AbilityCharges(this.charges, remaining);
        }

        // the clock has run out (or was never started - a state restored from an older save, or one
        // whose ceiling just rose past it). Either way there is room for a charge, so credit one.
        final int gained = this.charges + 1;
        return new AbilityCharges(gained, gained >= maxCharges ? 0 : Math.max(1, cooldownTicks));
    }

    /**
     * Spends one charge. Starting the clock only when the bank was full is the whole point - see
     * this record's own note. Spending with nothing banked is a no-op rather than an error, since
     * the server validates before it calls this and a rejected press should change nothing.
     */
    public AbilityCharges spend(int maxCharges, int cooldownTicks)
    {
        if (!canSpend())
        {
            return this;
        }

        final boolean wasFull = this.charges >= maxCharges;
        final int remaining = this.charges - 1;
        final int clock = wasFull ? Math.max(1, cooldownTicks) : Math.max(this.ticksToNextCharge, 1);

        return new AbilityCharges(remaining, clock);
    }

    /**
     * How far along the current recharge is, 0 to 1, for the HUD's arc. A full bank reads 1 rather
     * than 0: nothing is pending, which the player should see as "ready", not as "just started".
     */
    public double rechargeProgress(int maxCharges, int cooldownTicks)
    {
        if (this.charges >= maxCharges || this.ticksToNextCharge <= 0 || cooldownTicks <= 0)
        {
            return 1d;
        }

        return Math.max(0d, Math.min(1d, 1d - ((double) this.ticksToNextCharge / cooldownTicks)));
    }

    /**
     * The state a player comes back to after dying: #87 is explicit that charges do not survive
     * death and cooldowns reset, so a death is neither a punishment that leaves them stranded on a
     * long recharge nor a free refill.
     */
    public static AbilityCharges afterDeath(int cooldownTicks)
    {
        return new AbilityCharges(0, Math.max(1, cooldownTicks));
    }
}
