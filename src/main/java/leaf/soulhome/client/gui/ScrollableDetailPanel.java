/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
    private static final int BAR_HEIGHT = 6;
    private static final int THUMB_MIN_HEIGHT = 10;

    private int scrollOffset;
    private int maxScroll;
    /** A trackpad's wheel is a fraction of a step per event; the fraction is banked here until it is a whole step. */
    private double wheelRemainder;

    /*
     * The geometry of the last render. Mouse input arrives between frames, so the thumb has to be
     * hit-tested against where it was last drawn rather than where a fresh layout would put it.
     */
    private int viewLeft;
    private int viewTop;
    private int viewRight;
    private int viewBottom;
    private int contentHeight;

    private boolean dragging;
    private double dragStartY;
    private int dragStartOffset;

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

    /**
     * The width text may wrap to inside a panel spanning [left, right]. The scrollbar and its gutter
     * are reserved whether or not the bar is showing, so text does not reflow the moment a list grows
     * long enough to need scrolling.
     */
    static int textWidth(int left, int right)
    {
        return Math.max(10, right - left - SoulGuiStyle.SCROLLBAR_WIDTH - SoulGuiStyle.SCROLLBAR_GUTTER);
    }

    void resetScroll()
    {
        this.scrollOffset = 0;
        this.wheelRemainder = 0;
        this.dragging = false;
    }

    /**
     * How far the content is currently scrolled. Needed by {@link SoulAnchorScreen}, whose lines are
     * clickable rather than only readable: a click has to be turned back into the row it landed on,
     * and the row positions are in content space rather than screen space.
     */
    int scrollOffset()
    {
        return this.scrollOffset;
    }

    /**
     * Draws {@code lines} clipped to [left, top, right, bottom], offset by the current scroll position.
     * This overload draws the thumb idle, for screens not yet wired to the cursor; the full one lights it on hover.
     */
    void render(GuiGraphics graphics, Font font, List<VisualLine> lines, int left, int top, int right, int bottom,
                int barWidth, int barBackColor, int barFillColor)
    {
        render(graphics, font, lines, left, top, right, bottom, barWidth, barBackColor, barFillColor, -1, -1);
    }

    void render(GuiGraphics graphics, Font font, List<VisualLine> lines, int left, int top, int right, int bottom,
                int barWidth, int barBackColor, int barFillColor, int mouseX, int mouseY)
    {
        int contentHeight = 0;

        for (VisualLine line : lines)
        {
            contentHeight += line.height();
        }

        this.viewLeft = left;
        this.viewTop = top;
        this.viewRight = right;
        this.viewBottom = bottom;
        this.contentHeight = contentHeight;
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
                    // A bar is drawn BAR_HEIGHT tall in whatever height its line reserves. Centring it in that
                    // space keeps the gap above and below equal; top-aligned, it sat against the line above.
                    final int barTop = y + (line.height() - BAR_HEIGHT) / 2;

                    graphics.fill(left, barTop, left + barWidth, barTop + BAR_HEIGHT, barBackColor);
                    graphics.fill(left, barTop, left + (int) Math.round(barWidth * Math.min(1d, Math.max(0d, line.barFraction()))),
                            barTop + BAR_HEIGHT, barFillColor);
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
            final int thumbTop = thumbTop();
            final int thumbHeight = thumbHeight();
            final boolean hovered = this.dragging || overThumb(mouseX, mouseY);

            // Lit while held too: a fast drag can take the pointer off a 3px thumb, and it should not dim mid-grab.
            graphics.fill(right - SoulGuiStyle.SCROLLBAR_WIDTH, top, right, bottom, SoulGuiStyle.SCROLL_TRACK);
            graphics.fill(right - SoulGuiStyle.SCROLLBAR_WIDTH, thumbTop, right, thumbTop + thumbHeight,
                    hovered ? SoulGuiStyle.SCROLL_THUMB_HOVER : SoulGuiStyle.SCROLL_THUMB);
        }
    }

    /**
     * Takes a wheel event if the cursor is over the panel and the content can still move the way the wheel
     * turns. Kept as a boolean so a screen can pass the wheel on to whatever is behind at the limits.
     */
    boolean scroll(double mouseX, double mouseY, double delta, int left, int top, int bottom)
    {
        return scroll(mouseX, mouseY, delta, left, top, this.viewRight, bottom);
    }

    /** @return true if the panel took the wheel: it moved, or it had room to move and banked a fraction of a step. */
    boolean scroll(double mouseX, double mouseY, double delta, int left, int top, int right, int bottom)
    {
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom)
        {
            return false;
        }

        // Rounding each event on its own drops a trackpad's 0.1 entirely, so a slow gesture would never scroll.
        this.wheelRemainder += delta * SCROLL_STEP;

        final int steps = (int) this.wheelRemainder;

        this.wheelRemainder -= steps;

        final int before = this.scrollOffset;

        this.scrollOffset = Math.max(0, Math.min(this.maxScroll, before - steps));

        // A sub-step event moves nothing but is still taken while there is room to move, so a slow gesture
        // does not fall through to the list behind. Moving implies room, so this covers that case too.
        return delta > 0 ? before > 0 : delta < 0 && before < this.maxScroll;
    }

    /**
     * Presses the thumb to start a drag, or jumps the thumb's centre to a click on the track and drags from
     * there. A click anywhere else in the panel is not the scrollbar's, so it returns false.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button != 0 || this.maxScroll <= 0 || !overScrollbar(mouseX, mouseY))
        {
            return false;
        }

        if (!overThumb(mouseX, mouseY))
        {
            final double thumbTop = mouseY - thumbHeight() / 2.0;
            final double fraction = Math.max(0d, Math.min(1d, (thumbTop - this.viewTop) / travel()));

            this.scrollOffset = (int) Math.round(fraction * this.maxScroll);
        }

        this.dragging = true;
        this.dragStartY = mouseY;
        this.dragStartOffset = this.scrollOffset;

        return true;
    }

    /** Maps the drag's vertical distance to scroll offset, so the thumb follows the pointer rather than the wheel's step. */
    public boolean mouseDragged(double mouseX, double mouseY)
    {
        if (!this.dragging)
        {
            return false;
        }

        final double offset = this.dragStartOffset + (mouseY - this.dragStartY) * this.maxScroll / travel();

        this.scrollOffset = Math.max(0, Math.min(this.maxScroll, (int) Math.round(offset)));

        return true;
    }

    public void mouseReleased()
    {
        this.dragging = false;
    }

    /**
     * The hit column is wider than the drawn 3px bar by the gutter. A bar that is 3px wide is too small a
     * target to grab, and the gutter is empty space, so widening it costs no text.
     */
    private boolean overScrollbar(double mouseX, double mouseY)
    {
        return mouseX >= this.viewRight - SoulGuiStyle.SCROLLBAR_WIDTH - SoulGuiStyle.SCROLLBAR_GUTTER
                && mouseX <= this.viewRight
                && mouseY >= this.viewTop && mouseY <= this.viewBottom;
    }

    private boolean overThumb(double mouseX, double mouseY)
    {
        if (this.maxScroll <= 0 || !overScrollbar(mouseX, mouseY))
        {
            return false;
        }

        return mouseY >= thumbTop() && mouseY <= thumbTop() + thumbHeight();
    }

    /** How far the thumb can travel, in pixels. Never zero, so a drag does not divide by it. */
    private double travel()
    {
        return Math.max(1, (this.viewBottom - this.viewTop) - thumbHeight());
    }

    private int thumbHeight()
    {
        final int trackHeight = this.viewBottom - this.viewTop;

        return Math.max(THUMB_MIN_HEIGHT, trackHeight * trackHeight / Math.max(1, this.contentHeight));
    }

    private int thumbTop()
    {
        return this.viewTop + (int) (travel() * (this.scrollOffset / (double) Math.max(1, this.maxScroll)));
    }
}
