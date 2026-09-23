/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.registry.SoundsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 *   <li><b>Your rank is the count.</b> Every few seconds the field throbs once per ring - but only
 *       as loudly as you can read it, so to a player who cannot, it is a hum with nothing in it to
 *       count, and to one who can it is a number.</li>
 * </ul>
 *
 * Both are the mod's own sounds (see {@code tools/ambience/suppression.py}). They were the ascension
 * ritual's beacon hum and the lens's amethyst chime, which made a suppressed player walking past
 * sound like a ritual and a scan at once; a count also needs a beat that is over before the next
 * one, and a chime rings for most of a second.
 *
 * <p>Positional and line-of-sight only, like everything else about suppression: a drone that carried
 * through walls would be a tracker. Neither rolls off with distance in the sound engine - both are
 * {@link SoundInstance.Attenuation#NONE} - because the fall-off is already here, as {@code nearness},
 * and runs out exactly at the edge of perception. The engine's own linear fall-off would silence
 * them at sixteen blocks, inside a range that reaches fifty-two for a rank IX.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SuppressionSounds
{
    private static final int BEAT_PERIOD_TICKS = 100;
    private static final int BEAT_SPACING_TICKS = 5;

    /** Below this, the count is not worth playing - the field is still a smear to this observer. */
    private static final float MIN_COUNT_LEGIBILITY = 0.2f;

    private static final Map<Integer, Drone> DRONES = new HashMap<>();

    private static int clock;

    private SuppressionSounds()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

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

            throb(minecraft, seen.player(), field, nearness);
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
     * One throb per ring, spaced so they can be counted, repeated every {@link #BEAT_PERIOD_TICKS}.
     * Each player's cycle is offset by their entity id, so two suppressed players side by side do
     * not throb over each other and merge into one larger number.
     */
    private static void throb(Minecraft minecraft, Player player, ClientSuppression.Perceived field, float nearness)
    {
        if (field.legibility() < MIN_COUNT_LEGIBILITY)
        {
            return;
        }

        final int phase = Math.floorMod(clock + field.entityId() * 37, BEAT_PERIOD_TICKS);

        if (phase % BEAT_SPACING_TICKS != 0 || phase / BEAT_SPACING_TICKS >= field.rings())
        {
            return;
        }

        minecraft.getSoundManager().play(new SimpleSoundInstance(
                SoundsRegistry.SUPPRESSION_THROB.get().getLocation(),
                SoundSource.PLAYERS,
                0.5f * field.legibility() * nearness,
                1.0f,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                player.getX(), player.getY() + 1d, player.getZ(),
                false));
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
            super(SoundsRegistry.SUPPRESSION_DRONE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.attenuation = Attenuation.NONE;
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
