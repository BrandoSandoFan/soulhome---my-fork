/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.constants.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The switches for the Ambience epic (#167), reachable from Mods -> SoulHome -> Config.
 *
 * <p>The knobs live in {@code config/soulhome-client.toml} and a player editing that file gets the
 * same result. This screen exists because "there is a switch" and "a player can find the switch"
 * are different claims, and the second is the one the issue asks for - somebody who finds the fog
 * uncomfortable should not have to be told there is a toml file.
 *
 * <p>Two columns when the window is wide enough for them, one stacked column when it is not. The left
 * is the soul's own ambience: six controls, and the first of them, {@code Soul ambience}, switches off
 * everything that depends on it - the other five, and the soul's music in the right-hand column. Those
 * are greyed out while it is off, so a player can see why they do nothing. {@code Intensity} reaches
 * zero through the same slider it is set by, which is deliberately the one control that does everything:
 * a player sensitive to motion or contrast has one thing to find rather than four.
 *
 * <p>The right is suppression (#188): the screen warp around a player who has ascended, and its
 * sound. Both were in the toml from the start, and the warp is the one control in the mod that
 * exists because of motion sickness - which made it the one most in need of a screen, and it had
 * none. It sits here rather than on a screen of its own because this is where a player who has
 * already turned the fog off will look for the next thing that bothers them. Suppression is not
 * greyed by the master switch: it reads its own config, and turning the soul's ambience off does not
 * turn off how other players are shown to you. The soul's own music (#163) sits under it, under a
 * heading of its own. Columns rather than two more rows, because two more rows push the last of them
 * under the footer at the smallest window the game allows.
 */
@OnlyIn(Dist.CLIENT)
public class SoulAmbienceOptionsScreen extends Screen
{
    /** The vanilla formats a slider is written in, so a translation that already has them is used as it is. */
    private static final String PERCENT_VALUE = "options.percent_value";
    private static final String GENERIC_VALUE = "options.generic_value";

    /** A control is never wider than this; a narrower window gives each column less. */
    private static final int WIDTH = 150;
    private static final int GUTTER = 10;
    /** Below this a column is too narrow to read comfortably, and the rows stack in one column instead. */
    private static final int MIN_COLUMN_WIDTH = 120;

    /** Clears the title. */
    private static final int ROWS_TOP = SoulGuiStyle.TITLE_Y + SoulGuiStyle.LINE_HEIGHT + 6;
    /** A heading's own line, and the gap that keeps its first row off the text. */
    private static final int HEADING_HEIGHT = SoulGuiStyle.LINE_HEIGHT + 3;
    private static final int FOOTER_SPACING = 8;

    /**
     * Tried in order until the stack clears the footer. A control is always {@link SoulGuiStyle#BUTTON_HEIGHT}
     * tall, so the step is the space between rows. The group gap is what is given up after it.
     */
    private static final Spacing[] SPACINGS = {
            new Spacing(24, 8),
            new Spacing(22, 8),
            new Spacing(20, 8),
            new Spacing(20, 4)
    };

    private final Screen parent;

    /** The controls that do nothing while the soul is switched off. Rebuilt with the layout each time. */
    private final List<AbstractWidget> gated = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();

    private List<FormattedCharSequence> note = List.of();
    private int noteTop;

    public SoulAmbienceOptionsScreen(Screen parent)
    {
        super(Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        // Each column takes what the window leaves for it, up to WIDTH. Vanilla scrolls a label too long for its
        // button rather than cutting it off, so a narrower button still reads; a fixed width is what left the
        // Music toggle under the footer at 320 px.
        final int twoColumnWidth = (this.width - 2 * SoulGuiStyle.MARGIN - GUTTER) / 2;
        final boolean twoColumns = twoColumnWidth >= MIN_COLUMN_WIDTH;
        final int columnWidth = Math.min(WIDTH, twoColumns ? twoColumnWidth : this.width - 2 * SoulGuiStyle.MARGIN);
        final int limit = SoulGuiStyle.footerTop(this.height) - SoulGuiStyle.FOOTER_GAP;

        int bottom = 0;

        // The tightest spacing is kept even when it still reaches the footer, because there is no smaller layout
        // to fall back to. A control drawn under the footer cannot be clicked, so this is a last resort for a
        // window shorter than the stack needs, not a layout to aim for.
        for (Spacing spacing : SPACINGS)
        {
            this.clearWidgets();
            bottom = stack(twoColumns, columnWidth, spacing);

            if (bottom <= limit)
            {
                break;
            }
        }

        // The note is dropped rather than drawn over a control when the stack leaves it no room. A missing
        // line costs less than a line a player cannot read.
        final List<FormattedCharSequence> lines = this.font.split(
                Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_COSMETIC), this.width - 2 * SoulGuiStyle.MARGIN);

        this.noteTop = limit - lines.size() * SoulGuiStyle.LINE_HEIGHT;
        this.note = bottom <= this.noteTop ? lines : List.of();

        final int footerY = SoulGuiStyle.footerTop(this.height);

        final Component done = CommonComponents.GUI_DONE;
        final int doneWidth = SoulGuiStyle.buttonWidth(this.font, done);

        this.addRenderableWidget(Button.builder(done, button -> onClose())
                .bounds(this.width - SoulGuiStyle.MARGIN - doneWidth, footerY, doneWidth, SoulGuiStyle.BUTTON_HEIGHT)
                .build());

        final Component reset = Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_RESET);
        final int resetWidth = Math.min(SoulGuiStyle.buttonWidth(this.font, reset),
                this.width - 2 * SoulGuiStyle.MARGIN - doneWidth - FOOTER_SPACING);

        this.addRenderableWidget(Button.builder(
                        SoulGuiStyle.truncate(this.font, reset, resetWidth - SoulGuiStyle.BUTTON_PAD),
                        button -> resetToDefaults())
                .bounds(SoulGuiStyle.MARGIN, footerY, resetWidth, SoulGuiStyle.BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_RESET_TIP)))
                .build());
    }

    /**
     * Lays out every heading and control at one spacing, and returns the lowest pixel a control reaches.
     * Two columns put the soul's ambience on the left and suppression with music on the right. One column
     * stacks the same three groups in that order, on one cursor.
     */
    private int stack(boolean twoColumns, int columnWidth, Spacing spacing)
    {
        this.gated.clear();
        this.headings.clear();

        final SoulHomeClientConfig.Client config = SoulHomeClientConfig.CLIENT;
        final int leftX = twoColumns ? this.width / 2 - GUTTER / 2 - columnWidth : (this.width - columnWidth) / 2;
        final int rightX = twoColumns ? this.width / 2 + GUTTER / 2 : leftX;

        final Column left = new Column(leftX, columnWidth, spacing);
        final Column right = twoColumns ? new Column(rightX, columnWidth, spacing) : left;

        heading(left, Constants.StringKeys.AMBIENCE_SCREEN_SECTION_SOUL);

        // The master is not gated itself. Every control below it tests AmbienceSettings#active first, which
        // is false while the master is off, so each one is a dial for nothing until it is turned back on.
        addToggle(left.x, left.row(), left.width, Constants.StringKeys.AMBIENCE_SCREEN_ENABLED,
                Constants.StringKeys.AMBIENCE_SCREEN_ENABLED_TIP, config.ambienceEnabled);
        this.gated.add(addToggle(left.x, left.row(), left.width, Constants.StringKeys.AMBIENCE_SCREEN_RANK,
                Constants.StringKeys.AMBIENCE_SCREEN_RANK_TIP, config.rankVisuals));
        this.gated.add(addToggle(left.x, left.row(), left.width, Constants.StringKeys.AMBIENCE_SCREEN_CHARACTER,
                Constants.StringKeys.AMBIENCE_SCREEN_CHARACTER_TIP, config.characterColour));
        this.gated.add(addToggle(left.x, left.row(), left.width, Constants.StringKeys.AMBIENCE_SCREEN_SOUND,
                Constants.StringKeys.AMBIENCE_SCREEN_SOUND_TIP, config.ambientSound));
        this.gated.add(addSlider(left.x, left.row(), left.width, Constants.StringKeys.AMBIENCE_SCREEN_INTENSITY_LABEL,
                Constants.StringKeys.AMBIENCE_SCREEN_INTENSITY_TIP, config.intensity));
        this.gated.add(addSlider(left.x, left.row(), left.width, Constants.StringKeys.AMBIENCE_SCREEN_VOLUME_LABEL,
                Constants.StringKeys.AMBIENCE_SCREEN_VOLUME_TIP, config.soundVolume));

        heading(right, Constants.StringKeys.AMBIENCE_SCREEN_SECTION_SUPPRESSION);

        addToggle(right.x, right.row(), right.width, Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_DISTORTION,
                Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_DISTORTION_TIP, config.suppressionDistortion);
        addToggle(right.x, right.row(), right.width, Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_AUDIO,
                Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_AUDIO_TIP, config.suppressionAudio);

        heading(right, Constants.StringKeys.AMBIENCE_SCREEN_SECTION_MUSIC);

        this.gated.add(addToggle(right.x, right.row(), right.width, Constants.StringKeys.AMBIENCE_SCREEN_MUSIC,
                Constants.StringKeys.AMBIENCE_SCREEN_MUSIC_TIP, config.soulMusic));

        refreshGated();

        return Math.max(left.bottom(), right.bottom());
    }

    private void heading(Column column, String key)
    {
        this.headings.add(new Heading(column.x + column.width / 2, column.heading(), Component.translatable(key)));
    }

    /**
     * Written through on the spot rather than on closing.
     *
     * <p>Ambience answers within a few seconds of any change, so a player fiddling with the
     * intensity slider is watching the thing they are setting - which is the only sane way to set
     * it, and impossible if the screen banks the change until it is closed.
     *
     * <p>The file itself is flushed when a control is let go rather than on every value it passes
     * through, since a slider dragged across its range would otherwise be a few hundred writes.
     */
    private static <T> void write(ModConfigSpec.ConfigValue<T> value, T set)
    {
        value.set(set);
    }

    private static void flush()
    {
        SoulHomeClientConfig.SPEC.save();
    }

    /**
     * Greys the controls that do nothing while the soul is off. It reads the config back rather than the
     * switch just pressed, so the greying always agrees with what is saved.
     */
    private void refreshGated()
    {
        final boolean on = SoulHomeClientConfig.CLIENT.ambienceEnabled.get();

        for (AbstractWidget widget : this.gated)
        {
            widget.active = on;
        }
    }

    /**
     * Every control says what it does, not only the ones whose label is ambiguous. A player wary of a
     * setting should not have to work out its consequences from a two-word label.
     *
     * <p>The tooltip is built once and handed back for every value, since the toggle asks for it again
     * whenever it cycles.
     */
    private CycleButton<Boolean> addToggle(int x, int y, int controlWidth, String key, @Nullable String tipKey,
                                           ModConfigSpec.BooleanValue value)
    {
        CycleButton.Builder<Boolean> builder = CycleButton.onOffBuilder(value.get());

        if (tipKey != null)
        {
            final Tooltip tooltip = Tooltip.create(Component.translatable(tipKey));

            builder = builder.withTooltip(set -> tooltip);
        }

        return addRenderableWidget(builder.create(x, y, controlWidth, SoulGuiStyle.BUTTON_HEIGHT, Component.translatable(key),
                (button, set) ->
                {
                    write(value, set);
                    flush();
                    refreshGated();
                }));
    }

    private PercentSlider addSlider(int x, int y, int controlWidth, String labelKey, @Nullable String tipKey,
                                    ModConfigSpec.DoubleValue value)
    {
        final PercentSlider slider = addRenderableWidget(
                new PercentSlider(x, y, controlWidth, Component.translatable(labelKey), value));

        if (tipKey != null)
        {
            slider.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        }

        return slider;
    }

    /**
     * Puts back every value this screen writes, then rebuilds the controls so they show the defaults. Each
     * default is read from the spec, not repeated here, so a default changed in SoulHomeClientConfig reaches
     * this button with no second edit. {@code config_version} is bookkeeping, not a setting, and is left alone.
     */
    private void resetToDefaults()
    {
        final SoulHomeClientConfig.Client config = SoulHomeClientConfig.CLIENT;

        reset(config.ambienceEnabled);
        reset(config.rankVisuals);
        reset(config.characterColour);
        reset(config.ambientSound);
        reset(config.intensity);
        reset(config.soundVolume);
        reset(config.soulMusic);
        reset(config.suppressionDistortion);
        reset(config.suppressionAudio);

        flush();

        this.rebuildWidgets();
    }

    private static <T> void reset(ModConfigSpec.ConfigValue<T> value)
    {
        value.set(value.getDefault());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, SoulGuiStyle.TITLE_Y, SoulGuiStyle.TITLE);

        for (Heading heading : this.headings)
        {
            graphics.drawCenteredString(this.font, heading.text(), heading.centreX(), heading.y(), SoulGuiStyle.HEADER);
        }

        int y = this.noteTop;

        for (FormattedCharSequence line : this.note)
        {
            graphics.drawString(this.font, line, (this.width - this.font.width(line)) / 2, y, SoulGuiStyle.MUTED);

            y += SoulGuiStyle.LINE_HEIGHT;
        }
    }

    @Override
    public void onClose()
    {
        flush();

        this.minecraft.setScreen(this.parent);
    }

    /** One column's place on the stack: a cursor that moves down past each heading and row. */
    private static final class Column
    {
        private final int x;
        private final int width;
        private final Spacing spacing;

        private int y = ROWS_TOP;
        private int bottom;
        private boolean started;

        Column(int x, int width, Spacing spacing)
        {
            this.x = x;
            this.width = width;
            this.spacing = spacing;
        }

        /** The heading's y. A group after the first in this column is set off by the group gap. */
        int heading()
        {
            if (this.started)
            {
                this.y += this.spacing.groupGap();
            }

            this.started = true;

            final int at = this.y;

            this.y += HEADING_HEIGHT;

            return at;
        }

        int row()
        {
            final int at = this.y;

            this.y += this.spacing.step();
            this.bottom = at + SoulGuiStyle.BUTTON_HEIGHT;

            return at;
        }

        int bottom()
        {
            return this.bottom;
        }
    }

    private record Spacing(int step, int groupGap)
    {
    }

    private record Heading(int centreX, int y, Component text)
    {
    }

    /** A 0-100% slider over one of the config's doubles, showing "Off" rather than "0%" at zero. */
    private static final class PercentSlider extends AbstractSliderButton
    {
        private final Component label;
        private final ModConfigSpec.DoubleValue config;

        PercentSlider(int x, int y, int controlWidth, Component label, ModConfigSpec.DoubleValue config)
        {
            super(x, y, controlWidth, SoulGuiStyle.BUTTON_HEIGHT, Component.empty(), config.get());
            this.label = label;
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            // "Off" goes through the generic format, because the percent format would make it "Off%".
            setMessage(this.value <= 0d
                    ? Component.translatable(GENERIC_VALUE, this.label,
                            Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_OFF))
                    : Component.translatable(PERCENT_VALUE, this.label, Math.round(this.value * 100d)));
        }

        @Override
        protected void applyValue()
        {
            write(this.config, this.value);
        }

        @Override
        public void onRelease(double mouseX, double mouseY)
        {
            super.onRelease(mouseX, mouseY);

            flush();
        }
    }
}
