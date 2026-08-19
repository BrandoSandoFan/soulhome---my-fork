/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Track: you move faster, everywhere, all the time.
 *
 * <p>Applied as a transient {@code MULTIPLY_TOTAL} modifier on the movement speed attribute - the
 * same operation vanilla's own Speed effect uses - rather than as a potion effect. That keeps it
 * off the effects HUD, uncurable by milk, and free to stack cleanly with an actual Speed potion
 * rather than fighting it for the same slot. Transient rather than permanent so it is never
 * written to the player's save file: it is recomputed from the capability, not persisted state.
 *
 * <h2>Why a tick check instead of reacting to a buff-change event</h2>
 *
 * Nothing in this codebase fires an event when a player's buff set changes - {@link SoulBuffs}
 * pushes a network sync on change, not a bus event other code can subscribe to - so the cheapest
 * correct option available to an effect on its own is to reconcile the modifier against what it
 * should be a few times a second, the same as checking any other slowly-changing state.
 */
public class SpeedEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SPEED;

    /** Derived from the buff id, so it needs no separate bookkeeping to stay stable. */
    private static final UUID MODIFIER_ID = UUID.nameUUIDFromBytes(TYPE.getBytes());

    private static final String MODIFIER_NAME = "soulhome:speed";

    /** How often to reconcile the modifier. Movement speed does not need per-tick precision. */
    private static final int CHECK_INTERVAL_TICKS = 10;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra movement speed as a fraction of the player's own";
    }

    /**
     * Always true for a real, server-side player. The tick handler decides for itself whether to
     * add, update or remove the modifier - including removing one left over from a buff that has
     * since dropped to zero, which a magnitude-gated {@code appliesTo} would never get the chance
     * to do.
     */
    @Override
    public boolean appliesTo(Player player)
    {
        return player != null && !player.level().isClientSide;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;

        if (!appliesTo(player) || player.tickCount % CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }

        final AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute == null)
        {
            return;
        }

        final AttributeModifier existing = attribute.getModifier(MODIFIER_ID);
        final double magnitude = magnitudeFor(player);

        if (magnitude <= 0d)
        {
            if (existing != null)
            {
                attribute.removeModifier(MODIFIER_ID);
            }

            return;
        }

        if (existing != null && existing.getAmount() == magnitude)
        {
            // already correct - nothing to reapply
            return;
        }

        if (existing != null)
        {
            attribute.removeModifier(MODIFIER_ID);
        }

        attribute.addTransientModifier(new AttributeModifier(
                MODIFIER_ID, MODIFIER_NAME, magnitude, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
