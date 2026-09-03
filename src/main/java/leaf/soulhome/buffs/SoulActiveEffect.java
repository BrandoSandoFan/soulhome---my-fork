/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.buffs;

import net.minecraft.server.level.ServerPlayer;

/**
 * A buff that is pressed rather than carried (#87).
 *
 * <p>An active is a {@link SoulBuffEffect} in every way that matters to the rest of the mod: it has
 * a type id, an archetype declares it in the same {@code buffs} block, the classifier scores it the
 * same way, and {@link SoulBuffEffects} registers it in the same list. What it adds is a bank of
 * charges, a clock, and something that happens when a player asks for it.
 *
 * <p>The reason for a second interface rather than a flag on the first is that nothing else in the
 * mod should have to care. {@code MiningSpeedEffect} does not want a {@code charges()} method it
 * would return zero from, and the ability service does not want to filter a registry of twenty-odd
 * passives at every keypress. An {@code instanceof} at the two places that need the distinction is
 * cheaper than a wider interface everywhere else.
 *
 * <p><b>Magnitude means what the ability says it means.</b> A passive's magnitude is a percentage or
 * a count with a fixed unit; an active's is the tier-scaled number this effect reads to work out
 * its own radius, distance, charges and recharge. Two actives with a magnitude of 4.0 have nothing
 * in common but the number.
 */
public interface SoulActiveEffect extends SoulBuffEffect
{
    /**
     * How many uses this magnitude banks, before {@code ActiveAbilitySettings} bounds it. At least
     * one - an ability that grants no charges is an ability the player was told they have and can
     * never use.
     */
    int chargesFor(double magnitude);

    /**
     * Ticks to recharge one spent charge at this magnitude, before the server's multiplier and
     * floor. Higher magnitude should shorten this rather than lengthen it; the floor in
     * {@code ActiveAbilitySettings#minCooldownTicks} is what stops rank driving it to nothing.
     */
    int rechargeTicksFor(double magnitude);

    /**
     * Do the thing. Called server-side only, after the service has checked that this player owns
     * the ability, has a charge, and is not blocked by config.
     *
     * @return whether the ability actually fired. Returning {@code false} spends nothing - that is
     *         how "your mount is dead" and "there is nowhere safe to land" refuse without costing
     *         the player a charge, per #92 and #90. An implementation returning false should
     *         normally have told the player why.
     */
    boolean activate(ServerPlayer player, double magnitude);

    /**
     * Actives carry no {@code @SubscribeEvent} methods of their own - they run when a player asks,
     * not when the game raises something - and Forge throws on registering a listener object with
     * no handler methods on it. So the default subscription is deliberately not inherited.
     *
     * <p>An active that genuinely does need a passive hook - {@code AegisEffect} has to watch for
     * damage to spend its absorption - overrides this again and calls
     * {@code SoulBuffEffect.super.register()}.
     */
    @Override
    default void register()
    {
    }
}
