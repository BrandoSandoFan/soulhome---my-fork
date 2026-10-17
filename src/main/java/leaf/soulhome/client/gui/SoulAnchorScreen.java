/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.AscensionReport;
import leaf.soulhome.feedback.AttunementReport;
import leaf.soulhome.feedback.BuffNames;
import leaf.soulhome.network.CollectResidueMessage;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SetAttunementMessage;
import leaf.soulhome.network.SyncSoulAnchorMessage;
import leaf.soulhome.structures.core.RoomPool;
import leaf.soulhome.structures.core.SoulBounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen the Soul Anchor opens: what the soul is carrying (#154), and how far it still has to
 * climb (#83).
 *
 * <p>Both halves are here because both are answers to the same click. The climb used to be printed
 * to chat, which a player reading this screen cannot see at all, and the residue was converted by
 * the click itself - so finding out how close you were spent the thing you were saving. Now the
 * summary is read and the conversion is a button, and looking costs nothing.
 *
 * <p>Binding is a click on a row. It is free, instant, and has no confirmation - the same rooms are
 * available either way, so anything that made changing your mind cost something would only punish
 * experimenting.
 *
 * <p>Nothing here decides anything. A click is a request; the server answers with a whole fresh
 * anchor, which this screen redraws from. That is why there is no local toggling: a slot that was
 * full when the click was sent is still full when it lands, and a row that flipped optimistically
 * and then flipped back is worse than one that simply waited. The same goes for the residue button -
 * the number on it is the server's, and it is still the server's after it has been pressed.
 *
 * <p>Lists wrap and scroll (#67) - a soulhome may hold sixty-four regions, and nothing on the Java
 * side bounds how long a room's name or its grants are.
 */
@OnlyIn(Dist.CLIENT)
public class SoulAnchorScreen extends Screen
{
    /** The gap between the header and the plate, and between the sections inside it. */
    private static final int SECTION_GAP = 4;
    private static final int INDENT = 8;
    /** A room's name starts past the checkbox, and its wrapped lines and grants start at the same place. */
    private static final int ROW_INDENT = 10;
    private static final int CHECKBOX_SIZE = 7;
    private static final int BUTTON_GAP = 8;
    private static final int NOTICE_TICKS = 80;
    /** The notice fades over its last ticks rather than being cut, so it does not vanish mid-glance. */
    private static final int NOTICE_FADE_TICKS = 20;
    /**
     * Below this the notice is not drawn at all. drawString reads an alpha under 4 as opaque in some
     * versions, so a near-invisible fade would pop back to full strength for its last frames.
     */
    private static final int NOTICE_MIN_ALPHA = 0x10;

    private static final String[] NOTICE_KEYS = {
            Constants.StringKeys.ATTUNE_BOUND,
            Constants.StringKeys.ATTUNE_UNBOUND,
            Constants.StringKeys.ATTUNE_NO_SLOTS
    };

    /** Residue is the one colour this screen owns; it means nothing the shared palette says. */
    private static final int COLOR_RESIDUE = 0x69D7E0;

    private final ScrollableDetailPanel panel = new ScrollableDetailPanel();

    /** Where each clickable room sits in content space, so a click can be turned back into a room. */
    private final List<Row> rows = new ArrayList<>();

    private AscensionReport ascension;
    private AttunementReport report;
    private Button closeButton;
    private Button residueButton;
    /** The tallest any notice wraps to at this width, measured once per init rather than every frame. */
    private int noticeRows = 1;
    private long seenGeneration;

    /** The server's word on the last click, and the tick it arrived on - drawn briefly in the hint's place (#279). */
    private SyncSoulAnchorMessage.Notice notice = SyncSoulAnchorMessage.Notice.NONE;
    private int noticeAge;

    public SoulAnchorScreen(AscensionReport ascension, AttunementReport report)
    {
        super(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_TITLE));
        this.ascension = ascension;
        this.report = report;
        this.seenGeneration = SyncSoulAnchorMessage.ClientAnchor.generation();
    }

    @Override
    protected void init()
    {
        this.noticeRows = measureNoticeRows();

        this.closeButton = this.addRenderableWidget(Button.builder(
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE), button -> this.onClose())
                .bounds(0, 0, SoulGuiStyle.BUTTON_MIN_WIDTH, SoulGuiStyle.BUTTON_HEIGHT)
                .build());

        // built for the owner whatever the residue currently is, and hidden rather than rebuilt when
        // there is nothing to take: a report landing while this is open changes the number on it, and
        // a widget that had to be created at that moment would be a widget that sometimes was not
        this.residueButton = this.addRenderableWidget(Button.builder(
                        Component.empty(), button -> Network.sendToServer(new CollectResidueMessage()))
                .bounds(0, 0, SoulGuiStyle.BUTTON_MIN_WIDTH, SoulGuiStyle.BUTTON_HEIGHT)
                .build());

        updateResidueButton();
    }

    @Override
    public void tick()
    {
        // a report that lands while this is open is the server's answer to a click made in it, so it
        // updates the screen in place rather than opening a second one over the top
        this.noticeAge++;

        final long generation = SyncSoulAnchorMessage.ClientAnchor.generation();

        if (generation != this.seenGeneration)
        {
            this.seenGeneration = generation;

            final SyncSoulAnchorMessage.Anchor anchor = SyncSoulAnchorMessage.ClientAnchor.latest();
            this.ascension = anchor.ascension();
            this.report = anchor.attunement();
            this.notice = anchor.notice();
            this.noticeAge = 0;

            updateResidueButton();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, SoulGuiStyle.TITLE_Y, SoulGuiStyle.TITLE);

        final int textWidth = this.width - 2 * SoulGuiStyle.MARGIN;

        if (loadoutShown())
        {
            SoulGuiStyle.drawTruncated(graphics, this.font, Component.translatable(
                            Constants.StringKeys.ANCHOR_SCREEN_SLOTS,
                            this.report.passiveUsed(), this.report.passiveSlots(),
                            this.report.activeUsed(), this.report.activeSlots()),
                    SoulGuiStyle.MARGIN, slotsY(), textWidth, SoulGuiStyle.HEADER);
        }

        drawStatus(graphics, textWidth);

        // a window too short for the list, or too narrow to wrap it, keeps its header and footer and
        // loses the list: a plate whose bottom sits above its top draws nothing sensible
        if (!panelFits())
        {
            return;
        }

        final List<ScrollableDetailPanel.VisualLine> lines =
                buildLines(ScrollableDetailPanel.textWidth(viewLeft(), viewRight()));
        final Row hovered = rowAt(mouseX, mouseY);

        SoulGuiStyle.drawPanel(graphics, SoulGuiStyle.MARGIN, plateTop(), plateRight(), plateBottom());

        if (hovered != null)
        {
            drawHover(graphics, hovered);
        }

        this.panel.render(graphics, this.font, lines, viewLeft(), viewTop(), viewRight(), viewBottom(),
                0, SoulGuiStyle.BAR_BACK, SoulGuiStyle.BAR_FILL, mouseX, mouseY);

        drawCheckboxes(graphics);

        if (hovered != null)
        {
            graphics.renderTooltip(this.font, tooltipFor(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // the panel is asked first: its scrollbar sits inside the row area, so a click on the bar is
        // the bar's and never a room's
        if (panelFits() && this.panel.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }

        final Row row = button == 0 ? rowAt(mouseX, mouseY) : null;

        if (row != null)
        {
            // a bind is only ever sent for a room that is standing. A ghost is always bound, so its click
            // is a release - and a bind of an absent room is refused server-side with no effect
            // (NO_SUCH_ROOM, or UNCHANGED for one still bound), so asking for one would be a dead click
            Network.sendToServer(new SetAttunementMessage(row.roomId(), row.present() && !row.attuned()));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (this.panel.mouseDragged(mouseX, mouseY))
        {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        this.panel.mouseReleased();

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if (panelFits() && this.panel.scroll(mouseX, mouseY, delta,
                SoulGuiStyle.MARGIN, plateTop(), plateRight(), plateBottom()))
        {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /**
     * Whether there is a loadout to draw at all. Attunement switched off sends
     * {@link AttunementReport#EMPTY} rather than a report saying "off" (#151's "off means off"), so
     * this reads the absence rather than a flag - and a soul with no slots and no rooms has nothing
     * to say about attunement either way, which is the same silence.
     */
    private boolean loadoutShown()
    {
        return this.report.owner() || !this.report.rooms().isEmpty()
                || this.report.passiveSlots() + this.report.activeSlots() > 0;
    }

    /*
     * The header is a stack of LINE_HEIGHT rows under the title: the slots line (only with a loadout),
     * then the status row that carries the hint or a notice. Everything below is derived from that
     * stack, so the list's top moves with the header and never with a hand-tuned number.
     */

    private int slotsY()
    {
        return SoulGuiStyle.TITLE_Y + SoulGuiStyle.LINE_HEIGHT;
    }

    private int statusY()
    {
        return loadoutShown() ? slotsY() + SoulGuiStyle.LINE_HEIGHT : slotsY();
    }

    /**
     * The status band is reserved at the height of the tallest notice, not the height of whatever is
     * showing. A notice that appeared and pushed the list down would move the rows under the cursor
     * on the very click that changed them.
     */
    private int statusRows()
    {
        return loadoutShown() || this.notice != SyncSoulAnchorMessage.Notice.NONE ? this.noticeRows : 0;
    }

    private int plateTop()
    {
        return statusY() + statusRows() * SoulGuiStyle.LINE_HEIGHT + SECTION_GAP;
    }

    private int plateBottom()
    {
        return SoulGuiStyle.footerTop(this.height) - SoulGuiStyle.FOOTER_GAP;
    }

    private int plateRight()
    {
        return this.width - SoulGuiStyle.MARGIN;
    }

    /** The scrollable view sits inside the plate's padding, so text never touches the border. */
    private int viewLeft()
    {
        return SoulGuiStyle.MARGIN + SoulGuiStyle.PANEL_PADDING;
    }

    private int viewRight()
    {
        return plateRight() - SoulGuiStyle.PANEL_PADDING;
    }

    private int viewTop()
    {
        return plateTop() + SoulGuiStyle.PANEL_PADDING;
    }

    private int viewBottom()
    {
        return plateBottom() - SoulGuiStyle.PANEL_PADDING;
    }

    private boolean panelFits()
    {
        return viewBottom() > viewTop()
                && viewRight() - viewLeft() > SoulGuiStyle.SCROLLBAR_WIDTH + SoulGuiStyle.SCROLLBAR_GUTTER;
    }

    /** The slots line's neighbour: the hint, or the notice that replaces it for a few seconds. */
    private void drawStatus(GuiGraphics graphics, int width)
    {
        if (drawNotice(graphics, width))
        {
            return;
        }

        if (loadoutShown())
        {
            SoulGuiStyle.drawTruncated(graphics, this.font, Component.translatable(this.ascension.owner()
                            ? Constants.StringKeys.ANCHOR_SCREEN_HINT
                            : Constants.StringKeys.ANCHOR_SCREEN_VISITOR),
                    SoulGuiStyle.MARGIN, statusY(), width, SoulGuiStyle.MUTED);
        }
    }

    /**
     * What the server made of the last click, for a few seconds in the status row's place. A refusal
     * used to go to chat, which is not drawn behind this screen, so a full loadout read as a click that
     * did nothing (#279). It is drawn whether or not there is a loadout to show, because a click on a
     * soul with no slots can still be refused.
     *
     * @return whether the notice holds the status row, drawn or fading
     */
    private boolean drawNotice(GuiGraphics graphics, int width)
    {
        if (this.notice == SyncSoulAnchorMessage.Notice.NONE || this.noticeAge > NOTICE_TICKS)
        {
            return false;
        }

        final int alpha = noticeAlpha();

        if (alpha < NOTICE_MIN_ALPHA)
        {
            return true;
        }

        final int colour = (alpha << 24) | noticeRgb();
        int y = statusY();

        for (FormattedCharSequence line : this.font.split(Component.translatable(noticeKey()), width))
        {
            graphics.drawString(this.font, line, SoulGuiStyle.MARGIN, y, colour);
            y += SoulGuiStyle.LINE_HEIGHT;
        }

        return true;
    }

    private int noticeAlpha()
    {
        final int remaining = NOTICE_TICKS - this.noticeAge;

        return remaining >= NOTICE_FADE_TICKS ? 0xFF : Math.max(0, remaining) * 0xFF / NOTICE_FADE_TICKS;
    }

    private String noticeKey()
    {
        return switch (this.notice)
        {
            case BOUND -> Constants.StringKeys.ATTUNE_BOUND;
            case UNBOUND -> Constants.StringKeys.ATTUNE_UNBOUND;
            default -> Constants.StringKeys.ATTUNE_NO_SLOTS;
        };
    }

    private int noticeRgb()
    {
        return switch (this.notice)
        {
            case BOUND -> SoulGuiStyle.POSITIVE;
            case NO_SLOTS -> SoulGuiStyle.NEGATIVE;
            default -> SoulGuiStyle.TEXT;
        };
    }

    private int measureNoticeRows()
    {
        final int width = this.width - 2 * SoulGuiStyle.MARGIN;
        int rows = 1;

        for (String key : NOTICE_KEYS)
        {
            rows = Math.max(rows, this.font.split(Component.translatable(key), width).size());
        }

        return rows;
    }

    /** The button says what pressing it would actually yield, and is not there when that is nothing. */
    private void updateResidueButton()
    {
        if (this.residueButton == null)
        {
            return;
        }

        final AscensionReport.Residue residue = this.ascension.residue();
        final boolean shown = this.ascension.owner() && residue.worthSaying();

        this.residueButton.visible = shown;
        this.residueButton.active = shown && residue.essence() > 0;
        this.residueButton.setTooltip(Tooltip.create(Component.translatable(residue.essence() > 0
                ? Constants.StringKeys.ANCHOR_SCREEN_COLLECT_TIP
                : Constants.StringKeys.ANCHOR_SCREEN_COLLECT_NONE_TIP)));

        layoutFooter();
    }

    private Component collectLabel()
    {
        final int essence = this.ascension.residue().essence();

        return essence > 0
                ? Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_COLLECT, essence)
                : Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_COLLECT_NONE);
    }

    /**
     * Close sits at the right margin and the residue button at the left, both measured to their labels.
     * When a long translation would make them collide they shrink together, in proportion, and their
     * labels are cut to fit - a button that ran into its neighbour would be a button a click could land on
     * the wrong side of.
     */
    private void layoutFooter()
    {
        if (this.closeButton == null || this.residueButton == null)
        {
            return;
        }

        final int y = SoulGuiStyle.footerTop(this.height);
        final int available = this.width - 2 * SoulGuiStyle.MARGIN;
        final boolean collectShown = this.residueButton.visible;

        final Component closeFull = Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE);
        final Component collectFull = collectLabel();

        int closeWidth = SoulGuiStyle.buttonWidth(this.font, closeFull);
        int collectWidth = collectShown ? SoulGuiStyle.buttonWidth(this.font, collectFull) : 0;
        Component closeText = closeFull;
        Component collectText = collectFull;

        final int room = collectShown ? available - BUTTON_GAP : available;
        final int wanted = closeWidth + collectWidth;

        if (wanted > room)
        {
            closeWidth = Math.max(1, (int) ((long) closeWidth * room / wanted));
            collectWidth = collectShown ? Math.max(1, room - closeWidth) : 0;

            closeText = SoulGuiStyle.truncate(this.font, closeFull, closeWidth - SoulGuiStyle.BUTTON_PAD);

            if (collectShown)
            {
                collectText = SoulGuiStyle.truncate(this.font, collectFull, collectWidth - SoulGuiStyle.BUTTON_PAD);
            }
        }

        this.closeButton.setMessage(closeText);
        this.closeButton.setWidth(closeWidth);
        this.closeButton.setPosition(this.width - SoulGuiStyle.MARGIN - closeWidth, y);

        this.residueButton.setMessage(collectText);
        this.residueButton.setWidth(collectWidth);
        this.residueButton.setPosition(SoulGuiStyle.MARGIN, y);
    }

    /**
     * The clickable row under the cursor, or null. Only the owner has rows to click, and the hit area
     * is the text column: the scrollbar and its gutter belong to the panel's own drag.
     */
    private Row rowAt(double mouseX, double mouseY)
    {
        if (!this.report.owner() || !panelFits()
                || mouseX < viewLeft() || mouseX >= viewRight() - SoulGuiStyle.SCROLLBAR_WIDTH - SoulGuiStyle.SCROLLBAR_GUTTER
                || mouseY < viewTop() || mouseY > viewBottom())
        {
            return null;
        }

        final int contentY = (int) (mouseY - viewTop()) + this.panel.scrollOffset();

        for (Row row : this.rows)
        {
            if (contentY >= row.top() && contentY < row.top() + row.height())
            {
                return row;
            }
        }

        return null;
    }

    private void drawHover(GuiGraphics graphics, Row row)
    {
        final int top = Math.max(viewTop(), viewTop() + row.top() - this.panel.scrollOffset());
        final int bottom = Math.min(viewBottom(), viewTop() + row.top() + row.height() - this.panel.scrollOffset());

        if (bottom > top)
        {
            graphics.fill(viewLeft(), top, viewRight(), bottom, SoulGuiStyle.ROW_HOVER);
        }
    }

    /**
     * The checkbox is drawn over the panel's text rather than as a glyph, so it reads the same at any
     * scale and carries no "[x]" literal to translate. It is clipped to the view separately: the panel's
     * own scissor has already been lifted when this runs.
     */
    private void drawCheckboxes(GuiGraphics graphics)
    {
        graphics.enableScissor(viewLeft(), viewTop(), viewRight(), viewBottom());

        for (Row row : this.rows)
        {
            final int x = viewLeft();
            // one pixel below the line's top: the box is centred on the capitals, which sit low in the line
            final int y = viewTop() + row.top() - this.panel.scrollOffset() + 1;
            final int size = CHECKBOX_SIZE;

            graphics.fill(x, y, x + size, y + 1, SoulGuiStyle.MUTED);
            graphics.fill(x, y + size - 1, x + size, y + size, SoulGuiStyle.MUTED);
            graphics.fill(x, y, x + 1, y + size, SoulGuiStyle.MUTED);
            graphics.fill(x + size - 1, y, x + size, y + size, SoulGuiStyle.MUTED);

            if (row.attuned())
            {
                // a ghost is ticked too, but in the colour its name is drawn in - it is bound, and gone
                graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1,
                        row.present() ? SoulGuiStyle.POSITIVE : SoulGuiStyle.CAPPED);
            }
        }

        graphics.disableScissor();
    }

    private static Component tooltipFor(Row row)
    {
        if (!row.present())
        {
            return Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_GHOST_TIP);
        }

        return Component.translatable(row.attuned()
                ? Constants.StringKeys.ANCHOR_SCREEN_RELEASE_TIP
                : Constants.StringKeys.ANCHOR_SCREEN_ATTUNE_TIP);
    }

    /**
     * The climb first, then the rooms. A player who clicked the anchor asked one of two questions,
     * and the one that cannot be answered anywhere else is what the ascension still needs - the
     * loadout is also on the lens and in {@code /soulhome buffs}.
     *
     * <p>Attuned rooms before the rest, for the same reason: the list a player scans for "what am I
     * carrying" is the short one, and burying it inside thirty rooms in scan order makes the screen
     * answer the question it was opened for last.
     */
    private List<ScrollableDetailPanel.VisualLine> buildLines(int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();

        this.rows.clear();

        int contentY = addAscension(out, maxWidth);

        if (!loadoutShown())
        {
            return out;
        }

        if (this.report.isEmpty())
        {
            out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NO_ROOMS), 0, SoulGuiStyle.MUTED, maxWidth));
            return out;
        }

        contentY = addSection(out, contentY, maxWidth, Constants.StringKeys.ANCHOR_SCREEN_ATTUNED, true);
        addSection(out, contentY, maxWidth, Constants.StringKeys.ANCHOR_SCREEN_DORMANT, false);

        out.add(ScrollableDetailPanel.VisualLine.spacer(SECTION_GAP));
        out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NOT_LOST), 0, SoulGuiStyle.MUTED, maxWidth));

        return out;
    }

    /**
     * The ascension summary, in the words it was printed in - the requirement lines are the same
     * {@code message.soulhome.anchor.*} strings the chat readout used, because they say the same
     * thing and two copies of a sentence drift.
     *
     * @return the content height used, so the room rows below it know where they start
     */
    private int addAscension(List<ScrollableDetailPanel.VisualLine> out, int maxWidth)
    {
        final AscensionReport climb = this.ascension;

        int height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_ASCENSION), 0, SoulGuiStyle.HEADER, maxWidth);

        height += add(out, Component.translatable(
                Constants.StringKeys.ANCHOR_RANK, SoulBounds.rankLabel(climb.rank())), INDENT, SoulGuiStyle.TEXT, maxWidth);

        if (!climb.enabled())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ASCENT_DISABLED), INDENT, SoulGuiStyle.MUTED, maxWidth);
        }
        else if (climb.maxed())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_MAXED), INDENT, SoulGuiStyle.MUTED, maxWidth);
        }
        else if (climb.ritualElsewhere())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_IN_PROGRESS), INDENT, SoulGuiStyle.WARNING, maxWidth);
        }
        else
        {
            height += addRequirements(out, climb, maxWidth);
        }

        height += addResidue(out, climb.residue(), maxWidth);

        out.add(ScrollableDetailPanel.VisualLine.spacer(SECTION_GAP));

        return height + SECTION_GAP;
    }

    private int addRequirements(List<ScrollableDetailPanel.VisualLine> out, AscensionReport climb, int maxWidth)
    {
        int height;

        if (climb.pillarValid())
        {
            height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_OK), INDENT, SoulGuiStyle.POSITIVE, maxWidth);
        }
        else if (!climb.pillarHasBase())
        {
            height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_NO_BASE), INDENT, SoulGuiStyle.NEGATIVE, maxWidth);
        }
        else
        {
            height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_GAP, climb.pillarGap()),
                    INDENT, SoulGuiStyle.NEGATIVE, maxWidth);
        }

        height += add(out, Component.translatable(
                        climb.willpowerMet()
                                ? Constants.StringKeys.ANCHOR_WILLPOWER_OK
                                : Constants.StringKeys.ANCHOR_WILLPOWER_MISSING,
                        climb.willpowerHave(), climb.willpowerRequired()),
                INDENT, climb.willpowerMet() ? SoulGuiStyle.POSITIVE : SoulGuiStyle.NEGATIVE, maxWidth);

        height += add(out, Component.translatable(
                        climb.essenceMet()
                                ? Constants.StringKeys.ANCHOR_ESSENCE_OK
                                : Constants.StringKeys.ANCHOR_ESSENCE_MISSING,
                        climb.essenceHave(), climb.essenceRequired(), SoulBounds.rankLabel(climb.targetRank())),
                INDENT, climb.essenceMet() ? SoulGuiStyle.POSITIVE : SoulGuiStyle.NEGATIVE, maxWidth);

        if (climb.ready())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_READY), INDENT, SoulGuiStyle.POSITIVE, maxWidth);
        }

        return height;
    }

    /**
     * What the soul has banked and what the button beneath would turn it into. A conversion that
     * just happened says so here rather than in chat, which is not on screen to be read.
     */
    private int addResidue(List<ScrollableDetailPanel.VisualLine> out, AscensionReport.Residue residue, int maxWidth)
    {
        if (!residue.worthSaying())
        {
            return 0;
        }

        final String banked = SoulGuiStyle.oneDecimal(residue.banked());

        int height = add(out, residue.essence() > 0
                        ? Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_RESIDUE, banked, residue.essence())
                        : Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_RESIDUE_SHORT, banked),
                INDENT, COLOR_RESIDUE, maxWidth);

        if (residue.collected() > 0)
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_RESIDUE_CONVERTED, residue.collected()),
                    INDENT, SoulGuiStyle.POSITIVE, maxWidth);
        }

        return height;
    }

    private int addSection(
            List<ScrollableDetailPanel.VisualLine> out, int contentY, int maxWidth, String headerKey, boolean attuned)
    {
        List<AttunementReport.Room> matching = new ArrayList<>();

        for (AttunementReport.Room room : this.report.rooms())
        {
            if (room.attuned() == attuned)
            {
                matching.add(room);
            }
        }

        if (matching.isEmpty())
        {
            return contentY;
        }

        List<ScrollableDetailPanel.VisualLine> header =
                wrap(Component.translatable(headerKey), 0, SoulGuiStyle.HEADER, maxWidth);

        out.addAll(header);
        contentY += heightOf(header);

        for (AttunementReport.Room room : matching)
        {
            List<ScrollableDetailPanel.VisualLine> lines = roomLines(room, maxWidth);
            final int height = heightOf(lines);

            // a ghost cannot be bound again from here - the room is not standing anywhere - but it
            // can be let go of, which is the whole reason it is on the list
            this.rows.add(new Row(room.roomId(), room.attuned(), room.present(), contentY, height));

            out.addAll(lines);
            contentY += height;
        }

        out.add(ScrollableDetailPanel.VisualLine.spacer(SECTION_GAP));

        return contentY + SECTION_GAP;
    }

    private List<ScrollableDetailPanel.VisualLine> roomLines(AttunementReport.Room room, int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> lines = new ArrayList<>();
        final int color = room.attuned()
                ? (room.present() ? SoulGuiStyle.POSITIVE : SoulGuiStyle.CAPPED)
                : SoulGuiStyle.MUTED;

        // the checkbox is drawn beside this line rather than typed into it, so the name starts at the indent
        MutableComponent name = (room.hasAspect()
                        ? Component.translatable(
                                Constants.StringKeys.ANCHOR_SCREEN_ROOM_ASPECT,
                                Component.translatable(room.displayName()),
                                Component.translatable(room.aspectName()),
                                room.tier())
                        : Component.translatable(
                                Constants.StringKeys.ANCHOR_SCREEN_ROOM,
                                Component.translatable(room.displayName()),
                                room.tier()))
                .append(Component.literal(" "))
                .append(Component.translatable(room.roomPool() == RoomPool.ACTIVE
                        ? Constants.StringKeys.ANCHOR_SCREEN_ACTIVE
                        : Constants.StringKeys.ANCHOR_SCREEN_PASSIVE));

        lines.addAll(wrap(name, ROW_INDENT, color, maxWidth));

        if (!room.present())
        {
            lines.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_GONE), ROW_INDENT, SoulGuiStyle.CAPPED, maxWidth));
            return lines;
        }

        for (AttunementReport.Grant grant : room.grants())
        {
            // names and units, never ids, and formatted by the same BuffNames /soulhome buffs uses,
            // so the two cannot drift into describing the same magnitude differently
            lines.addAll(wrap(Component.translatable(
                            room.attuned()
                                    ? Constants.StringKeys.ANCHOR_SCREEN_GRANT
                                    : Constants.StringKeys.ANCHOR_SCREEN_WOULD_GRANT,
                            BuffNames.name(grant.buffType()),
                            BuffNames.magnitude(grant.buffType(), grant.magnitude())),
                    ROW_INDENT, SoulGuiStyle.TEXT, maxWidth));
        }

        return lines;
    }

    /** Appends one wrapped line and answers how much content height it took. */
    private int add(List<ScrollableDetailPanel.VisualLine> out, Component text, int indent, int color, int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> lines = wrap(text, indent, color, maxWidth);

        out.addAll(lines);

        return heightOf(lines);
    }

    private static int heightOf(List<ScrollableDetailPanel.VisualLine> lines)
    {
        int height = 0;

        for (ScrollableDetailPanel.VisualLine line : lines)
        {
            height += line.height();
        }

        return height;
    }

    private List<ScrollableDetailPanel.VisualLine> wrap(Component text, int indent, int color, int maxWidth)
    {
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, SoulGuiStyle.LINE_HEIGHT);
    }

    /**
     * One clickable room, in content space - see {@link ScrollableDetailPanel#scrollOffset}. A row is
     * {@code present} only while its room is standing; a ghost is bound but absent.
     */
    private record Row(int roomId, boolean attuned, boolean present, int top, int height)
    {
    }
}
