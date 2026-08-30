/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * A column of text that wraps to the panel's width and scrolls instead of running off the edges
 * of the screen (#67). Shared by {@link SoulLensScreen} and {@link SoulLensBuffsScreen}: both
 * list an unbounded, data-driven number of lines - regions, signals, buffs, sources - whose
 * length nothing on the Java side controls, so both hit the same overflow the same way.
 */
@OnlyIn(Dist.CLIENT)
final class ScrollableDetailPanel
{
    private static final int SCROLL_STEP = 10;

    private int scrollOffset;
    private int maxScroll;

    record VisualLine(FormattedCharSequence text, int indent, int color, int height, double barFraction, boolean isBar)
    {
        static VisualLine of(FormattedCharSequence text, int indent, int color, int height)
        {
            return new VisualLine(text, indent, color, height, 0, false);
        }

        static VisualLine spacer(int height)
        {
            return new VisualLine(null, 0, 0, height, 0, false);
        }

        static VisualLine bar(double fraction, int height)
        {
            return new VisualLine(null, 0, 0, height, fraction, true);
        }
    }

    /** Wraps one logical line of text to {@code maxWidth} (indent included) as one or more visual lines. */
    static List<VisualLine> wrap(Font font, Component text, int indent, int color, int maxWidth, int lineHeight)
    {
        final List<VisualLine> out = new ArrayList<>();
        final int width = Math.max(10, maxWidth - indent);

        for (FormattedCharSequence line : font.split(text, width))
        {
            out.add(VisualLine.of(line, indent, color, lineHeight));
        }

        return out;
    }

    void resetScroll()
    {
        this.scrollOffset = 0;
    }

    /** Draws {@code lines} clipped to [left, top, right, bottom], offset by the current scroll position. */
    void render(GuiGraphics graphics, Font font, List<VisualLine> lines, int left, int top, int right, int bottom,
                int barWidth, int barBackColor, int barFillColor)
    {
        int contentHeight = 0;

        for (VisualLine line : lines)
        {
            contentHeight += line.height();
        }

        this.maxScroll = Math.max(0, contentHeight - (bottom - top));
        this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll);

        graphics.enableScissor(left, top, right, bottom);

        int y = top - this.scrollOffset;

        for (VisualLine line : lines)
        {
            if (y + line.height() > top && y < bottom)
            {
                if (line.isBar())
                {
                    graphics.fill(left, y, left + barWidth, y + 6, barBackColor);
                    graphics.fill(left, y, left + (int) Math.round(barWidth * Math.min(1d, Math.max(0d, line.barFraction()))),
                            y + 6, barFillColor);
                }
                else if (line.text() != null)
                {
                    graphics.drawString(font, line.text(), left + line.indent(), y, line.color());
                }
            }

            y += line.height();
        }

        graphics.disableScissor();

        if (this.maxScroll > 0)
        {
            final int trackHeight = bottom - top;
            final int thumbHeight = Math.max(10, trackHeight * trackHeight / Math.max(1, contentHeight));
            final int thumbTop = top + (int) ((trackHeight - thumbHeight) * (this.scrollOffset / (double) this.maxScroll));

            graphics.fill(right - 3, top, right, bottom, 0x40FFFFFF);
            graphics.fill(right - 3, thumbTop, right, thumbTop + thumbHeight, 0xA0FFFFFF);
        }
    }

    /** @return true if the cursor was inside the panel and the scroll was consumed. */
    boolean scroll(double mouseX, double mouseY, double delta, int left, int top, int bottom)
    {
        if (mouseX < left || mouseY < top || mouseY > bottom)
        {
            return false;
        }

        this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset - (int) Math.round(delta * SCROLL_STEP)));

        return true;
    }
}
