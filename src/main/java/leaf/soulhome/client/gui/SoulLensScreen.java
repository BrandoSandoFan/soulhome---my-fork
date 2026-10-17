/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.BondNames;
import leaf.soulhome.feedback.BuffNames;
import leaf.soulhome.feedback.LensRegionReport;
import leaf.soulhome.network.SyncSoulBoundsMessage;
import leaf.soulhome.network.SyncSoulLensReportMessage;
import leaf.soulhome.structures.core.SoulBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Soul Lens shows in place of the chat wall it used to print (#50).
 *
 * <p>A list of the regions the last scan found down the left, coloured to match the outlines the
 * lens also drew in the world; the selected one explained on the right - what counted, what is
 * missing, how it is arranged, and what it grants. Every number here comes straight off
 * {@link LensRegionReport}, which is itself read verbatim off {@code ArchetypeScore}: nothing is
 * recomputed client-side, so this screen and {@code /soulhome analyse} can never disagree.
 *
 * <p>The world outlines {@code SoulLensRenderer} draws are untouched and keep drawing behind this
 * screen - they are the single most useful thing the lens does, and this screen explains rather
 * than replaces them.
 *
 * <p>The detail panel's content is entirely data-driven - block names, archetype display names,
 * clause text, the number of signals a room matched - so nothing here bounds how wide or how tall
 * it gets. It wraps to the panel width and scrolls rather than running off the screen (#67); see
 * {@link ScrollableDetailPanel}. The region list down the left has the same problem in the other
 * direction - a soulhome may hold far more regions than there are rows for - and scrolls too.
 */
@OnlyIn(Dist.CLIENT)
public class SoulLensScreen extends Screen
{
    private static final int LIST_WIDTH = 120;
    private static final int LIST_LEFT = SoulGuiStyle.MARGIN;
    /** A button draws its label inside its edges, so the cut has to land inside them too. */
    private static final int ROW_LABEL_WIDTH = LIST_WIDTH - 8;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_BUTTON_HEIGHT = ROW_HEIGHT - 4;
    /** Between the list's scrollbar and the selection ring, so neither touches the other. */
    private static final int LIST_BAR_LEFT = LIST_LEFT + LIST_WIDTH + 5;

    private static final int SUMMARY_TOP = SoulGuiStyle.TITLE_Y + SoulGuiStyle.LINE_HEIGHT;
    /**
     * Between the box summary and the list. The detail plate starts PANEL_PADDING above the list, so
     * this keeps the plate's border clear of the summary's last line.
     */
    private static final int SUMMARY_GAP = 8;
    private static final String SUMMARY_JOIN = "  ";

    private static final int DETAIL_LEFT = LIST_LEFT + LIST_WIDTH + 16;
    private static final int DETAIL_BAR_MAX_WIDTH = 160;

    private static final int MAX_MATCHED_SHOWN = 6;
    private static final int MAX_MISSING_SHOWN = 5;
    private static final int MAX_CLAUSES_SHOWN = 6;
    private static final int MAX_BUFFS_SHOWN = 4;
    private static final int MAX_BONDS_SHOWN = 6;

    private final List<LensRegionReport> regions;
    private final ScrollableDetailPanel detailPanel = new ScrollableDetailPanel();
    private int selected;

    /** Index of the region drawn in the top row. See {@link #rebuildRows()}. */
    private int listOffset;

    /** The y of the top row. It follows the box summary's line count, so it is only known once the summary is. */
    private int listTop;

    public SoulLensScreen(List<LensRegionReport> regions, int standingIn)
    {
        super(Component.translatable(Constants.StringKeys.LENS_SCREEN_TITLE));
        this.regions = regions;
        this.selected = standingIn >= 0 && standingIn < regions.size() ? standingIn : 0;

        // the world outlines pick out the selected region and the rooms it is bonded to (#148)
        SyncSoulLensReportMessage.ClientLensReport.select(regions.isEmpty() ? -1 : this.selected);
    }

    @Override
    protected void init()
    {
        this.listTop = listTopFor(boxSummary().size());

        // start showing the row that is already selected. Without this, walking into the tenth
        // region of a busy soulhome opens the lens on a list scrolled to the top and a detail
        // panel describing a room whose row is nowhere on screen
        this.listOffset = Math.max(0, Math.min(this.selected, this.regions.size() - visibleRows()));

        rebuildRows();
    }

    private static int listTopFor(int summaryLines)
    {
        return SUMMARY_TOP + summaryLines * SoulGuiStyle.LINE_HEIGHT + SUMMARY_GAP;
    }

    /** How many list rows fit between the list's top and the bottom of the content. */
    private int visibleRows()
    {
        return Math.max(1, (contentBottom() - this.listTop) / ROW_HEIGHT);
    }

    /** Content ends FOOTER_GAP above the footer row, and so does the plate around it. */
    private int contentBottom()
    {
        return SoulGuiStyle.footerTop(this.height) - SoulGuiStyle.FOOTER_GAP;
    }

    /** The detail text stops PANEL_PADDING short of the plate's edge, so it never sits on the border. */
    private int detailRight()
    {
        return this.width - SoulGuiStyle.MARGIN - SoulGuiStyle.PANEL_PADDING;
    }

    private int detailBottom()
    {
        return contentBottom() - SoulGuiStyle.PANEL_PADDING;
    }

    private int contentWidth()
    {
        return Math.max(20, this.width - 2 * SoulGuiStyle.MARGIN);
    }

    /**
     * The list is a column of buttons rather than a scrolling widget, so scrolling it means
     * rebuilding the rows at the new offset. {@code max_regions} allows 64 regions and about ten
     * rows fit on a default window, so without this the rest of a full soulhome had no row to
     * click and could not be looked at at all.
     */
    private void rebuildRows()
    {
        this.clearWidgets();

        // clearWidgets does not clear focus, so a row that had it would still take Enter after it was removed.
        this.setFocused(null);

        final int last = Math.min(this.regions.size(), this.listOffset + visibleRows());

        for (int i = this.listOffset; i < last; i++)
        {
            final int index = i;

            this.addRenderableWidget(Button.builder(rowLabel(this.regions.get(i)), button -> select(index))
                    .bounds(LIST_LEFT, rowY(i), LIST_WIDTH, ROW_BUTTON_HEIGHT)
                    .build());
        }

        final Component close = Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE);
        final int closeWidth = SoulGuiStyle.buttonWidth(this.font, close);

        this.addRenderableWidget(Button.builder(close, button -> this.onClose())
                .bounds(this.width - SoulGuiStyle.MARGIN - closeWidth, SoulGuiStyle.footerTop(this.height),
                        closeWidth, SoulGuiStyle.BUTTON_HEIGHT)
                .build());
    }

    private int rowY(int index)
    {
        return this.listTop + (index - this.listOffset) * ROW_HEIGHT;
    }

    /**
     * What clicking a row does, and what the arrow keys do too. Both go through here so a keyboard
     * player and a mouse player are looking at the same thing after the same input.
     */
    private void select(int index)
    {
        this.selected = index;
        this.detailPanel.resetScroll();
        SyncSoulLensReportMessage.ClientLensReport.select(index);

        if (scrollToSelected())
        {
            rebuildRows();
        }
    }

    /** @return true if the list moved, so its rows need rebuilding at the new offset. */
    private boolean scrollToSelected()
    {
        final int rows = visibleRows();
        final int before = this.listOffset;
        int offset = Math.max(0, Math.min(this.listOffset, this.regions.size() - rows));

        if (this.selected < offset)
        {
            offset = this.selected;
        }
        else if (this.selected >= offset + rows)
        {
            offset = this.selected - rows + 1;
        }

        this.listOffset = offset;

        return offset != before;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (!this.regions.isEmpty() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN))
        {
            final int step = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
            final int next = Math.max(0, Math.min(this.regions.size() - 1, this.selected + step));

            if (next != this.selected)
            {
                select(next);
            }

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);

        // the summary arrives over the network, so it can change length after the screen has opened
        final List<Component> summary = boxSummary();
        final int top = listTopFor(summary.size());

        if (top != this.listTop)
        {
            this.listTop = top;
            scrollToSelected();
            rebuildRows();
        }

        if (this.selected >= this.listOffset && this.selected < this.listOffset + visibleRows()
                && this.selected < this.regions.size())
        {
            // a ring rather than a fill, so the button's own label and hover state stay readable
            graphics.renderOutline(LIST_LEFT - 2, rowY(this.selected) - 2, LIST_WIDTH + 4, ROW_HEIGHT,
                    SoulGuiStyle.SELECTION);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, SoulGuiStyle.TITLE_Y, SoulGuiStyle.TITLE);
        drawBoxSummary(graphics, summary);
        drawListScrollBar(graphics);

        if (this.regions.isEmpty())
        {
            drawEmptyState(graphics);
            return;
        }

        drawDetail(graphics, this.regions.get(this.selected), mouseX, mouseY);
    }

    /**
     * The box a soulhome is bounded by (#78/#79/#81), one line: rank, build layers, verge, and a
     * legacy note if this soulhome predates the box. Rule 5 of the Ascent epic is that scarcity
     * must be legible, and this is where a player already is when they are deciding whether they
     * need more of it.
     */
    private List<Component> boxSummary()
    {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            return List.of();
        }

        final SyncSoulBoundsMessage bounds = SyncSoulBoundsMessage.ClientSoulBounds.forDimension(
                minecraft.level.dimension().location().toString());

        if (bounds.getCeilingY() <= bounds.getFloorY())
        {
            // no box known for this dimension - bounds are off, or nothing has synced one yet
            return List.of();
        }

        final List<MutableComponent> pieces = new ArrayList<>();

        pieces.add(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_RANK, SoulBounds.rankLabel(bounds.getRank())));
        pieces.add(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_LAYERS,
                bounds.getCeilingY() - bounds.getFloorY(), bounds.getFloorY(), bounds.getCeilingY()));
        pieces.add(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_VERGE, bounds.getVergeHalfExtent()));
        // ground beside walls, because they are different numbers and the gap between them
        // is the open verge terrain growth deliberately leaves (#158/#162)
        pieces.add(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_GROUND, bounds.getGroundReach()));

        if (bounds.isGrowing())
        {
            pieces.add(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_GROWING));
        }

        if (!bounds.getLegacyBox().isEmpty())
        {
            pieces.add(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_LEGACY, legacyBoxText(bounds)));
        }

        return packLines(pieces, contentWidth());
    }

    /**
     * The summary's pieces, packed onto two lines with each piece kept whole. Wrapping the joined text
     * at the font's own break would cut a piece in half, and cutting the last line would need the rest
     * of the text as a string, which the font does not hand back once it has wrapped. Whatever does not
     * fit on the second line is cut with an ellipsis instead.
     */
    private List<Component> packLines(List<MutableComponent> pieces, int maxWidth)
    {
        final List<Component> lines = new ArrayList<>();
        MutableComponent first = pieces.get(0);
        int next = 1;

        while (next < pieces.size())
        {
            final MutableComponent joined = first.copy().append(SUMMARY_JOIN).append(pieces.get(next));

            if (this.font.width(joined) > maxWidth)
            {
                break;
            }

            first = joined;
            next++;
        }

        lines.add(first);

        if (next < pieces.size())
        {
            final MutableComponent rest = pieces.get(next).copy();

            for (int i = next + 1; i < pieces.size(); i++)
            {
                rest.append(SUMMARY_JOIN).append(pieces.get(i));
            }

            lines.add(SoulGuiStyle.truncate(this.font, rest, maxWidth));
        }

        return lines;
    }

    private void drawBoxSummary(GuiGraphics graphics, List<Component> lines)
    {
        int y = SUMMARY_TOP;

        for (Component line : lines)
        {
            SoulGuiStyle.drawTruncated(graphics, this.font, line, LIST_LEFT, y, contentWidth(), SoulGuiStyle.MUTED);
            y += SoulGuiStyle.LINE_HEIGHT;
        }
    }

    private static MutableComponent legacyBoxText(SyncSoulBoundsMessage bounds)
    {
        final List<Integer> box = bounds.getLegacyBox();

        return Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_LEGACY_BOUNDS,
                box.get(0), box.get(3), box.get(1), box.get(4), box.get(2), box.get(5));
    }

    /**
     * Shows where the list sits when rows are hidden above or below. The thumb is the same bar the
     * detail panel draws, so a player who has learnt one scrollbar has learnt both.
     */
    private void drawListScrollBar(GuiGraphics graphics)
    {
        final int total = this.regions.size();
        final int rows = visibleRows();

        if (total <= rows)
        {
            return;
        }

        // the track ends at the bottom of the last row's button, not at the row's slot
        final int trackHeight = rows * ROW_HEIGHT - (ROW_HEIGHT - ROW_BUTTON_HEIGHT);
        final int thumbHeight = Math.max(10, trackHeight * rows / total);
        final int thumbTop = this.listTop + (trackHeight - thumbHeight) * this.listOffset / (total - rows);

        graphics.fill(LIST_BAR_LEFT, this.listTop, LIST_BAR_LEFT + SoulGuiStyle.SCROLLBAR_WIDTH,
                this.listTop + trackHeight, SoulGuiStyle.SCROLL_TRACK);
        graphics.fill(LIST_BAR_LEFT, thumbTop, LIST_BAR_LEFT + SoulGuiStyle.SCROLLBAR_WIDTH,
                thumbTop + thumbHeight, SoulGuiStyle.SCROLL_THUMB);
    }

    /** With no regions there is no list and no detail, so the plate says why there is nothing here. */
    private void drawEmptyState(GuiGraphics graphics)
    {
        final int left = SoulGuiStyle.MARGIN;
        final int right = this.width - SoulGuiStyle.MARGIN;
        final int top = this.listTop - SoulGuiStyle.PANEL_PADDING;
        final int bottom = contentBottom();

        SoulGuiStyle.drawPanel(graphics, left, top, right, bottom);

        final List<ScrollableDetailPanel.VisualLine> lines = wrap(
                Component.translatable(Constants.StringKeys.LENS_SCREEN_NO_REGIONS), 0, SoulGuiStyle.MUTED,
                right - left - 2 * SoulGuiStyle.PANEL_PADDING);

        int y = (top + bottom) / 2 - lines.size() * SoulGuiStyle.LINE_HEIGHT / 2;

        for (ScrollableDetailPanel.VisualLine line : lines)
        {
            graphics.drawCenteredString(this.font, line.text(), this.width / 2, y, line.color());
            y += line.height();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.detailPanel.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (this.detailPanel.mouseDragged(mouseX, mouseY))
        {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        this.detailPanel.mouseReleased();

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (this.detailPanel.scroll(mouseX, mouseY, delta, DETAIL_LEFT, this.listTop, detailRight(), detailBottom()))
        {
            return true;
        }

        if (scrollList(mouseX, mouseY, delta))
        {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** @return true if the cursor was over the region list and the scroll moved it. */
    private boolean scrollList(double mouseX, double mouseY, double delta)
    {
        if (mouseX < LIST_LEFT - 2 || mouseX > LIST_BAR_LEFT + SoulGuiStyle.SCROLLBAR_WIDTH
                || mouseY < this.listTop || mouseY > contentBottom())
        {
            return false;
        }

        final int furthest = Math.max(0, this.regions.size() - visibleRows());
        final int moved = Math.max(0, Math.min(furthest, this.listOffset - (int) Math.signum(delta)));

        if (moved == this.listOffset)
        {
            return false;
        }

        this.listOffset = moved;
        rebuildRows();

        return true;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void drawDetail(GuiGraphics graphics, LensRegionReport region, int mouseX, int mouseY)
    {
        final int maxWidth = ScrollableDetailPanel.textWidth(DETAIL_LEFT, detailRight());

        final List<ScrollableDetailPanel.VisualLine> lines = region.noArchetypes()
                ? wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_EMPTY_DETAIL), 0, SoulGuiStyle.MUTED, maxWidth)
                : buildDetailLines(region, maxWidth);

        SoulGuiStyle.drawPanel(graphics, DETAIL_LEFT - SoulGuiStyle.PANEL_PADDING, this.listTop - SoulGuiStyle.PANEL_PADDING,
                this.width - SoulGuiStyle.MARGIN, contentBottom());

        this.detailPanel.render(graphics, this.font, lines, DETAIL_LEFT, this.listTop, detailRight(), detailBottom(),
                Math.min(DETAIL_BAR_MAX_WIDTH, maxWidth), SoulGuiStyle.BAR_BACK, SoulGuiStyle.BAR_FILL, mouseX, mouseY);
    }

    private List<ScrollableDetailPanel.VisualLine> buildDetailLines(LensRegionReport region, int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();

        out.addAll(wrap(headlineName(region), 0, SoulGuiStyle.TEXT, maxWidth));

        if (region.isClassified())
        {
            out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_TIER, region.tier()), 0, SoulGuiStyle.POSITIVE, maxWidth));
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_SCORE, SoulGuiStyle.oneDecimal(region.score())), 0, SoulGuiStyle.MUTED, maxWidth));
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));

        appendProgress(out, region, maxWidth);

        if (region.isAmbiguous() && region.hasRunnerUp())
        {
            out.addAll(wrap(Component.translatable(
                            Constants.StringKeys.LENS_SCREEN_AMBIGUOUS_DETAIL,
                            Component.translatable(region.runnerUpDisplayName()),
                            SoulGuiStyle.oneDecimal(region.runnerUpScore())),
                    0, SoulGuiStyle.MUTED, maxWidth));
            out.add(ScrollableDetailPanel.VisualLine.spacer(SoulGuiStyle.LINE_HEIGHT));
        }

        final boolean capShown = appendSignalSection(out, Constants.StringKeys.LENS_SCREEN_SIGNALS_HEADER, region.matched(), MAX_MATCHED_SHOWN, maxWidth);
        appendMissingSection(out, region.missing(), maxWidth);
        appendAspectSection(out, region, maxWidth);
        appendArrangementSection(out, region, maxWidth);
        appendBondSection(out, region, maxWidth);
        appendGrantsSection(out, region, maxWidth);

        if (capShown)
        {
            out.add(ScrollableDetailPanel.VisualLine.spacer(4));
            out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_CAPPED_LEGEND), 0, SoulGuiStyle.MUTED, maxWidth));
        }

        return out;
    }

    /**
     * What the room turned out to be for - the Aspects epic (#171) - carrying the same four things
     * {@code /soulhome analyse} says, wrapped and scrolling with the rest of the panel (#67).
     *
     * <p>Silent for a room that took no aspect, which covers both an archetype declaring none and
     * every room on a server with the switch off: there is no config read here, because there is
     * nothing to read - the report simply carries no aspect (#176).
     *
     * <p>The closing line is the one that looks like filler and is not. Two aspects listed with a
     * margin between them read, in this mod, as a split that cost the player something. Saying that
     * it did not is what keeps a feature from being reported as a bug.
     */
    private void appendAspectSection(List<ScrollableDetailPanel.VisualLine> out, LensRegionReport region, int maxWidth)
    {
        if (!region.hasAspect())
        {
            return;
        }

        final LensRegionReport.AspectLine aspect = region.aspect();

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_ASPECT_HEADER), 0, SoulGuiStyle.HEADER, maxWidth));

        out.addAll(wrap(Component.translatable(
                        Constants.StringKeys.REGION_ASPECT_TAKEN, Component.translatable(aspect.displayName())),
                4, SoulGuiStyle.POSITIVE, maxWidth));

        if (aspect.hasRunnerUp())
        {
            out.addAll(wrap(Component.translatable(
                            aspect.heldByDefault()
                                    ? Constants.StringKeys.REGION_ASPECT_HELD
                                    : Constants.StringKeys.REGION_ASPECT_RUNNER_UP,
                            Component.translatable(aspect.runnerUpDisplayName()),
                            SoulGuiStyle.oneDecimal(aspect.margin())),
                    4, SoulGuiStyle.MUTED, maxWidth));
        }

        if (aspect.hasTip())
        {
            // the block name is flattened on the server (BlockNames#text), so it arrives as data, not a key
            out.addAll(wrap(Component.translatable(
                            Constants.StringKeys.REGION_ASPECT_TIP,
                            aspect.tipBlocks(),
                            Component.literal(aspect.tipDescription()),
                            Component.translatable(aspect.tipDisplayName())),
                    4, SoulGuiStyle.MUTED, maxWidth));
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.REGION_ASPECT_FREE), 4, SoulGuiStyle.MUTED, maxWidth));
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    /**
     * The bonds section (#148), matching the signals and arrangement sections above it. A region
     * with nothing to say about bonds says nothing. The rooms a credited bond is with are also
     * picked out in the world outlines behind this screen - see {@code SoulLensRenderer}.
     */
    private void appendBondSection(List<ScrollableDetailPanel.VisualLine> out, LensRegionReport region, int maxWidth)
    {
        if (region.bonds().isEmpty())
        {
            return;
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_BONDS_HEADER), 0, SoulGuiStyle.HEADER, maxWidth));

        final int shown = Math.min(MAX_BONDS_SHOWN, region.bonds().size());

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.BondLine bond = region.bonds().get(i);

            // A discord is credited like any bond - its contribution is just negative - so "+" would be
            // what it reads as. It gets its own mark, because colour alone does not say it is a discord.
            final String prefix = bond.discord() ? "! " : (bond.credited() ? "+ " : "- ");

            MutableComponent line = Component.literal(prefix)
                    .append(BondNames.relation(bond.relationId()))
                    .append(Component.literal(" "))
                    .append(BondNames.room(bond.otherDisplayName(), bond.otherArchetypeId()));

            if (bond.credited())
            {
                line = line.append(Component.literal(" (" + SoulGuiStyle.oneDecimal(bond.contribution()) + ")"));
            }
            else if (!bond.diagnostic().isBlank())
            {
                line = line.append(Component.literal(": " + bond.diagnostic()));
            }

            final int colour = bond.discord() ? SoulGuiStyle.NEGATIVE : (bond.credited() ? SoulGuiStyle.POSITIVE : SoulGuiStyle.MUTED);
            out.addAll(wrap(line, 4, colour, maxWidth));
        }

        appendMoreLine(out, region.bonds().size(), shown, maxWidth);
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    private void appendProgress(List<ScrollableDetailPanel.VisualLine> out, LensRegionReport region, int maxWidth)
    {
        if (!region.hasNextTier())
        {
            if (region.isClassified())
            {
                out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_MAXED), 0, SoulGuiStyle.MUTED, maxWidth));
            }

            out.add(ScrollableDetailPanel.VisualLine.spacer(4));
            return;
        }

        final double fraction = region.score() <= 0d
                ? 0d
                : region.score() / (region.score() + region.scoreToNextTier());

        out.add(ScrollableDetailPanel.VisualLine.bar(fraction, 9));

        out.addAll(wrap(Component.translatable(
                        Constants.StringKeys.LENS_SCREEN_NEXT_TIER, SoulGuiStyle.oneDecimal(region.scoreToNextTier()), region.tier() + 1),
                0, SoulGuiStyle.MUTED, maxWidth));

        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    /** @return true if one of the signals shown was capped, so the legend for its marker is needed. */
    private boolean appendSignalSection(List<ScrollableDetailPanel.VisualLine> out, String headerKey,
                                        List<LensRegionReport.Signal> signals, int max, int maxWidth)
    {
        if (signals.isEmpty())
        {
            return false;
        }

        out.addAll(wrap(Component.translatable(headerKey), 0, SoulGuiStyle.HEADER, maxWidth));

        final int shown = Math.min(max, signals.size());
        boolean capShown = false;

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.Signal signal = signals.get(i);

            MutableComponent line = Component.translatable(Constants.StringKeys.LENS_SCREEN_SIGNAL_LINE,
                    signal.description(), signal.count(), SoulGuiStyle.oneDecimal(signal.contribution()));

            if (signal.isCapped())
            {
                line = line.append(" *");
                capShown = true;
            }

            out.addAll(wrap(line, 4,
                    signal.contribution() < 0 ? SoulGuiStyle.MUTED : (signal.isCapped() ? SoulGuiStyle.CAPPED : SoulGuiStyle.TEXT), maxWidth));
        }

        appendMoreLine(out, signals.size(), shown, maxWidth);
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));

        return capShown;
    }

    private void appendMissingSection(List<ScrollableDetailPanel.VisualLine> out, List<String> missing, int maxWidth)
    {
        if (missing.isEmpty())
        {
            return;
        }

        // "what to add next" is a to-do, not a failed check, so it is waiting rather than negative
        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_MISSING_HEADER), 0, SoulGuiStyle.HEADER, maxWidth));

        final int shown = Math.min(MAX_MISSING_SHOWN, missing.size());

        for (int i = 0; i < shown; i++)
        {
            out.addAll(wrap(Component.literal(missing.get(i)), 4, SoulGuiStyle.WARNING, maxWidth));
        }

        appendMoreLine(out, missing.size(), shown, maxWidth);
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    private void appendArrangementSection(List<ScrollableDetailPanel.VisualLine> out, LensRegionReport region, int maxWidth)
    {
        if (region.forms().isEmpty())
        {
            return;
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_ARRANGEMENT_HEADER), 0, SoulGuiStyle.HEADER, maxWidth));

        int shownClauses = 0;

        for (LensRegionReport.Form form : region.forms())
        {
            if (shownClauses >= MAX_CLAUSES_SHOWN)
            {
                break;
            }

            for (LensRegionReport.ClauseLine clause : form.clauses())
            {
                if (shownClauses >= MAX_CLAUSES_SHOWN)
                {
                    break;
                }

                final String prefix = clause.hit() ? "+ " : "- ";

                out.addAll(wrap(Component.literal(prefix + clause.text()), 4,
                        clause.hit() ? SoulGuiStyle.POSITIVE : SoulGuiStyle.MUTED, maxWidth));
                shownClauses++;
            }
        }

        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    private void appendGrantsSection(List<ScrollableDetailPanel.VisualLine> out, LensRegionReport region, int maxWidth)
    {
        if (region.buffs().isEmpty())
        {
            return;
        }

        out.addAll(wrap(Component.translatable(region.grantsCarried()
                ? Constants.StringKeys.LENS_SCREEN_GRANTS_HEADER
                : Constants.StringKeys.LENS_SCREEN_WOULD_GRANT_HEADER), 0, SoulGuiStyle.HEADER, maxWidth));

        final int shown = Math.min(MAX_BUFFS_SHOWN, region.buffs().size());

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.BuffEntry buff = region.buffs().get(i);

            out.addAll(wrap(BuffNames.name(buff.buffType())
                            .append(Component.literal(" " + BuffNames.magnitude(buff.buffType(), buff.magnitude()))),
                    4, SoulGuiStyle.TEXT, maxWidth));
        }
    }

    private void appendMoreLine(List<ScrollableDetailPanel.VisualLine> out, int total, int shown, int maxWidth)
    {
        if (total <= shown)
        {
            return;
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_MORE, total - shown), 4, SoulGuiStyle.MUTED, maxWidth));
    }

    private List<ScrollableDetailPanel.VisualLine> wrap(Component text, int indent, int color, int maxWidth)
    {
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, SoulGuiStyle.LINE_HEIGHT);
    }

    /** Mirrors {@code SoulReport#headline}: the winning archetype's name, or the generic label. */
    private MutableComponent headlineName(LensRegionReport region)
    {
        if (region.isClassified())
        {
            return Component.translatable(region.displayName());
        }

        if (region.isAmbiguous())
        {
            return Component.translatable(Constants.StringKeys.LENS_SCREEN_AMBIGUOUS);
        }

        return Component.translatable(Constants.StringKeys.LENS_SCREEN_UNCLASSIFIED);
    }

    /**
     * The name, with its tier after it. When the name has to be cut the tier is kept: it is the one
     * number a player scans the list for, and a cut that took it would leave "Library" and
     * "Scriptorium" looking the same down the list.
     */
    private Component rowLabel(LensRegionReport region)
    {
        final MutableComponent name = headlineName(region);

        if (!region.isClassified())
        {
            return SoulGuiStyle.truncate(this.font, name, ROW_LABEL_WIDTH);
        }

        final Component whole = Component.translatable(Constants.StringKeys.LENS_LABEL_TIER, name, region.tier());

        if (this.font.width(whole) <= ROW_LABEL_WIDTH)
        {
            return whole;
        }

        final int nameWidth = ROW_LABEL_WIDTH - this.font.width(Component.translatable(Constants.StringKeys.LENS_LABEL_TIER, "", region.tier()));

        return Component.translatable(Constants.StringKeys.LENS_LABEL_TIER,
                SoulGuiStyle.truncate(this.font, name, nameWidth), region.tier());
    }
}
