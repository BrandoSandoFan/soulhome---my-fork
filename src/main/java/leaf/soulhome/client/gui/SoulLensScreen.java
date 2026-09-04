/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.BuffNames;
import leaf.soulhome.feedback.LensRegionReport;
import leaf.soulhome.network.SyncSoulBoundsMessage;
import leaf.soulhome.structures.core.SoulBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private static final int LIST_LEFT = 12;
    private static final int BOX_SUMMARY_TOP = 20;
    private static final int LIST_TOP = 55;
    private static final int ROW_HEIGHT = 22;

    private static final int DETAIL_LEFT = LIST_LEFT + LIST_WIDTH + 14;
    private static final int RIGHT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 26;
    private static final int LINE_HEIGHT = 11;

    private static final int MAX_MATCHED_SHOWN = 6;
    private static final int MAX_MISSING_SHOWN = 5;
    private static final int MAX_CLAUSES_SHOWN = 6;
    private static final int MAX_BUFFS_SHOWN = 4;

    private static final int COLOR_TITLE = 0xE0E0FF;
    private static final int COLOR_HEADER = 0xC7A6FF;
    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_MUTED = 0xA0A0A0;
    private static final int COLOR_HIT = 0x55FF55;
    private static final int COLOR_MISS = 0xAAAAAA;
    private static final int COLOR_MISSING = 0x5599FF;
    private static final int COLOR_CAPPED = 0xFFAA00;
    private static final int COLOR_BAR_BACK = 0xFF2A2A2E;
    private static final int COLOR_BAR_FILL = 0xFF55AAFF;

    private final List<LensRegionReport> regions;
    private final ScrollableDetailPanel detailPanel = new ScrollableDetailPanel();
    private int selected;

    /** Index of the region drawn in the top row. See {@link #rebuildRows()}. */
    private int listOffset;

    public SoulLensScreen(List<LensRegionReport> regions, int standingIn)
    {
        super(Component.translatable(Constants.StringKeys.LENS_SCREEN_TITLE));
        this.regions = regions;
        this.selected = standingIn >= 0 && standingIn < regions.size() ? standingIn : 0;
    }

    @Override
    protected void init()
    {
        // start showing the row that is already selected. Without this, walking into the tenth
        // region of a busy soulhome opens the lens on a list scrolled to the top and a detail
        // panel describing a room whose row is nowhere on screen
        this.listOffset = Math.max(0, Math.min(this.selected, this.regions.size() - visibleRows()));

        rebuildRows();
    }

    /** How many list rows fit between the box summary and the close button. */
    private int visibleRows()
    {
        return Math.max(1, (this.height - BOTTOM_MARGIN - LIST_TOP) / ROW_HEIGHT);
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

        final int last = Math.min(this.regions.size(), this.listOffset + visibleRows());

        for (int i = this.listOffset; i < last; i++)
        {
            final int index = i;

            this.addRenderableWidget(Button.builder(rowLabel(this.regions.get(i)), button ->
                    {
                        this.selected = index;
                        this.detailPanel.resetScroll();
                    })
                    .bounds(LIST_LEFT, rowY(i), LIST_WIDTH, ROW_HEIGHT - 4)
                    .build());
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE), button -> this.onClose())
                .bounds(this.width - 90, this.height - BOTTOM_MARGIN, 80, 20)
                .build());
    }

    private int rowY(int index)
    {
        return LIST_TOP + (index - this.listOffset) * ROW_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);

        if (this.selected >= this.listOffset && this.selected < this.listOffset + visibleRows()
                && this.selected < this.regions.size())
        {
            final int rowY = rowY(this.selected);

            graphics.fill(LIST_LEFT - 2, rowY - 2, LIST_LEFT + LIST_WIDTH + 2, rowY + ROW_HEIGHT - 2, 0x805599FF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);
        drawBoxSummary(graphics);

        if (this.regions.isEmpty())
        {
            return;
        }

        drawDetail(graphics, this.regions.get(this.selected));
    }

    /**
     * The box a soulhome is bounded by (#78/#79/#81), one line: rank, build layers, verge, and a
     * legacy note if this soulhome predates the box. Rule 5 of the Ascent epic is that scarcity
     * must be legible, and this is where a player already is when they are deciding whether they
     * need more of it.
     */
    private void drawBoxSummary(GuiGraphics graphics)
    {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            return;
        }

        final SyncSoulBoundsMessage bounds = SyncSoulBoundsMessage.ClientSoulBounds.forDimension(
                minecraft.level.dimension().location().toString());

        if (bounds.getCeilingY() <= bounds.getFloorY())
        {
            // no box known for this dimension - bounds are off, or nothing has synced one yet
            return;
        }

        MutableComponent line = Component.translatable(
                        Constants.StringKeys.LENS_SCREEN_BOX_RANK, SoulBounds.rankLabel(bounds.getRank()))
                .append(Component.literal("  "))
                .append(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_LAYERS,
                        bounds.getCeilingY() - bounds.getFloorY(), bounds.getFloorY(), bounds.getCeilingY()))
                .append(Component.literal("  "))
                .append(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_VERGE, bounds.getVergeHalfExtent()));

        if (!bounds.getLegacyBox().isEmpty())
        {
            line = line.append(Component.literal("  "))
                    .append(Component.translatable(Constants.StringKeys.LENS_SCREEN_BOX_LEGACY, legacyBoxText(bounds)));
        }

        final int maxWidth = Math.max(20, this.width - LIST_LEFT - RIGHT_MARGIN);
        int y = BOX_SUMMARY_TOP;

        for (ScrollableDetailPanel.VisualLine visualLine
                : ScrollableDetailPanel.wrap(this.font, line, 0, COLOR_MUTED, maxWidth, LINE_HEIGHT))
        {
            graphics.drawString(this.font, visualLine.text(), LIST_LEFT, y, visualLine.color());
            y += visualLine.height();
        }
    }

    private static String legacyBoxText(SyncSoulBoundsMessage bounds)
    {
        final List<Integer> box = bounds.getLegacyBox();

        return "x " + box.get(0) + ".." + box.get(3)
                + ", y " + box.get(1) + ".." + box.get(4)
                + ", z " + box.get(2) + ".." + box.get(5);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        final int bottom = this.height - BOTTOM_MARGIN;

        if (this.detailPanel.scroll(mouseX, mouseY, delta, DETAIL_LEFT, LIST_TOP, bottom))
        {
            return true;
        }

        if (scrollList(mouseX, mouseY, delta, bottom))
        {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** @return true if the cursor was over the region list and the scroll moved it. */
    private boolean scrollList(double mouseX, double mouseY, double delta, int bottom)
    {
        if (mouseX < LIST_LEFT - 2 || mouseX > LIST_LEFT + LIST_WIDTH + 2 || mouseY < LIST_TOP || mouseY > bottom)
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

    private void drawDetail(GuiGraphics graphics, LensRegionReport region)
    {
        if (region.noArchetypes())
        {
            graphics.drawString(this.font,
                    Component.translatable(Constants.StringKeys.LENS_SCREEN_EMPTY_DETAIL), DETAIL_LEFT, LIST_TOP, COLOR_MUTED);
            return;
        }

        final int right = this.width - RIGHT_MARGIN;
        final int maxWidth = Math.max(20, right - DETAIL_LEFT);
        final int bottom = this.height - BOTTOM_MARGIN;

        final List<ScrollableDetailPanel.VisualLine> lines = buildDetailLines(region, maxWidth);

        this.detailPanel.render(graphics, this.font, lines, DETAIL_LEFT, LIST_TOP, right, bottom,
                160, COLOR_BAR_BACK, COLOR_BAR_FILL);
    }

    private List<ScrollableDetailPanel.VisualLine> buildDetailLines(LensRegionReport region, int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();

        out.addAll(wrap(headlineName(region), 0, COLOR_TEXT, maxWidth));

        if (region.isClassified())
        {
            out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_TIER, region.tier()), 0, COLOR_HIT, maxWidth));
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_SCORE, score(region.score())), 0, COLOR_MUTED, maxWidth));
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));

        appendProgress(out, region, maxWidth);

        if (region.isAmbiguous() && region.hasRunnerUp())
        {
            out.addAll(wrap(Component.translatable(
                            Constants.StringKeys.LENS_SCREEN_AMBIGUOUS_DETAIL,
                            Component.translatable(region.runnerUpDisplayName()),
                            score(region.runnerUpScore())),
                    0, COLOR_MUTED, maxWidth));
            out.add(ScrollableDetailPanel.VisualLine.spacer(LINE_HEIGHT));
        }

        appendSignalSection(out, Constants.StringKeys.LENS_SCREEN_SIGNALS_HEADER, region.matched(), MAX_MATCHED_SHOWN, maxWidth);
        appendMissingSection(out, region.missing(), maxWidth);
        appendArrangementSection(out, region, maxWidth);
        appendGrantsSection(out, region, maxWidth);

        return out;
    }

    private void appendProgress(List<ScrollableDetailPanel.VisualLine> out, LensRegionReport region, int maxWidth)
    {
        if (!region.hasNextTier())
        {
            if (region.isClassified())
            {
                out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_MAXED), 0, COLOR_MUTED, maxWidth));
            }

            out.add(ScrollableDetailPanel.VisualLine.spacer(4));
            return;
        }

        final double fraction = region.score() <= 0d
                ? 0d
                : region.score() / (region.score() + region.scoreToNextTier());

        out.add(ScrollableDetailPanel.VisualLine.bar(fraction, 9));

        out.addAll(wrap(Component.translatable(
                        Constants.StringKeys.LENS_SCREEN_NEXT_TIER, score(region.scoreToNextTier()), region.tier() + 1),
                0, COLOR_MUTED, maxWidth));

        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    private void appendSignalSection(List<ScrollableDetailPanel.VisualLine> out, String headerKey,
                                      List<LensRegionReport.Signal> signals, int max, int maxWidth)
    {
        if (signals.isEmpty())
        {
            return;
        }

        out.addAll(wrap(Component.translatable(headerKey), 0, COLOR_HEADER, maxWidth));

        final int shown = Math.min(max, signals.size());

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.Signal signal = signals.get(i);

            String line = signal.description() + " x" + signal.count() + " (" + score(signal.contribution()) + ")";

            if (signal.isCapped())
            {
                line += " *";
            }

            out.addAll(wrap(Component.literal(line), 4,
                    signal.contribution() < 0 ? COLOR_MISS : (signal.isCapped() ? COLOR_CAPPED : COLOR_TEXT), maxWidth));
        }

        appendMoreLine(out, signals.size(), shown, maxWidth);
        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
    }

    private void appendMissingSection(List<ScrollableDetailPanel.VisualLine> out, List<String> missing, int maxWidth)
    {
        if (missing.isEmpty())
        {
            return;
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_MISSING_HEADER), 0, COLOR_HEADER, maxWidth));

        final int shown = Math.min(MAX_MISSING_SHOWN, missing.size());

        for (int i = 0; i < shown; i++)
        {
            out.addAll(wrap(Component.literal(missing.get(i)), 4, COLOR_MISSING, maxWidth));
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

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_ARRANGEMENT_HEADER), 0, COLOR_HEADER, maxWidth));

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
                        clause.hit() ? COLOR_HIT : COLOR_MISS, maxWidth));
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

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_GRANTS_HEADER), 0, COLOR_HEADER, maxWidth));

        final int shown = Math.min(MAX_BUFFS_SHOWN, region.buffs().size());

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.BuffEntry buff = region.buffs().get(i);

            out.addAll(wrap(BuffNames.name(buff.buffType())
                            .append(Component.literal(" " + BuffNames.magnitude(buff.buffType(), buff.magnitude()))),
                    4, COLOR_TEXT, maxWidth));
        }
    }

    private void appendMoreLine(List<ScrollableDetailPanel.VisualLine> out, int total, int shown, int maxWidth)
    {
        if (total <= shown)
        {
            return;
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_MORE, total - shown), 4, COLOR_MUTED, maxWidth));
    }

    private List<ScrollableDetailPanel.VisualLine> wrap(Component text, int indent, int color, int maxWidth)
    {
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, LINE_HEIGHT);
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

    private Component rowLabel(LensRegionReport region)
    {
        final MutableComponent name = headlineName(region);

        return region.isClassified() ? name.append(Component.literal(" T" + region.tier())) : name;
    }

    private static String score(double value)
    {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
