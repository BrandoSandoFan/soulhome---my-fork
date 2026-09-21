/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.platform.InputConstants;
import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.ClientSoulAbilities;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.network.CycleSoulAbilityMessage;
import leaf.soulhome.network.MeditateMessage;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.UseSoulAbilityMessage;
import leaf.soulhome.structures.core.MeditationSettings;
import leaf.soulhome.utils.ChannelRing;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The two binds active abilities need (#87), the Meditate bind (#183), and the client tick that
 * drives all three.
 *
 * <p>Two rather than one per ability: a player with five ability rooms would otherwise be asked to
 * find five free keys, and the fifth would collide with something. One key fires, one key chooses.
 *
 * <p><b>V and B are defaults, not decisions.</b> Both are unbound in vanilla 1.20.1, which is the
 * only property that matters here - a player who dislikes them rebinds them, and a pack that wants
 * them elsewhere ships a keybind file.
 *
 * <p>The press is sent, never acted on. Everything about whether it did anything is decided
 * server-side; see {@code UseSoulAbilityMessage}.
 *
 * <p>The mappings are held here and registered by {@code ClientRegistry}, which is where this mod
 * does its client-side mod-bus registration. This class only owns them and reads them.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SoulKeybinds
{
    public static final KeyMapping USE_ABILITY = new KeyMapping(
            Constants.StringKeys.KEY_ABILITY_USE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            Constants.StringKeys.KEYS_CATEGORY);

    public static final KeyMapping CYCLE_ABILITY = new KeyMapping(
            Constants.StringKeys.KEY_ABILITY_CYCLE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            Constants.StringKeys.KEYS_CATEGORY);

    /** The good way in and the way back out, held rather than pressed - see {@code MeditationService}. */
    public static final KeyMapping MEDITATE = new KeyMapping(
            Constants.StringKeys.KEY_MEDITATE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            Constants.StringKeys.KEYS_CATEGORY);

    /**
     * Whether this client last told the server the bind was held, and how long it has believed
     * so - both purely cosmetic. The server is the only thing that ever decides a channel is
     * complete; this local count only drives {@link ChannelRing}, the same ring
     * {@code SoulKeyItem#onUseTick} draws, running here since a block has no use-tick of its own to
     * hook.
     */
    private static boolean meditating = false;
    private static int meditationTicks = 0;

    private SoulKeybinds()
    {
    }

    /**
     * Drains the key queue once a tick and advances the client's copy of the recharge clocks.
     *
     * <p>{@code consumeClick} rather than {@code isDown} for both: an ability is a press, not a
     * state, and a held key should fire once. The server rate-limits anyway - see
     * {@code SoulAbilities} - but a client that sends sixty packets a second to have fifty-nine
     * rejected is still a client wasting a server's time.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null)
        {
            return;
        }

        ClientSoulAbilities.tick();
        SurveyedBlocks.tick();

        boolean used = false;

        while (USE_ABILITY.consumeClick())
        {
            // only the first press in a tick is worth sending; the rest are the key repeating
            if (!used && !ClientSoulAbilities.isEmpty())
            {
                Network.sendToServer(new UseSoulAbilityMessage(ClientSoulAbilities.selected()));
                used = true;
            }
        }

        boolean cycled = false;

        while (CYCLE_ABILITY.consumeClick())
        {
            if (!cycled && ClientSoulAbilities.owned().size() > 1)
            {
                Network.sendToServer(new CycleSoulAbilityMessage(true));
                cycled = true;
            }
        }

        tickMeditate(minecraft);
    }

    /**
     * {@code isDown} rather than {@code consumeClick}: meditation is a hold, and the server is what
     * actually tracks the channel - this only tells it when the key's state changes, and draws the
     * same ring locally while it is down.
     */
    private static void tickMeditate(Minecraft minecraft)
    {
        final boolean down = MEDITATE.isDown();

        if (down != meditating)
        {
            meditating = down;
            meditationTicks = 0;
            Network.sendToServer(new MeditateMessage(down));
        }

        if (meditating && minecraft.player != null)
        {
            meditationTicks++;

            final int required = MeditationSettings.DEFAULT_CUSHION_CHANNEL_TICKS;
            final int remaining = Math.max(0, required - meditationTicks);

            ChannelRing.spawn(minecraft.player, remaining, required, ParticleTypes.SOUL_FIRE_FLAME);
        }
    }
}
