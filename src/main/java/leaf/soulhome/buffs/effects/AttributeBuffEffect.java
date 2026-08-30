/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * A buff that is nothing more than a number on an attribute.
 *
 * <p>Movement speed, mana, spell power and reach are all the same shape: hold a modifier on an
 * attribute equal to the player's current magnitude, and take it off again when the magnitude
 * drops to zero. The reconciliation is fiddly enough - remove before re-adding, do not thrash the
 * attribute every tick, cope with the attribute not being on this entity at all - that four
 * copies of it would be four chances to get it subtly wrong.
 *
 * <h2>Why a tick check rather than reacting to a buff change</h2>
 *
 * Nothing in this codebase fires an event when a player's buff set changes - {@code SoulBuffs}
 * pushes a network sync on change, not a bus event other code can subscribe to - so the cheapest
 * correct option available to an effect on its own is to reconcile against what it should be a
 * few times a second, the same as checking any other slowly-changing state.
 *
 * <h2>An attribute that is not there</h2>
 *
 * {@link #attributes()} returns what could be resolved, which for a buff aimed at another mod's
 * attribute may be nothing at all - see {@code ModAttributes}. That is not an error: the effect
 * simply has nothing to write to, and the room that grants it still classifies, still reports and
 * still shows up in {@code /soulhome buffs} for the day that mod is installed.
 */
public abstract class AttributeBuffEffect implements SoulBuffEffect
{
    /** How often to reconcile. None of these attributes needs per-tick precision. */
    private static final int CHECK_INTERVAL_TICKS = 10;

    /**
     * Derived from the buff id, so it needs no separate bookkeeping to stay stable across
     * restarts. Shared across this effect's attributes, which is safe: a modifier is identified by
     * uuid within one attribute, not globally.
     *
     * <p>Worked out on first use rather than in the constructor, because deriving it there would
     * mean calling {@link #type()} on a half-built subclass.
     */
    private UUID modifierId;

    /**
     * The attributes this buff writes to, resolved however the implementation likes - empty if
     * none of them exist in this install, which is how a buff aimed at a mod nobody has installed
     * reports itself as inert rather than pretending to work.
     */
    public abstract List<Attribute> attributes();

    /**
     * How the magnitude is applied. {@code ADDITION} for a flat amount in the attribute's own
     * units (mana, blocks of reach); {@code MULTIPLY_BASE} or {@code MULTIPLY_TOTAL} for a
     * magnitude that reads as a fraction.
     */
    protected abstract AttributeModifier.Operation operation();

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

        final List<Attribute> attributes = attributes();

        if (attributes.isEmpty())
        {
            return;
        }

        final double magnitude = magnitudeFor(player);

        for (Attribute attribute : attributes)
        {
            reconcile(player, attribute, magnitude);
        }
    }

    /** Only ever called from the server tick, so a plain lazy field needs no synchronisation. */
    private UUID modifierId()
    {
        if (this.modifierId == null)
        {
            this.modifierId = UUID.nameUUIDFromBytes(type().getBytes(StandardCharsets.UTF_8));
        }

        return this.modifierId;
    }

    private void reconcile(Player player, Attribute attribute, double magnitude)
    {
        final AttributeInstance instance = player.getAttribute(attribute);

        if (instance == null)
        {
            //registered, but not an attribute players carry
            return;
        }

        final AttributeModifier existing = instance.getModifier(modifierId());

        if (magnitude <= 0d)
        {
            if (existing != null)
            {
                instance.removeModifier(modifierId());
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
            instance.removeModifier(modifierId());
        }

        //transient rather than permanent, so it is never written to the player's save file: this
        //is recomputed from the capability, not persisted state
        instance.addTransientModifier(
                new AttributeModifier(modifierId(), type(), magnitude, operation()));
    }
}
