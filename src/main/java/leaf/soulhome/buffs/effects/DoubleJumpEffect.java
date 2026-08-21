/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.mixin.LivingEntityAccessor;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Training yard: one extra jump while already in the air - a real double jump, not a triple or
 * quadruple one. The magnitude here is a plain count, so nothing stops an archetype from asking
 * for more, but the shipped training yard and the shipped config default both cap it at one.
 *
 * <h2>Reading the jump key from the server</h2>
 *
 * Whether the jump key is currently held is not exposed publicly anywhere on {@link LivingEntity}
 * - it is the same field vanilla's own ground-jump logic reads, kept {@code protected} because
 * nothing outside the entity was ever meant to need it. A jump granted mid-air is exactly that
 * "outside" case Forge does not build a public hook for, so this reads the field through
 * {@link LivingEntityAccessor} rather than duplicating input handling with a bespoke network
 * packet.
 *
 * <p>A mixin accessor and not reflection, deliberately. The field is named one thing in this
 * source tree and another in an installed jar, so a reflective lookup has to be handed the
 * obfuscated name; one written against the readable name resolves in a development workspace,
 * fails in every real install, and reports it as a single warning at startup that nobody sees.
 * The mixin annotation processor does the renaming, so there is nothing to keep in step by hand.
 */
public class DoubleJumpEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.DOUBLE_JUMP;

    /** Vanilla's own jump impulse; matched here rather than invented so the hop feels native. */
    private static final double JUMP_VELOCITY = 0.5d;

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
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
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
        return ((LivingEntityAccessor) player).getJumping();
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
