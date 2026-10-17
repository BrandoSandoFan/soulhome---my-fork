/*
 * File created ~ 17 - 10 - 2026
 */

package leaf.soulhome.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * The colours, margins and sizes every Soul screen draws with. Four screens had each grown their own
 * grey, margin and title height for the same meaning, and a player reads a shift in grey or a few
 * pixels of margin as a shift in meaning - so a value lives here once, and a screen that wants a
 * different one has to say what the difference means rather than quietly picking another number.
 */
@OnlyIn(Dist.CLIENT)
final class SoulGuiStyle
{
    private static final String ELLIPSIS = "...";

    // Text colours are 0xRRGGBB; drawString takes them as they are.
    static final int TITLE = 0xE0E0FF;
    static final int HEADER = 0xC7A6FF;
    static final int TEXT = 0xE0E0E0;
    static final int MUTED = 0xA0A0A0;

    /** Met, attuned, hit or credited. */
    static final int POSITIVE = 0x7CE38B;
    /** A missing requirement, or a discord. */
    static final int NEGATIVE = 0xFF6B6B;
    /** Waiting, or ambiguous. */
    static final int WARNING = 0xFFD24A;
    /** Capped or gone. */
    static final int CAPPED = 0xFFAA00;
    /** Selection, or information. */
    static final int ACCENT = 0x55AAFF;

    static final int PANEL_BG = 0xB0101018;
    static final int PANEL_BORDER = 0x50C7A6FF;

    static final int BAR_BACK = 0xFF2A2A2E;
    static final int BAR_FILL = 0xFF55AAFF;

    static final int SCROLL_TRACK = 0x40FFFFFF;
    static final int SCROLL_THUMB = 0xA0FFFFFF;
    static final int SCROLL_THUMB_HOVER = 0xFFFFFFFF;

    /** Ring around the selected row's button. */
    static final int SELECTION = 0xC055AAFF;
    /** Fill behind a clickable text row under the cursor. */
    static final int ROW_HOVER = 0x30FFFFFF;

    static final int MARGIN = 12;
    static final int TITLE_Y = 8;
    static final int LINE_HEIGHT = 11;
    static final int PANEL_PADDING = 4;

    static final int BUTTON_HEIGHT = 20;
    static final int FOOTER_BOTTOM = 6;
    static final int FOOTER_GAP = 4;

    static final int SCROLLBAR_WIDTH = 3;
    /** Clear space between the text and the scrollbar, so a wrapped line never touches the bar. */
    static final int SCROLLBAR_GUTTER = 3;

    static final int BUTTON_MIN_WIDTH = 80;
    static final int BUTTON_PAD = 16;

    private SoulGuiStyle()
    {
    }

    /** The y at which a footer row of buttons sits; content ends {@link #FOOTER_GAP} above it. */
    static int footerTop(int screenHeight)
    {
        return screenHeight - FOOTER_BOTTOM - BUTTON_HEIGHT;
    }

    /**
     * Measured to the label rather than fixed: a fixed width lets a longer translation spill over
     * the button beside it, and the minimum keeps short labels from looking like chips.
     */
    static int buttonWidth(Font font, Component label)
    {
        return Math.max(BUTTON_MIN_WIDTH, font.width(label) + BUTTON_PAD);
    }

    /** Fills the plate and draws its 1px border. [left, right) and [top, bottom) as {@code fill} takes them. */
    static void drawPanel(GuiGraphics g, int left, int top, int right, int bottom)
    {
        g.fill(left, top, right, bottom, PANEL_BG);
        g.renderOutline(left, top, right - left, bottom - top, PANEL_BORDER);
    }

    /**
     * Draws {@code text} with shadow, cut to {@code maxWidth} with an ellipsis if it is wider. The cut
     * keeps the text's styling, so a coloured word that survives the cut stays coloured.
     */
    static void drawTruncated(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int colour)
    {
        final FormattedText shown = font.width(text) <= maxWidth ? text : cut(font, text, maxWidth);

        g.drawString(font, Language.getInstance().getVisualOrder(shown), x, y, colour);
    }

    /** The same cut as {@link #drawTruncated}, as a Component, for a button label. */
    static Component truncate(Font font, Component text, int maxWidth)
    {
        if (font.width(text) <= maxWidth)
        {
            return text;
        }

        return Component.literal(font.substrByWidth(text, maxWidth - font.width(ELLIPSIS)).getString() + ELLIPSIS);
    }

    static String oneDecimal(double value)
    {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static FormattedText cut(Font font, FormattedText text, int maxWidth)
    {
        return FormattedText.composite(font.substrByWidth(text, maxWidth - font.width(ELLIPSIS)), FormattedText.of(ELLIPSIS));
    }
}
