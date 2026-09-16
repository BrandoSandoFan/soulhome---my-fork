/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trophy room: pikes hold their ground, and so, a little, do you - and someone whose head hangs
 * on your wall barely moves you at all (#196).
 *
 * <p>Vanilla's own knockback resistance attribute is already a 0-1 fraction rather than a
 * percentage of something else, so the room's general half is added to it directly -
 * {@code ADDITION} on {@link Attributes#KNOCKBACK_RESISTANCE}, the same operation a Wolf's or a
 * Piglin Brute's own innate resistance uses.
 *
 * <p>The targeted half cannot be an attribute modifier - {@code KNOCKBACK_RESISTANCE} is a
 * property of the victim and knows nothing about who is hitting them. {@link LivingKnockBackEvent}
 * carries the victim and the strength but not the attacker, so the attacker is recorded a moment
 * earlier, off {@link LivingHurtEvent}'s own {@code DamageSource}, and read back and cleared the
 * instant the knockback event asks for it - never left to leak into a later, unrelated knockback
 * (explosions, wind charges, a {@code /damage} command with no entity behind it at all).
 */
public class KnockbackResistanceEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.KNOCKBACK_RESISTANCE;

    /** Victim id to whoever just hurt them, for the one knockback event that follows. */
    private static final Map<UUID, UUID> PENDING_ATTACKER = new ConcurrentHashMap<>();

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra knockback resistance, as a fraction added to the player's own";
    }

    @Override
    public List<Attribute> attributes()
    {
        return List.of(Attributes.KNOCKBACK_RESISTANCE);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.ADDITION;
    }

    /**
     * Remembers who is about to knock this victim back, so {@link #onKnockBack} - which fires
     * without ever seeing the attacker - can ask "against this one, specifically". Overwritten
     * rather than queued: a victim can only be mid-knockback from their most recent hit.
     */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        final LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide)
        {
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attacker)
        {
            PENDING_ATTACKER.put(victim.getUUID(), attacker.getUUID());
        }
        else
        {
            // an explosion, a wind charge, a /damage command with no entity: no grudge applies,
            // and a stale entry from an earlier hit must not be read for this one
            PENDING_ATTACKER.remove(victim.getUUID());
        }
    }

    /**
     * The targeted half: a further fraction off whatever strength the knockback would otherwise
     * land at, composing with the room's own general resistance - already folded in by vanilla's
     * attribute - the same way vanilla's own reductions compose with each other.
     */
    @SubscribeEvent
    public void onKnockBack(LivingKnockBackEvent event)
    {
        final LivingEntity entity = event.getEntity();

        if (entity.level().isClientSide)
        {
            return;
        }

        // consumed immediately, whether or not it turns out to matter: a knockback with no
        // attacker recorded for it must never accidentally read one meant for the last hit
        final UUID attackerId = PENDING_ATTACKER.remove(entity.getUUID());

        if (attackerId == null || !(entity instanceof ServerPlayer victim))
        {
            return;
        }

        final double grudge = SoulBuffs.grudgeAgainst(victim, attackerId);

        if (grudge <= 0d)
        {
            return;
        }

        event.setStrength((float) (event.getStrength() * (1d - grudge)));
    }
}
