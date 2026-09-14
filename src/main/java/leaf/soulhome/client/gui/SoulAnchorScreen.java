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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
    private static final int LEFT = 14;
    private static final int TOP_WITH_LOADOUT = 56;
    private static final int TOP_BARE = 30;
    private static final int RIGHT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 26;
    private static final int LINE_HEIGHT = 11;
    private static final int INDENT = 8;

    private static final int COLOR_TITLE = 0xE0E0FF;
    private static final int COLOR_SLOTS = 0xC7A6FF;
    private static final int COLOR_ATTUNED = 0x7CE38B;
    private static final int COLOR_DORMANT = 0xA0A0A0;
    private static final int COLOR_GONE = 0xFFAA00;
    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_MUTED = 0x8A8A8A;
    private static final int COLOR_MET = 0x7CE38B;
    private static final int COLOR_MISSING = 0xFF6B6B;
    private static final int COLOR_WAITING = 0xFFD24A;
    private static final int COLOR_RESIDUE = 0x69D7E0;
    private static final int COLOR_BAR_BACK = 0xFF2A2A2E;
    private static final int COLOR_BAR_FILL = 0xFF55AAFF;

    private final ScrollableDetailPanel panel = new ScrollableDetailPanel();

    /** Where each clickable room sits in content space, so a click can be turned back into a room. */
    private final List<Row> rows = new ArrayList<>();

    private AscensionReport ascension;
    private AttunementReport report;
    private Button residueButton;
    private long seenGeneration;

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
        this.addRenderableWidget(Button.builder(
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE), button -> this.onClose())
                .bounds(this.width - 90, this.height - BOTTOM_MARGIN, 80, 20)
                .build());

        // built for the owner whatever the residue currently is, and hidden rather than rebuilt when
        // there is nothing to take: a report landing while this is open changes the number on it, and
        // a widget that had to be created at that moment would be a widget that sometimes was not
        this.residueButton = this.addRenderableWidget(Button.builder(
                        Component.empty(), button -> Network.sendToServer(new CollectResidueMessage()))
                .bounds(LEFT, this.height - BOTTOM_MARGIN, 120, 20)
                .build());

        updateResidueButton();
    }

    @Override
    public void tick()
    {
        // a report that lands while this is open is the server's answer to a click made in it, so it
        // updates the screen in place rather than opening a second one over the top
        final long generation = SyncSoulAnchorMessage.ClientAnchor.generation();

        if (generation != this.seenGeneration)
        {
            this.seenGeneration = generation;

            final SyncSoulAnchorMessage.Anchor anchor = SyncSoulAnchorMessage.ClientAnchor.latest();
            this.ascension = anchor.ascension();
            this.report = anchor.attunement();

            updateResidueButton();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);

        if (loadoutShown())
        {
            graphics.drawString(this.font, Component.translatable(
                            Constants.StringKeys.ANCHOR_SCREEN_SLOTS,
                            this.report.passiveUsed(), this.report.passiveSlots(),
                            this.report.activeUsed(), this.report.activeSlots()),
                    LEFT, 26, COLOR_SLOTS);

            graphics.drawString(this.font, Component.translatable(this.ascension.owner()
                            ? Constants.StringKeys.ANCHOR_SCREEN_HINT
                            : Constants.StringKeys.ANCHOR_SCREEN_VISITOR),
                    LEFT, 38, COLOR_MUTED);
        }

        final int right = this.width - RIGHT_MARGIN;
        final int maxWidth = Math.max(20, right - LEFT);
        final int top = panelTop();

        this.panel.render(graphics, this.font, buildLines(maxWidth), LEFT, top, right, panelBottom(),
                0, COLOR_BAR_BACK, COLOR_BAR_FILL);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        final int top = panelTop();

        if (button == 0 && this.report.owner() && mouseX >= LEFT && mouseX <= this.width - RIGHT_MARGIN
                && mouseY >= top && mouseY <= panelBottom())
        {
            final int contentY = (int) (mouseY - top) + this.panel.scrollOffset();

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
        if (this.panel.scroll(mouseX, mouseY, delta, LEFT, panelTop(), panelBottom()))
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

    private int panelTop()
    {
        return loadoutShown() ? TOP_WITH_LOADOUT : TOP_BARE;
    }

    private int panelBottom()
    {
        return this.height - BOTTOM_MARGIN;
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
        this.residueButton.setMessage(residue.essence() > 0
                ? Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_COLLECT, residue.essence())
                : Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_COLLECT_NONE));
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
            out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NO_ROOMS), 0, COLOR_MUTED, maxWidth));
            return out;
        }

        contentY = addSection(out, contentY, maxWidth, Constants.StringKeys.ANCHOR_SCREEN_ATTUNED, true);
        addSection(out, contentY, maxWidth, Constants.StringKeys.ANCHOR_SCREEN_DORMANT, false);

        out.add(ScrollableDetailPanel.VisualLine.spacer(4));
        out.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_NOT_LOST), 0, COLOR_MUTED, maxWidth));

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

        int height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_ASCENSION), 0, COLOR_SLOTS, maxWidth);

        height += add(out, Component.translatable(
                Constants.StringKeys.ANCHOR_RANK, SoulBounds.rankLabel(climb.rank())), INDENT, COLOR_TEXT, maxWidth);

        if (!climb.enabled())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ASCENT_DISABLED), INDENT, COLOR_MUTED, maxWidth);
        }
        else if (climb.maxed())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_MAXED), INDENT, COLOR_MUTED, maxWidth);
        }
        else if (climb.ritualElsewhere())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_RITUAL_IN_PROGRESS), INDENT, COLOR_WAITING, maxWidth);
        }
        else
        {
            height += addRequirements(out, climb, maxWidth);
        }

        height += addResidue(out, climb.residue(), maxWidth);

        out.add(ScrollableDetailPanel.VisualLine.spacer(5));

        return height + 5;
    }

    private int addRequirements(List<ScrollableDetailPanel.VisualLine> out, AscensionReport climb, int maxWidth)
    {
        int height;

        if (climb.pillarValid())
        {
            height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_OK), INDENT, COLOR_MET, maxWidth);
        }
        else if (!climb.pillarHasBase())
        {
            height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_NO_BASE), INDENT, COLOR_MISSING, maxWidth);
        }
        else
        {
            height = add(out, Component.translatable(Constants.StringKeys.ANCHOR_PILLAR_GAP, climb.pillarGap()),
                    INDENT, COLOR_MISSING, maxWidth);
        }

        height += add(out, Component.translatable(
                        climb.willpowerMet()
                                ? Constants.StringKeys.ANCHOR_WILLPOWER_OK
                                : Constants.StringKeys.ANCHOR_WILLPOWER_MISSING,
                        climb.willpowerHave(), climb.willpowerRequired()),
                INDENT, climb.willpowerMet() ? COLOR_MET : COLOR_MISSING, maxWidth);

        height += add(out, Component.translatable(
                        climb.essenceMet()
                                ? Constants.StringKeys.ANCHOR_ESSENCE_OK
                                : Constants.StringKeys.ANCHOR_ESSENCE_MISSING,
                        climb.essenceHave(), climb.essenceRequired(), SoulBounds.rankLabel(climb.targetRank())),
                INDENT, climb.essenceMet() ? COLOR_MET : COLOR_MISSING, maxWidth);

        if (climb.ready())
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_READY), INDENT, COLOR_MET, maxWidth);
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

        final String banked = String.format("%.1f", residue.banked());

        int height = add(out, residue.essence() > 0
                        ? Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_RESIDUE, banked, residue.essence())
                        : Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_RESIDUE_SHORT, banked),
                INDENT, COLOR_RESIDUE, maxWidth);

        if (residue.collected() > 0)
        {
            height += add(out, Component.translatable(Constants.StringKeys.ANCHOR_RESIDUE_CONVERTED, residue.collected()),
                    INDENT, COLOR_MET, maxWidth);
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
            lines.addAll(wrap(Component.translatable(Constants.StringKeys.ANCHOR_SCREEN_GONE), INDENT, COLOR_GONE, maxWidth));
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
                    INDENT, COLOR_TEXT, maxWidth));
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
        return ScrollableDetailPanel.wrap(this.font, text, indent, color, maxWidth, LINE_HEIGHT);
    }

    /** One clickable room, in content space - see {@link ScrollableDetailPanel#scrollOffset}. */
    private record Row(int roomId, boolean attuned, int top, int height)
    {
    }
}
