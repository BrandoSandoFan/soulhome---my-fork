/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.platform.InputConstants;
import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.ClientSoulAbilities;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.network.CycleSoulAbilityMessage;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.UseSoulAbilityMessage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

/**
 * The two binds active abilities need (#87), and the client tick that drives them.
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
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID)
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

    private SoulKeybinds()
    {
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration
    {
        private Registration()
        {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event)
        {
            event.register(USE_ABILITY);
            event.register(CYCLE_ABILITY);
        }
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
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null)
        {
            return;
        }

        ClientSoulAbilities.tick();

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
    }
}
