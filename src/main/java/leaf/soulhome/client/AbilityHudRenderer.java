/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.client;

import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.ClientSoulAbilities;
import leaf.soulhome.constants.Constants;
import leaf.soulhome.network.SyncSoulAbilitiesMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The selected ability, its charges, and how far along its recharge is (#87).
 *
 * <p><b>A player with no ability rooms sees nothing at all</b>, which is #87's own acceptance
 * criterion and the reason this draws off {@link ClientSoulAbilities} being non-empty rather than
 * off a toggle. The mod adds no furniture to the screen until the player has built something that
 * earns it.
 *
 * <p>Drawn above the hotbar and offset left of centre so it clears the vanilla health and hunger
 * rows. Charges are pips rather than a number because a glance has to answer "can I press it" -
 * counting to three is slower than seeing three.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbilityHudRenderer
{
    private static final int PIP_SIZE = 5;
    private static final int PIP_GAP = 2;

    /** Clear of the hotbar and of the health row above it. */
    private static final int BOTTOM_MARGIN = 62;

    private static final int READY_COLOUR = 0xFFB98CE8;
    private static final int SPENT_COLOUR = 0x66FFFFFF;
    private static final int TRACK_COLOUR = 0x66000000;
    private static final int PROGRESS_COLOUR = 0xFF7A54A0;
    private static final int TEXT_COLOUR = 0xFFE6D8F5;

    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 2;

    private AbilityHudRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;

        if (player == null || minecraft.options.hideGui || ClientSoulAbilities.isEmpty())
        {
            return;
        }

        final String selected = ClientSoulAbilities.selected();
        final SyncSoulAbilitiesMessage.State state = ClientSoulAbilities.stateOf(selected);

        if (state == null)
        {
            return;
        }

        final GuiGraphics graphics = event.getGuiGraphics();
        final int screenWidth = graphics.guiWidth();
        final int screenHeight = graphics.guiHeight();

        final int left = screenWidth / 2 - 91;
        final int bottom = screenHeight - BOTTOM_MARGIN;

        final Component name = Component.translatable(nameKeyOf(selected));

        graphics.drawString(minecraft.font, name, left, bottom, TEXT_COLOUR, true);

        // one pip per possible charge, filled for the ones actually banked - the empty pips are
        // what tell a player their ceiling went up when a room's tier did
        final int pipTop = bottom + 11;

        for (int i = 0; i < state.maxCharges(); i++)
        {
            final int x = left + i * (PIP_SIZE + PIP_GAP);
            graphics.fill(x, pipTop, x + PIP_SIZE, pipTop + PIP_SIZE,
                    i < state.charges() ? READY_COLOUR : SPENT_COLOUR);
        }

        // the recharge bar is drawn only while something is actually pending, so a player at full
        // charges is not shown an empty progress bar to wonder about
        if (state.charges() < state.maxCharges() && state.ticksToNext() > 0 && state.cooldownTicks() > 0)
        {
            final int barTop = pipTop + PIP_SIZE + 3;
            final double progress =
                    Math.max(0d, Math.min(1d, 1d - ((double) state.ticksToNext() / state.cooldownTicks())));

            graphics.fill(left, barTop, left + BAR_WIDTH, barTop + BAR_HEIGHT, TRACK_COLOUR);
            graphics.fill(left, barTop, left + (int) Math.round(BAR_WIDTH * progress), barTop + BAR_HEIGHT,
                    PROGRESS_COLOUR);
        }
    }

    /**
     * The lang key naming one ability. Derived from the id rather than held in a map, so a
     * datapack-registered ability gets a key of the same shape without a Java change - and an
     * untranslated one renders as its own key, which is a legible bug rather than a blank corner.
     */
    private static String nameKeyOf(String abilityType)
    {
        final int colon = abilityType.indexOf(':');

        // an id with no namespace is not something this mod ever produces, but a datapack can write
        // one; treating it as its own key is the least surprising thing to render
        return colon < 0
                ? abilityType
                : "ability." + abilityType.substring(0, colon) + "." + abilityType.substring(colon + 1);
    }
}
