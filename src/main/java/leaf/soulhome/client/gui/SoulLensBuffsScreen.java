/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.BuffNames;
import leaf.soulhome.feedback.LensBuffReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Soul Lens shows when used outside a soul (#50, deliverable D) - the question a player
 * out in the world actually has: is any of this doing anything, and which room earned it.
 *
 * <p>Reads straight off {@link LensBuffReport}, the network shape of the same
 * {@code BuffBreakdown} the chat command and the buff registry itself are built from.
 *
 * <p>A player carrying several buffs, each with several source rooms, produces a list nothing here
 * bounds the length of - it wraps and scrolls rather than running off the screen (#67); see
 * {@link ScrollableDetailPanel}.
 */
@OnlyIn(Dist.CLIENT)
public class SoulLensBuffsScreen extends Screen
{
    private static final int LEFT = 14;
    private static final int TOP = 30;
    private static final int RIGHT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 26;
    private static final int LINE_HEIGHT = 11;

    private static final int COLOR_TITLE = 0xE0E0FF;
    private static final int COLOR_HEADER = 0xC7A6FF;
    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_MUTED = 0xA0A0A0;
    private static final int COLOR_CAPPED = 0xFFAA00;
    private static final int COLOR_BAR_BACK = 0xFF2A2A2E;
    private static final int COLOR_BAR_FILL = 0xFF55AAFF;

    private final List<LensBuffReport> buffs;
    private final ScrollableDetailPanel panel = new ScrollableDetailPanel();

    public SoulLensBuffsScreen(List<LensBuffReport> buffs)
    {
        super(Component.translatable(Constants.StringKeys.LENS_SCREEN_BUFFS_TITLE));
        this.buffs = buffs;
    }

    @Override
    protected void init()
    {
        this.addRenderableWidget(Button.builder(
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE), button -> this.onClose())
                .bounds(this.width - 90, this.height - BOTTOM_MARGIN, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);

        if (this.buffs.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable(Constants.StringKeys.BUFFS_NONE), LEFT, TOP, COLOR_MUTED);
            return;
        }

        final int right = this.width - RIGHT_MARGIN;
        final int maxWidth = Math.max(20, right - LEFT);
        final int bottom = this.height - BOTTOM_MARGIN;

        this.panel.render(graphics, this.font, buildLines(maxWidth), LEFT, TOP, right, bottom,
                0, COLOR_BAR_BACK, COLOR_BAR_FILL);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        final int bottom = this.height - BOTTOM_MARGIN;

        if (this.panel.scroll(mouseX, mouseY, delta, LEFT, TOP, bottom))
        {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private List<ScrollableDetailPanel.VisualLine> buildLines(int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();

        for (LensBuffReport buff : this.buffs)
        {
            MutableComponent line = BuffNames.name(buff.buffType())
                    .append(Component.literal(" " + BuffNames.magnitude(buff.buffType(), buff.magnitude())));

            if (buff.capped())
            {
                line = line.append(Component.literal(" *"));
            }

            out.addAll(wrap(line, 0, buff.capped() ? COLOR_CAPPED : COLOR_HEADER, maxWidth));

            if (buff.rankBonus() > 0d)
            {
                out.addAll(wrap(Component.translatable(
                                Constants.StringKeys.LENS_SCREEN_BUFFS_RANK_BONUS,
                                BuffNames.magnitude(buff.buffType(), buff.rankBonus())),
                        6, COLOR_TEXT, maxWidth));
            }

            for (LensBuffReport.Source source : buff.sources())
            {
                out.addAll(wrap(Component.translatable(
                                Constants.StringKeys.LENS_SCREEN_BUFFS_FROM,
                                Component.translatable(source.displayName()), source.rooms(), source.bestTier()),
                        6, COLOR_TEXT, maxWidth));
            }

            out.add(ScrollableDetailPanel.VisualLine.spacer(3));
        }

        return out;
    }

    private List<ScrollableDetailPanel.VisualLine> wrap(Component text, int indent, int color, int maxWidth)
    {
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, LINE_HEIGHT);
    }
}
