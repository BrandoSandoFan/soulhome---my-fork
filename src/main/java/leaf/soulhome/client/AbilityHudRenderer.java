/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.client;

import com.mojang.blaze3d.systems.RenderSystem;
import leaf.soulhome.SoulHome;
import leaf.soulhome.buffs.ClientSoulAbilities;
import leaf.soulhome.network.SyncSoulAbilitiesMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
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
 * <p>Drawn on the left status stack, above the health and armour rows. The stack's height is
 * ForgeGui's own running total for the frame, so the HUD clears whatever is actually there - a
 * second row of hearts, or no armour at all. Having placed itself, the HUD adds its height to that
 * total, so anything drawn after it on the same side stacks above it rather than over it. Charges
 * are pips rather than a number because a glance has to answer "can I press it" - counting to three
 * is slower than seeing three.
 *
 * <p>It fades in and out rather than popping, because it appears when a soul is entered or a room is
 * attuned, and a block of text that switches on at the edge of the hotbar reads as a glitch.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbilityHudRenderer
{
    private static final int PIP_SIZE = 5;
    private static final int PIP_GAP = 2;

    /** Half the vanilla hotbar, which is 182 px wide. The HUD is anchored on the same centre line. */
    private static final int HOTBAR_HALF_WIDTH = 91;

    /** The name is the top line of the block; the pips start this far below it. */
    private static final int PIPS_BELOW_NAME = 11;
    private static final int BAR_GAP = 3;
    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 2;

    /**
     * The whole block, recharge bar included, is reserved on the stack even when no bar is drawn. A
     * stack that changes height every time a recharge starts or finishes would make everything above
     * it jump.
     */
    private static final int BLOCK_HEIGHT = PIPS_BELOW_NAME + PIP_SIZE + BAR_GAP + BAR_HEIGHT;

    /** A pixel of air between this block and the row it sits above, so the two do not read as one. */
    private static final int STACK_GAP = 1;

    /** Ticks for the HUD to fade fully in, or fully out. */
    private static final int FADE_TICKS = 5;

    /**
     * The faintest alpha drawn. Font reads a text colour whose alpha is under 4 as opaque, so a fade
     * allowed to get that faint would flash solid text at the end rather than trail off.
     */
    private static final int MIN_ALPHA = 4;

    private static final int READY_COLOUR = 0xFFB98CE8;

    /**
     * A spent pip is its outline in the ready colour at this share of its alpha. Same hue, so ready
     * and spent differ in shape - a player who cannot tell the two colours apart still can.
     */
    private static final float SPENT_ALPHA = 0.5f;

    private static final int TRACK_COLOUR = 0x66000000;
    private static final int PROGRESS_COLOUR = 0xFF7A54A0;
    private static final int TEXT_COLOUR = 0xFFE6D8F5;

    private static int fadeTicks;
    private static int previousFadeTicks;

    /**
     * The ability last drawn. Leaving a soul empties {@link ClientSoulAbilities} at once, but the HUD
     * should still fade out over the ticks that follow, and a fade has to have something to draw.
     */
    private static String shownAbility;
    private static SyncSoulAbilitiesMessage.State shownState;

    private AbilityHudRenderer()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        // stepped once per tick, not once per frame, so the fade lasts FADE_TICKS whatever the frame
        // rate is
        previousFadeTicks = fadeTicks;

        if (wantsShown(Minecraft.getInstance()))
        {
            fadeTicks = Math.min(FADE_TICKS, fadeTicks + 1);
        }
        else
        {
            fadeTicks = Math.max(0, fadeTicks - 1);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event)
    {
        // health is drawn first on the left and armour second, and nothing else on the left grows the
        // stack, so after ARMOR_LEVEL the stack is final. Food, air and mount health are all on the
        // right, which is why they do not need counting here.
        if (event.getOverlay() != VanillaGuiOverlay.ARMOR_LEVEL.type())
        {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.options.hideGui)
        {
            return;
        }

        final String selected = ClientSoulAbilities.selected();
        final SyncSoulAbilitiesMessage.State live = ClientSoulAbilities.stateOf(selected);

        if (live != null)
        {
            shownAbility = selected;
            shownState = live;
        }

        final float opacity = Mth.lerp(event.getPartialTick(), previousFadeTicks, fadeTicks) / FADE_TICKS;

        if (shownState == null || Math.round(opacity * 255f) < MIN_ALPHA)
        {
            return;
        }

        final GuiGraphics graphics = event.getGuiGraphics();
        // Forge installs its own Gui, which the leftHeight stacking below relies on. Another mod that swaps in a
        // plain one should cost this overlay, not a ClassCastException on every frame.
        if (!(minecraft.gui instanceof ForgeGui gui))
        {
            return;
        }

        final SyncSoulAbilitiesMessage.State state = shownState;

        final int left = graphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH;
        final int top = graphics.guiHeight() - gui.leftHeight - BLOCK_HEIGHT;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.drawString(
                minecraft.font,
                Component.translatable(nameKeyOf(shownAbility)),
                left,
                top,
                withAlpha(TEXT_COLOUR, opacity),
                true);

        final int pipTop = top + PIPS_BELOW_NAME;

        // one pip per possible charge, filled for the ones actually banked - the empty pips are
        // what tell a player their ceiling went up when a room's tier did
        for (int i = 0; i < state.maxCharges(); i++)
        {
            final int x = left + i * (PIP_SIZE + PIP_GAP);

            if (i < state.charges())
            {
                graphics.fill(x, pipTop, x + PIP_SIZE, pipTop + PIP_SIZE, withAlpha(READY_COLOUR, opacity));
            }
            else
            {
                spentOutline(graphics, x, pipTop, withAlpha(READY_COLOUR, opacity * SPENT_ALPHA));
            }
        }

        // the recharge bar is drawn only while something is actually pending, so a player at full
        // charges is not shown an empty progress bar to wonder about
        if (state.charges() < state.maxCharges() && state.ticksToNext() > 0 && state.cooldownTicks() > 0)
        {
            final int barTop = pipTop + PIP_SIZE + BAR_GAP;
            final double progress =
                    Math.max(0d, Math.min(1d, 1d - ((double) state.ticksToNext() / state.cooldownTicks())));

            graphics.fill(left, barTop, left + BAR_WIDTH, barTop + BAR_HEIGHT,
                    withAlpha(TRACK_COLOUR, opacity));
            graphics.fill(left, barTop, left + (int) Math.round(BAR_WIDTH * progress), barTop + BAR_HEIGHT,
                    withAlpha(PROGRESS_COLOUR, opacity));
        }

        RenderSystem.disableBlend();

        gui.leftHeight += BLOCK_HEIGHT + STACK_GAP;
    }

    /**
     * The HUD is wanted whenever a player is in a world with an ability to show and the GUI is not
     * hidden. Read per tick, so the fade is driven by the same condition the frame is drawn under.
     */
    private static boolean wantsShown(Minecraft minecraft)
    {
        return minecraft.player != null
                && !minecraft.options.hideGui
                && !ClientSoulAbilities.isEmpty()
                && ClientSoulAbilities.stateOf(ClientSoulAbilities.selected()) != null;
    }

    /** A spent pip is drawn as its outline only, one pixel wide, so it reads as empty rather than dim. */
    private static void spentOutline(GuiGraphics graphics, int x, int y, int colour)
    {
        final int right = x + PIP_SIZE;
        final int bottom = y + PIP_SIZE;

        graphics.fill(x, y, right, y + 1, colour);
        graphics.fill(x, bottom - 1, right, bottom, colour);
        graphics.fill(x, y + 1, x + 1, bottom - 1, colour);
        graphics.fill(right - 1, y + 1, right, bottom - 1, colour);
    }

    /**
     * The colour with its alpha scaled by the opacity. A fade changes how much of the HUD shows and
     * never its colour, so a fading pip is the same red or purple as a settled one.
     */
    private static int withAlpha(int argb, float opacity)
    {
        final int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);

        return alpha << 24 | argb & 0xFFFFFF;
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
