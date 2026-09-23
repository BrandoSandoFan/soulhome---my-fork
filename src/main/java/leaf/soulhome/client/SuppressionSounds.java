/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.config.SoulHomeClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suppression's audio channel (#188): the accessible one, and the fallback when the warp is off.
 * It carries both halves of rule 4 of #181 on its own, the same way the ring aura does:
 *
 * <ul>
 *   <li><b>Their rank is the drone.</b> A low hum on the suppressed player, louder for a stronger
 *       field and pitched lower for a higher rank.</li>
 *   <li><b>Your rank is the chimes.</b> Every few seconds the field chimes once per ring - but only
 *       as loudly as you can read it, so to a player who cannot, it is a hum with nothing in it to
 *       count, and to one who can it is a number.</li>
 * </ul>
 *
 * Positional and line-of-sight only, like everything else about suppression: a drone that carried
 * through walls would be a tracker.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SuppressionSounds
{
    private static final int CHIME_PERIOD_TICKS = 100;
    private static final int CHIME_SPACING_TICKS = 5;

    /** Below this, the chimes are not worth playing - the field is still a smear to this observer. */
    private static final float MIN_CHIME_LEGIBILITY = 0.2f;

    private static final Map<Integer, Drone> DRONES = new HashMap<>();

    private static int clock;

    private SuppressionSounds()
    {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused())
        {
            return;
        }

        clock++;

        final List<ClientSuppression.InView> inView =
                SoulHomeClientConfig.suppressionAudio() ? ClientSuppression.visible() : List.of();
        final Set<Integer> heard = new HashSet<>();

        for (ClientSuppression.InView seen : inView)
        {
            final ClientSuppression.Perceived field = seen.perceived();

            if (!field.audio())
            {
                continue;
            }

            heard.add(field.entityId());

            final float nearness = (float) Math.max(0d, 1d - seen.distance() / field.range());
            final float volume = (0.15f + 0.45f * field.strength()) * nearness;

            Drone drone = DRONES.get(field.entityId());

            if (drone == null || drone.isStopped())
            {
                drone = new Drone(seen.player(), Math.max(0.5f, 1.0f - 0.05f * field.rings()));
                DRONES.put(field.entityId(), drone);
                minecraft.getSoundManager().play(drone);
            }

            drone.aim(volume);

            chime(minecraft, seen.player(), field, nearness);
        }

        DRONES.entrySet().removeIf(entry ->
        {
            if (!heard.contains(entry.getKey()))
            {
                entry.getValue().fadeOut();
            }

            return entry.getValue().isStopped();
        });
    }

    /**
     * One chime per ring, spaced so they can be counted, repeated every {@link #CHIME_PERIOD_TICKS}.
     * Each player's cycle is offset by their entity id, so two suppressed players side by side do
     * not chime over each other and merge into one larger number.
     */
    private static void chime(Minecraft minecraft, Player player, ClientSuppression.Perceived field, float nearness)
    {
        if (field.legibility() < MIN_CHIME_LEGIBILITY)
        {
            return;
        }

        final int phase = Math.floorMod(clock + field.entityId() * 37, CHIME_PERIOD_TICKS);

        if (phase % CHIME_SPACING_TICKS != 0 || phase / CHIME_SPACING_TICKS >= field.rings())
        {
            return;
        }

        minecraft.getSoundManager().play(new SimpleSoundInstance(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.5f * field.legibility() * nearness,
                0.8f,
                SoundInstance.createUnseededRandom(),
                player.getX(), player.getY() + 1d, player.getZ()));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        DRONES.values().forEach(Drone::fadeOut);
        DRONES.clear();
    }

    /** The hum, following its player and easing toward whatever volume it was last aimed at. */
    private static final class Drone extends AbstractTickableSoundInstance
    {
        private final Player player;
        private float target;
        private boolean fading;

        Drone(Player player, float pitch)
        {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = pitch;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        void aim(float volume)
        {
            this.target = volume;
            this.fading = false;
        }

        void fadeOut()
        {
            this.target = 0f;
            this.fading = true;
        }

        @Override
        public void tick()
        {
            if (this.player.isRemoved())
            {
                this.stop();
                return;
            }

            this.x = this.player.getX();
            this.y = this.player.getY() + 1d;
            this.z = this.player.getZ();

            // eased, never snapped: nothing here may move fast enough to startle
            this.volume += (this.target - this.volume) * 0.1f;

            if (this.fading && this.volume < 0.01f)
            {
                this.stop();
            }
        }
    }
}
