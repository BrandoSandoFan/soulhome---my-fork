/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.LensRegionReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
 */
@OnlyIn(Dist.CLIENT)
public class SoulLensScreen extends Screen
{
    private static final int LIST_WIDTH = 120;
    private static final int LIST_LEFT = 12;
    private static final int LIST_TOP = 30;
    private static final int ROW_HEIGHT = 22;

    private static final int DETAIL_LEFT = LIST_LEFT + LIST_WIDTH + 14;
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
    private int selected;

    public SoulLensScreen(List<LensRegionReport> regions, int standingIn)
    {
        super(Component.translatable(Constants.StringKeys.LENS_SCREEN_TITLE));
        this.regions = regions;
        this.selected = standingIn >= 0 && standingIn < regions.size() ? standingIn : 0;
    }

    @Override
    protected void init()
    {
        for (int i = 0; i < this.regions.size(); i++)
        {
            final int index = i;
            final int y = LIST_TOP + i * ROW_HEIGHT;

            if (y + ROW_HEIGHT > this.height - 26)
            {
                break;
            }

            this.addRenderableWidget(Button.builder(rowLabel(this.regions.get(i)), button -> this.selected = index)
                    .bounds(LIST_LEFT, y, LIST_WIDTH, ROW_HEIGHT - 4)
                    .build());
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE), button -> this.onClose())
                .bounds(this.width - 90, this.height - 26, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);

        final int rowY = LIST_TOP + this.selected * ROW_HEIGHT;

        if (this.selected < this.regions.size() && rowY + ROW_HEIGHT <= this.height - 26)
        {
            graphics.fill(LIST_LEFT - 2, rowY - 2, LIST_LEFT + LIST_WIDTH + 2, rowY + ROW_HEIGHT - 2, 0x805599FF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);

        if (this.regions.isEmpty())
        {
            return;
        }

        drawDetail(graphics, this.regions.get(this.selected));
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void drawDetail(GuiGraphics graphics, LensRegionReport region)
    {
        int y = LIST_TOP;

        if (region.noArchetypes())
        {
            graphics.drawString(this.font,
                    Component.translatable(Constants.StringKeys.LENS_SCREEN_EMPTY_DETAIL), DETAIL_LEFT, y, COLOR_MUTED);
            return;
        }

        y = drawHeadline(graphics, region, y);
        y = drawProgress(graphics, region, y);

        if (region.isAmbiguous() && region.hasRunnerUp())
        {
            graphics.drawString(this.font, Component.translatable(
                            Constants.StringKeys.LENS_SCREEN_AMBIGUOUS_DETAIL,
                            Component.translatable(region.runnerUpDisplayName()),
                            score(region.runnerUpScore())),
                    DETAIL_LEFT, y, COLOR_MUTED);
            y += LINE_HEIGHT * 2;
        }

        y = drawSignalSection(graphics, Constants.StringKeys.LENS_SCREEN_SIGNALS_HEADER,
                region.matched(), MAX_MATCHED_SHOWN, y);
        y = drawMissingSection(graphics, region.missing(), y);
        y = drawArrangementSection(graphics, region, y);
        drawGrantsSection(graphics, region, y);
    }

    private int drawHeadline(GuiGraphics graphics, LensRegionReport region, int y)
    {
        graphics.drawString(this.font, headlineName(region), DETAIL_LEFT, y, COLOR_TEXT);
        y += LINE_HEIGHT;

        if (region.isClassified())
        {
            graphics.drawString(this.font,
                    Component.translatable(Constants.StringKeys.LENS_SCREEN_TIER, region.tier()),
                    DETAIL_LEFT, y, COLOR_HIT);
        }

        graphics.drawString(this.font,
                Component.translatable(Constants.StringKeys.LENS_SCREEN_SCORE, score(region.score())),
                DETAIL_LEFT + 60, y, COLOR_MUTED);

        return y + LINE_HEIGHT + 4;
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

    /** The bar toward the next tier - the "30.5 more points" line, made visible rather than read. */
    private int drawProgress(GuiGraphics graphics, LensRegionReport region, int y)
    {
        if (!region.hasNextTier())
        {
            if (region.isClassified())
            {
                graphics.drawString(this.font,
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_MAXED), DETAIL_LEFT, y, COLOR_MUTED);
                y += LINE_HEIGHT;
            }

            return y + 4;
        }

        final int barWidth = 160;
        final int barLeft = DETAIL_LEFT;
        final double fraction = region.score() <= 0d
                ? 0d
                : region.score() / (region.score() + region.scoreToNextTier());

        graphics.fill(barLeft, y, barLeft + barWidth, y + 6, COLOR_BAR_BACK);
        graphics.fill(barLeft, y, barLeft + (int) Math.round(barWidth * Math.min(1d, Math.max(0d, fraction))), y + 6, COLOR_BAR_FILL);
        y += 9;

        graphics.drawString(this.font, Component.translatable(
                        Constants.StringKeys.LENS_SCREEN_NEXT_TIER, score(region.scoreToNextTier()), region.tier() + 1),
                DETAIL_LEFT, y, COLOR_MUTED);

        return y + LINE_HEIGHT + 4;
    }

    private int drawSignalSection(GuiGraphics graphics, String headerKey, List<LensRegionReport.Signal> signals, int max, int y)
    {
        if (signals.isEmpty())
        {
            return y;
        }

        graphics.drawString(this.font, Component.translatable(headerKey), DETAIL_LEFT, y, COLOR_HEADER);
        y += LINE_HEIGHT;

        final int shown = Math.min(max, signals.size());

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.Signal signal = signals.get(i);

            String line = signal.description() + " x" + signal.count() + " (" + score(signal.contribution()) + ")";

            if (signal.isCapped())
            {
                line += " *";
            }

            graphics.drawString(this.font, Component.literal(line), DETAIL_LEFT + 4, y,
                    signal.contribution() < 0 ? COLOR_MISS : (signal.isCapped() ? COLOR_CAPPED : COLOR_TEXT));
            y += LINE_HEIGHT;
        }

        y = drawMoreLine(graphics, signals.size(), shown, y);

        return y + 4;
    }

    private int drawMissingSection(GuiGraphics graphics, List<String> missing, int y)
    {
        if (missing.isEmpty())
        {
            return y;
        }

        graphics.drawString(this.font,
                Component.translatable(Constants.StringKeys.LENS_SCREEN_MISSING_HEADER), DETAIL_LEFT, y, COLOR_HEADER);
        y += LINE_HEIGHT;

        final int shown = Math.min(MAX_MISSING_SHOWN, missing.size());

        for (int i = 0; i < shown; i++)
        {
            graphics.drawString(this.font, Component.literal(missing.get(i)), DETAIL_LEFT + 4, y, COLOR_MISSING);
            y += LINE_HEIGHT;
        }

        y = drawMoreLine(graphics, missing.size(), shown, y);

        return y + 4;
    }

    private int drawArrangementSection(GuiGraphics graphics, LensRegionReport region, int y)
    {
        if (region.forms().isEmpty())
        {
            return y;
        }

        graphics.drawString(this.font,
                Component.translatable(Constants.StringKeys.LENS_SCREEN_ARRANGEMENT_HEADER), DETAIL_LEFT, y, COLOR_HEADER);
        y += LINE_HEIGHT;

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

                graphics.drawString(this.font, Component.literal(prefix + clause.text()), DETAIL_LEFT + 4, y,
                        clause.hit() ? COLOR_HIT : COLOR_MISS);
                y += LINE_HEIGHT;
                shownClauses++;
            }
        }

        return y + 4;
    }

    private void drawGrantsSection(GuiGraphics graphics, LensRegionReport region, int y)
    {
        if (region.buffs().isEmpty())
        {
            return;
        }

        graphics.drawString(this.font,
                Component.translatable(Constants.StringKeys.LENS_SCREEN_GRANTS_HEADER), DETAIL_LEFT, y, COLOR_HEADER);
        y += LINE_HEIGHT;

        final int shown = Math.min(MAX_BUFFS_SHOWN, region.buffs().size());

        for (int i = 0; i < shown; i++)
        {
            final LensRegionReport.BuffEntry buff = region.buffs().get(i);

            graphics.drawString(this.font, Component.literal(buff.buffType() + " +" + score(buff.magnitude())),
                    DETAIL_LEFT + 4, y, COLOR_TEXT);
            y += LINE_HEIGHT;
        }
    }

    private int drawMoreLine(GuiGraphics graphics, int total, int shown, int y)
    {
        if (total <= shown)
        {
            return y;
        }

        graphics.drawString(this.font,
                Component.translatable(Constants.StringKeys.LENS_SCREEN_MORE, total - shown), DETAIL_LEFT + 4, y, COLOR_MUTED);

        return y + LINE_HEIGHT;
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
