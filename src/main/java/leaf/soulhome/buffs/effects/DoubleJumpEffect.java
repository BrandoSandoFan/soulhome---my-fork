/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.utils.LogHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Training yard: an extra jump, or several, while already in the air.
 *
 * <h2>Reading the jump key from the server</h2>
 *
 * Whether the jump key is currently held is not exposed publicly anywhere on {@link LivingEntity}
 * - it is the same field vanilla's own ground-jump logic reads, kept {@code protected} because
 * nothing outside the entity was ever meant to need it. A jump granted mid-air is exactly that
 * "outside" case Forge does not build a public hook for, so this reads it via
 * {@link ObfuscationReflectionHelper} - the standard Forge tool for exactly this situation -
 * rather than duplicating input handling with a bespoke network packet.
 *
 * <p>If a future mapping ever renames the field, this fails soft: {@link #resolveJumpingField}
 * logs one warning at startup and every tick after simply does nothing, rather than crashing the
 * server the first time someone stands in a training yard.
 */
public class DoubleJumpEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.DOUBLE_JUMP;

    /** Vanilla's own jump impulse; matched here rather than invented so the hop feels native. */
    private static final double JUMP_VELOCITY = 0.5d;

    private static final Field JUMPING_FIELD = resolveJumpingField();

    private final Map<UUID, AirState> airborne = new ConcurrentHashMap<>();

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra jumps available while airborne";
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (JUMPING_FIELD == null || event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;

        if (!appliesTo(player))
        {
            // covers both "no buff" and "buff just ran out mid-air" - either way, stop tracking
            this.airborne.remove(player.getUUID());
            return;
        }

        final AirState state = this.airborne.computeIfAbsent(player.getUUID(), id -> new AirState());

        if (player.onGround())
        {
            state.reset();
            return;
        }

        final boolean jumpKeyDown = isJumping(player);
        final int allowed = (int) Math.round(magnitudeFor(player));

        // only the moment the key goes down, not every tick it is held, or one long press would
        // spend every jump the player has instantly
        if (jumpKeyDown && !state.jumpKeyWasDown && state.used < allowed)
        {
            final Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, JUMP_VELOCITY, motion.z);
            player.hasImpulse = true;
            state.used++;
        }

        state.jumpKeyWasDown = jumpKeyDown;
    }

    /** Without this, a player who logs out mid-air leaks one map entry forever. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        this.airborne.remove(event.getEntity().getUUID());
    }

    private static boolean isJumping(Player player)
    {
        try
        {
            return JUMPING_FIELD.getBoolean(player);
        }
        catch (IllegalAccessException e)
        {
            return false;
        }
    }

    private static Field resolveJumpingField()
    {
        try
        {
            Field field = ObfuscationReflectionHelper.findField(LivingEntity.class, "jumping");
            field.setAccessible(true);
            return field;
        }
        catch (RuntimeException e)
        {
            LogHelper.warn("Could not find LivingEntity#jumping; soulhome:double_jump will never trigger: " + e);
            return null;
        }
    }

    /** Per-player state for the current time in the air. Reset the instant they land. */
    private static final class AirState
    {
        int used;
        boolean jumpKeyWasDown;

        void reset()
        {
            this.used = 0;
            this.jumpKeyWasDown = false;
        }
    }
}
