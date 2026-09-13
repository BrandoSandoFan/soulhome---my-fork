/*
 * File created ~ 13 - 9 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.AttunementReport;
import leaf.soulhome.feedback.BuffNames;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SetAttunementMessage;
import leaf.soulhome.network.SyncAttunementMessage;
import leaf.soulhome.structures.core.RoomPool;
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
 * The loadout screen the Soul Anchor opens (#154): every room the soul holds, which of them it is
 * carrying, and what the rest would grant.
 *
 * <p>Binding is a click on a row. It is free, instant, and has no confirmation - the same rooms are
 * available either way, so anything that made changing your mind cost something would only punish
 * experimenting.
 *
 * <p>Nothing here decides anything. A click is a request; the server answers with a whole fresh
 * report, which this screen redraws from. That is why there is no local toggling: a slot that was
 * full when the click was sent is still full when it lands, and a row that flipped optimistically
 * and then flipped back is worse than one that simply waited.
 *
 * <p>Lists wrap and scroll (#67) - a soulhome may hold sixty-four regions, and nothing on the Java
 * side bounds how long a room's name or its grants are.
 */
@OnlyIn(Dist.CLIENT)
public class SoulAnchorScreen extends Screen
{
    private static final int LEFT = 14;
    private static final int TOP = 56;
    private static final int RIGHT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 26;
    private static final int LINE_HEIGHT = 11;

    private static final int COLOR_TITLE = 0xE0E0FF;
    private static final int COLOR_SLOTS = 0xC7A6FF;
    private static final int COLOR_ATTUNED = 0x7CE38B;
    private static final int COLOR_DORMANT = 0xA0A0A0;
    private static final int COLOR_GONE = 0xFFAA00;
    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_MUTED = 0x8A8A8A;
    private static final int COLOR_BAR_BACK = 0xFF2A2A2E;
    private static final int COLOR_BAR_FILL = 0xFF55AAFF;

    private final ScrollableDetailPanel panel = new ScrollableDetailPanel();

    /** Where each clickable room sits in content space, so a click can be turned back into a room. */
    private final List<Row> rows = new ArrayList<>();

    private AttunementReport report;
    private long seenGeneration;

    public SoulAnchorScreen(AttunementReport report)
    {
        super(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_TITLE));
        this.report = report;
        this.seenGeneration = SyncAttunementMessage.ClientAttunement.generation();
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
    public void tick()
    {
        // a report that lands while this is open is the server's answer to a click made in it, so it
        // updates the screen in place rather than opening a second one over the top
        final long generation = SyncAttunementMessage.ClientAttunement.generation();

        if (generation != this.seenGeneration)
        {
            this.seenGeneration = generation;
            this.report = SyncAttunementMessage.ClientAttunement.latest();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);

        graphics.drawString(this.font, Component.translatable(
                        Constants.StringKeys.ANCHOR_SCREEN_SLOTS,
                        this.report.passiveUsed(), this.report.passiveSlots(),
                        this.report.activeUsed(), this.report.activeSlots()),
                LEFT, 26, COLOR_SLOTS);

        graphics.drawString(this.font, Component.translatable(this.report.owner()
                        ? Constants.StringKeys.ANCHOR_SCREEN_HINT
                        : Constants.StringKeys.ANCHOR_SCREEN_VISITOR),
                LEFT, 38, COLOR_MUTED);

        if (this.report.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NO_ROOMS),
                    LEFT, TOP, COLOR_MUTED);
            return;
        }

        final int right = this.width - RIGHT_MARGIN;
        final int maxWidth = Math.max(20, right - LEFT);
        final int bottom = this.height - BOTTOM_MARGIN;

        this.panel.render(graphics, this.font, buildLines(maxWidth), LEFT, TOP, right, bottom,
                0, COLOR_BAR_BACK, COLOR_BAR_FILL);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        final int bottom = this.height - BOTTOM_MARGIN;

        if (button == 0 && this.report.owner() && mouseX >= LEFT && mouseX <= this.width - RIGHT_MARGIN
                && mouseY >= TOP && mouseY <= bottom)
        {
            final int contentY = (int) (mouseY - TOP) + this.panel.scrollOffset();

            for (Row row : this.rows)
            {
                if (contentY >= row.top() && contentY < row.top() + row.height())
                {
                    Network.sendToServer(new SetAttunementMessage(row.roomId(), !row.attuned()));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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

    /**
     * Attuned rooms first, then the rest. The list a player scans for "what am I carrying" is the
     * short one, and burying it inside thirty rooms in scan order makes the screen answer the
     * question it was opened for last.
     */
    private List<ScrollableDetailPanel.VisualLine> buildLines(int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> out = new ArrayList<>();

        this.rows.clear();

        int contentY = 0;

        contentY = addSection(out, contentY, maxWidth, Constants.StringKeys.ANCHOR_SCREEN_ATTUNED, true);
        addSection(out, contentY, maxWidth, Constants.StringKeys.ANCHOR_SCREEN_DORMANT, false);

        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
        out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NOT_LOST), 0, COLOR_MUTED, maxWidth));

        return out;
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
                wrap(Component.translatable(headerKey), 0, COLOR_SLOTS, maxWidth);

        out.addAll(header);
        contentY += heightOf(header);

        for (AttunementReport.Room room : matching)
        {
            List<ScrollableDetailPanel.VisualLine> lines = roomLines(room, maxWidth);
            final int height = heightOf(lines);

            // a ghost cannot be bound again from here - the room is not standing anywhere - but it
            // can be let go of, which is the whole reason it is on the list
            this.rows.add(new Row(room.roomId(), room.attuned(), contentY, height));

            out.addAll(lines);
            contentY += height;
        }

        out.add(ScrollableDetailPanel.VisualLine.spacer(3));

        return contentY + 3;
    }

    private List<ScrollableDetailPanel.VisualLine> roomLines(AttunementReport.Room room, int maxWidth)
    {
        final List<ScrollableDetailPanel.VisualLine> lines = new ArrayList<>();
        final int color = room.attuned() ? (room.present() ? COLOR_ATTUNED : COLOR_GONE) : COLOR_DORMANT;

        MutableComponent name = Component.literal(room.attuned() ? "[x] " : "[ ] ")
                .append(room.hasAspect()
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

        lines.addAll(wrap(name, 0, color, maxWidth));

        if (!room.present())
        {
            lines.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_GONE), 8, COLOR_GONE, maxWidth));
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
                    8, COLOR_TEXT, maxWidth));
        }

        return lines;
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
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, LINE_HEIGHT);
    }

    /** One clickable room, in content space - see {@link ScrollableDetailPanel#scrollOffset}. */
    private record Row(int roomId, boolean attuned, int top, int height)
    {
    }
}
