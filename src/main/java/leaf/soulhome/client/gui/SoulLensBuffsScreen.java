/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.AttunementReport;
import leaf.soulhome.feedback.BuffNames;
import leaf.soulhome.feedback.LensBuffReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
    /** Clears the title, so the plate's border does not sit on its descenders. */
    private static final int PANEL_TOP = SoulGuiStyle.TITLE_Y + SoulGuiStyle.LINE_HEIGHT + 5;

    /** Between one buff and the next. */
    private static final int ENTRY_GAP = 3;
    /** Between the buffs and the attunement block, which is a different question and is headed as one. */
    private static final int SECTION_GAP = 6;

    private static final int INDENT_ROOT = 0;
    private static final int INDENT_DETAIL = 6;
    private static final int INDENT_NESTED = 12;

    private final List<LensBuffReport> buffs;
    private final AttunementReport attunement;
    private final ScrollableDetailPanel panel = new ScrollableDetailPanel();

    public SoulLensBuffsScreen(List<LensBuffReport> buffs)
    {
        this(buffs, AttunementReport.EMPTY);
    }

    public SoulLensBuffsScreen(List<LensBuffReport> buffs, AttunementReport attunement)
    {
        super(Component.translatable(Constants.StringKeys.LENS_SCREEN_BUFFS_TITLE));
        this.buffs = buffs;
        this.attunement = attunement;
    }

    @Override
    protected void init()
    {
        final Component label = Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE);
        final int closeWidth = SoulGuiStyle.buttonWidth(this.font, label);

        this.addRenderableWidget(Button.builder(label, button -> this.onClose())
                .bounds(this.width - SoulGuiStyle.MARGIN - closeWidth, SoulGuiStyle.footerTop(this.height),
                        closeWidth, SoulGuiStyle.BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, SoulGuiStyle.TITLE_Y, SoulGuiStyle.TITLE);

        final Plate plate = plate();
        final Plate inner = plate.inset();

        SoulGuiStyle.drawPanel(graphics, plate.left(), plate.top(), plate.right(), plate.bottom());

        if (this.buffs.isEmpty() && this.attunement.isEmpty())
        {
            drawNone(graphics, inner);
            return;
        }

        this.panel.render(graphics, this.font, buildLines(ScrollableDetailPanel.textWidth(inner.left(), inner.right())),
                inner.left(), inner.top(), inner.right(), inner.bottom(),
                0, SoulGuiStyle.BAR_BACK, SoulGuiStyle.BAR_FILL, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        final Plate plate = plate();

        if (this.panel.scroll(mouseX, mouseY, delta, plate.left(), plate.top(), plate.right(), plate.bottom()))
        {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.panel.mouseClicked(mouseX, mouseY, button))
        {
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
    public boolean isPauseScreen()
    {
        return false;
    }

    /**
     * The plate runs from the margin to the margin, and stops a gap above the footer so the scroll area
     * never sits on the Close button.
     */
    private Plate plate()
    {
        return new Plate(SoulGuiStyle.MARGIN, PANEL_TOP, this.width - SoulGuiStyle.MARGIN,
                SoulGuiStyle.footerTop(this.height) - SoulGuiStyle.FOOTER_GAP);
    }

    /**
     * The empty state is a sentence rather than a label, so it is wrapped and centred in the plate: a
     * single unwrapped line runs off the left edge of a narrow window.
     */
    private void drawNone(GuiGraphics graphics, Plate inner)
    {
        final List<FormattedCharSequence> lines = this.font.split(
                Component.translatable(Constants.StringKeys.BUFFS_NONE),
                ScrollableDetailPanel.textWidth(inner.left(), inner.right()));

        int y = inner.top() + (inner.bottom() - inner.top() - lines.size() * SoulGuiStyle.LINE_HEIGHT) / 2;

        for (FormattedCharSequence line : lines)
        {
            final int x = (inner.left() + inner.right() - this.font.width(line)) / 2;

            graphics.drawString(this.font, line, x, y, SoulGuiStyle.MUTED);

            y += SoulGuiStyle.LINE_HEIGHT;
        }
    }

    private List<ScrollableDetailPanel.VisualLine> buildLines(int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();
        boolean anyCapped = false;

        for (LensBuffReport buff : this.buffs)
        {
            MutableComponent line = BuffNames.name(buff.buffType())
                    .append(Component.literal(" " + BuffNames.magnitude(buff.buffType(), buff.magnitude())));

            if (buff.capped())
            {
                line = line.append(Component.literal(" *"));
                anyCapped = true;
            }

            out.addAll(wrap(line, INDENT_ROOT, buff.capped() ? SoulGuiStyle.CAPPED : SoulGuiStyle.HEADER, maxWidth));

            if (buff.rankBonus() > 0d)
            {
                out.addAll(wrap(Component.translatable(
                                Constants.StringKeys.LENS_SCREEN_BUFFS_RANK_BONUS,
                                BuffNames.magnitude(buff.buffType(), buff.rankBonus())),
                        INDENT_DETAIL, SoulGuiStyle.TEXT, maxWidth));
            }

            for (LensBuffReport.Source source : buff.sources())
            {
                // the aspect beside the room that granted it (#175), so this screen and
                // /soulhome buffs say the same thing. Absent, and the line is what it always was
                out.addAll(wrap(source.hasAspect()
                                ? Component.translatable(
                                        Constants.StringKeys.LENS_SCREEN_BUFFS_FROM_ASPECT,
                                        Component.translatable(source.displayName()),
                                        Component.translatable(source.aspectName()),
                                        source.rooms(), source.bestTier())
                                : Component.translatable(
                                        Constants.StringKeys.LENS_SCREEN_BUFFS_FROM,
                                        Component.translatable(source.displayName()), source.rooms(), source.bestTier()),
                        INDENT_DETAIL, SoulGuiStyle.TEXT, maxWidth));
            }

            out.add(ScrollableDetailPanel.VisualLine.spacer(ENTRY_GAP));
        }

        // The star means nothing on its own, and a capped buff reads as a fault unless someone says it is the ceiling
        if (anyCapped)
        {
            out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_BUFFS_CAPPED_LEGEND),
                    INDENT_ROOT, SoulGuiStyle.MUTED, maxWidth));
        }

        final List<ScrollableDetailPanel.VisualLine> attunementLines = attunementLines(maxWidth);

        if (!attunementLines.isEmpty())
        {
            if (!out.isEmpty())
            {
                out.add(ScrollableDetailPanel.VisualLine.spacer(SECTION_GAP));
            }

            out.addAll(attunementLines);
        }

        return out;
    }

    /**
     * What this soul is carrying its buffs instead of (#153). Read-only: the lens is where a player
     * out in the world realises they would rather have the aquarium attuned, and telling them costs
     * nothing - but binding stays at the anchor, because a loadout you can re-spec halfway down a
     * ravine is a different game from one you plan before setting out. Empty on a server with
     * attunement off, so this screen is then exactly the screen it always was.
     */
    private List<ScrollableDetailPanel.VisualLine> attunementLines(int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();

        if (this.attunement.isEmpty())
        {
            return out;
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.LENS_SCREEN_BUFFS_ATTUNEMENT_HEADER),
                INDENT_ROOT, SoulGuiStyle.HEADER, maxWidth));

        // the counts are a status line, not a heading, so they stay body text beneath the heading
        out.addAll(wrap(Component.translatable(
                        Constants.StringKeys.ANCHOR_SCREEN_SLOTS,
                        this.attunement.passiveUsed(), this.attunement.passiveSlots(),
                        this.attunement.activeUsed(), this.attunement.activeSlots()),
                INDENT_ROOT, SoulGuiStyle.TEXT, maxWidth));

        final List<AttunementReport.Room> dormant = this.attunement.dormant();

        if (!dormant.isEmpty())
        {
            out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_DORMANT),
                    INDENT_ROOT, SoulGuiStyle.MUTED, maxWidth));

            for (AttunementReport.Room room : dormant)
            {
                out.addAll(wrap(room.hasAspect()
                                ? Component.translatable(
                                        Constants.StringKeys.ANCHOR_SCREEN_ROOM_ASPECT,
                                        Component.translatable(room.displayName()),
                                        Component.translatable(room.aspectName()),
                                        room.tier())
                                : Component.translatable(
                                        Constants.StringKeys.ANCHOR_SCREEN_ROOM,
                                        Component.translatable(room.displayName()),
                                        room.tier()),
                        INDENT_DETAIL, SoulGuiStyle.MUTED, maxWidth));

                for (AttunementReport.Grant grant : room.grants())
                {
                    out.addAll(wrap(Component.translatable(
                                    Constants.StringKeys.ANCHOR_SCREEN_WOULD_GRANT,
                                    BuffNames.name(grant.buffType()),
                                    BuffNames.magnitude(grant.buffType(), grant.magnitude())),
                            INDENT_NESTED, SoulGuiStyle.TEXT, maxWidth));
                }
            }
        }

        out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NOT_LOST),
                INDENT_ROOT, SoulGuiStyle.MUTED, maxWidth));

        return out;
    }

    private List<ScrollableDetailPanel.VisualLine> wrap(Component text, int indent, int color, int maxWidth)
    {
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, SoulGuiStyle.LINE_HEIGHT);
    }

    /** A rectangle in screen space, from its outer edge in to the text. */
    private record Plate(int left, int top, int right, int bottom)
    {
        Plate inset()
        {
            return new Plate(this.left + SoulGuiStyle.PANEL_PADDING, this.top + SoulGuiStyle.PANEL_PADDING,
                    this.right - SoulGuiStyle.PANEL_PADDING, this.bottom - SoulGuiStyle.PANEL_PADDING);
        }
    }
}
