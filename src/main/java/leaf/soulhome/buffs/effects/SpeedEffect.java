/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Track: you move faster, everywhere, all the time - up to a point (#86).
 *
 * <p>Applied as a transient {@code MULTIPLY_TOTAL} modifier on the movement speed attribute - the
 * same operation vanilla's own Speed effect uses - rather than as a potion effect. That keeps it
 * off the effects HUD, uncurable by milk, and free to stack cleanly with an actual Speed potion
 * rather than fighting it for the same slot. The reconciliation, and the reason it happens on a
 * tick rather than on a buff-change event, live in {@link AttributeBuffEffect}.
 *
 * <h2>Past +40%</h2>
 *
 * Overshooting doorways, outrunning chunk loading and landing in your own holes is what raw speed
 * turns into past roughly forty percent extra - unwieldy, not powerful (#86). {@link #softCeiling}
 * stops the attribute there; the overflow buys two things a bigger number could not without making
 * the player harder to steer:
 *
 * <ul>
 *   <li>{@link #onSprintJump} adds to the forward push vanilla's own sprint-jump already gives -
 *       distance, not velocity, and only while airborne off a jump that was already sprinting</li>
 *   <li>{@link #onSprintStartupTick} shortens the run-up to full sprint speed with a second,
 *       separate modifier that comes off the instant cruising speed is reached, so top speed itself
 *       never moves</li>
 * </ul>
 */
public class SpeedEffect extends AttributeBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SPEED;

    /** Past this, more raw speed stops being controllable - see the class doc (#86). */
    private static final double SOFT_CEILING = 0.4d;

    /** Scales the overflow into extra forward push on a sprint-jump. */
    private static final double SPRINT_JUMP_BOOST_SCALE = 0.5d;

    /** Scales the overflow into how much faster the run-up to full sprint speed goes. */
    private static final double STARTUP_BOOST_SCALE = 2.0d;

    /** Below this fraction of cruising speed, a sprint reads as still starting up. */
    private static final double STARTUP_SPEED_THRESHOLD = 0.9d;

    /** A second modifier id, so the run-up boost never collides with the steady one above. */
    private UUID startupModifierId;

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

    @Override
    public double softCeiling()
    {
        return SOFT_CEILING;
    }

    @Override
    public String describeOverflow(double overflow)
    {
        return "extra sprint-jump distance and a shorter run-up to full sprint";
    }

    @Override
    public List<Attribute> attributes()
    {
        return List.of(Attributes.MOVEMENT_SPEED);
    }

    @Override
    protected AttributeModifier.Operation operation()
    {
        return AttributeModifier.Operation.MULTIPLY_TOTAL;
    }

    /**
     * Vanilla already pushes a sprinting jump forward by a fixed amount in
     * {@code Player#jumpFromGround}; this adds to that push rather than replacing it, scaled by
     * whatever speed the soft ceiling would otherwise have wasted (#86).
     */
    @SubscribeEvent
    public void onSprintJump(LivingEvent.LivingJumpEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide)
        {
            return;
        }

        if (!player.isSprinting())
        {
            return;
        }

        final double overflow = overflowFor(player);

        if (overflow <= 0d)
        {
            return;
        }

        final double push = overflow * SPRINT_JUMP_BOOST_SCALE;
        final double yaw = Math.toRadians(player.getYRot());
        final Vec3 motion = player.getDeltaMovement();

        player.setDeltaMovement(motion.x - Math.sin(yaw) * push, motion.y, motion.z + Math.cos(yaw) * push);
        player.hasImpulse = true;
    }

    /**
     * While a sprint has not yet reached cruising speed, a second transient modifier shortens how
     * long that takes (#86). It comes off the moment cruising speed is reached or sprinting stops,
     * so nothing about top speed changes - only how quickly a standing start gets there.
     */
    @SubscribeEvent
    public void onSprintStartupTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;
        final AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute == null)
        {
            return;
        }

        // cheapest check first - most players, most ticks, are not even sprinting
        if (!player.isSprinting())
        {
            removeStartupBoost(attribute);
            return;
        }

        final double overflow = overflowFor(player);

        if (overflow <= 0d)
        {
            removeStartupBoost(attribute);
            return;
        }

        final Vec3 motion = player.getDeltaMovement();
        final double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        if (horizontalSpeed >= attribute.getValue() * STARTUP_SPEED_THRESHOLD)
        {
            // already at cruising speed - nothing left to shorten
            removeStartupBoost(attribute);
            return;
        }

        applyStartupBoost(attribute, overflow);
    }

    private UUID startupModifierId()
    {
        if (this.startupModifierId == null)
        {
            this.startupModifierId =
                    UUID.nameUUIDFromBytes((type() + ":startup").getBytes(StandardCharsets.UTF_8));
        }

        return this.startupModifierId;
    }

    private void applyStartupBoost(AttributeInstance attribute, double overflow)
    {
        final double amount = overflow * STARTUP_BOOST_SCALE;
        final AttributeModifier existing = attribute.getModifier(startupModifierId());

        if (existing != null && existing.getAmount() == amount)
        {
            return;
        }

        if (existing != null)
        {
            attribute.removeModifier(startupModifierId());
        }

        attribute.addTransientModifier(new AttributeModifier(
                startupModifierId(), type() + ":startup", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private void removeStartupBoost(AttributeInstance attribute)
    {
        if (attribute.getModifier(startupModifierId()) != null)
        {
            attribute.removeModifier(startupModifierId());
        }
    }
}
