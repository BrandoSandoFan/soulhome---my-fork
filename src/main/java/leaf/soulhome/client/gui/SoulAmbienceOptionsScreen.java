/*
 * File created ~ 15 - 9 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.config.SoulHomeClientConfig;
import leaf.soulhome.constants.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The switches for the Ambience epic (#167), reachable from Mods -> SoulHome -> Config.
 *
 * <p>The knobs live in {@code config/soulhome-client.toml} and a player editing that file gets the
 * same result. This screen exists because "there is a switch" and "a player can find the switch"
 * are different claims, and the second is the one the issue asks for - somebody who finds the fog
 * uncomfortable should not have to be told there is a toml file.
 *
 * <p>Two columns. The left is the soul's own ambience: six controls, and the first of them turns off
 * the other five. {@code Intensity} reaches zero through the same slider it is set by, which is
 * deliberately the one control that does everything: a player sensitive to motion or contrast has one
 * thing to find rather than four.
 *
 * <p>The right is suppression (#188): the screen warp around a player who has ascended, and its
 * sound. Both were in the toml from the start, and the warp is the one control in the mod that
 * exists because of motion sickness - which made it the one most in need of a screen, and it had
 * none. It sits here rather than on a screen of its own because this is where a player who has
 * already turned the fog off will look for the next thing that bothers them. Columns rather than
 * two more rows, because two more rows push the last of them under the footer at the smallest window
 * the game allows.
 */
@OnlyIn(Dist.CLIENT)
public class SoulAmbienceOptionsScreen extends Screen
{
    private static final int ROW_HEIGHT = 24;
    private static final int WIDTH = 150;
    private static final int GUTTER = 10;
    private static final int HEADING_Y = 34;
    private static final int FIRST_ROW_Y = 46;

    private final Screen parent;

    public SoulAmbienceOptionsScreen(Screen parent)
    {
        super(Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        final SoulHomeClientConfig.Client config = SoulHomeClientConfig.CLIENT;
        final int left = leftColumn();
        final int right = rightColumn();

        int y = FIRST_ROW_Y;

        addToggle(left, y, Constants.StringKeys.AMBIENCE_SCREEN_ENABLED, config.ambienceEnabled);
        y += ROW_HEIGHT;

        addToggle(left, y, Constants.StringKeys.AMBIENCE_SCREEN_RANK, config.rankVisuals);
        y += ROW_HEIGHT;

        addToggle(left, y, Constants.StringKeys.AMBIENCE_SCREEN_CHARACTER, config.characterColour);
        y += ROW_HEIGHT;

        addToggle(left, y, Constants.StringKeys.AMBIENCE_SCREEN_SOUND, config.ambientSound);
        y += ROW_HEIGHT;

        addSlider(left, y, Constants.StringKeys.AMBIENCE_SCREEN_INTENSITY, config.intensity);
        y += ROW_HEIGHT;

        addSlider(left, y, Constants.StringKeys.AMBIENCE_SCREEN_VOLUME, config.soundVolume);

        y = FIRST_ROW_Y;

        addToggle(right, y, Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_DISTORTION,
                Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_DISTORTION_TIP, config.suppressionDistortion);
        y += ROW_HEIGHT;

        addToggle(right, y, Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_AUDIO,
                Constants.StringKeys.AMBIENCE_SCREEN_SUPPRESSION_AUDIO_TIP, config.suppressionAudio);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    private int leftColumn()
    {
        return this.width / 2 - WIDTH - GUTTER / 2;
    }

    private int rightColumn()
    {
        return this.width / 2 + GUTTER / 2;
    }

    private void addToggle(int x, int y, String key, ForgeConfigSpec.BooleanValue value)
    {
        addRenderableWidget(CycleButton.onOffBuilder(value.get())
                .create(x, y, WIDTH, 20, Component.translatable(key), (button, set) ->
                {
                    write(value, set);
                    flush();
                }));
    }

    /**
     * A toggle whose name alone does not say enough. "Screen warp" is two words; what a player
     * turning it off needs to know is that they lose nothing they would need, and that is a sentence.
     */
    private void addToggle(int x, int y, String key, String tooltipKey, ForgeConfigSpec.BooleanValue value)
    {
        final Tooltip tooltip = Tooltip.create(Component.translatable(tooltipKey));

        addRenderableWidget(CycleButton.onOffBuilder(value.get())
                .withTooltip(set -> tooltip)
                .create(x, y, WIDTH, 20, Component.translatable(key), (button, set) ->
                {
                    write(value, set);
                    flush();
                }));
    }

    private void addSlider(int x, int y, String key, ForgeConfigSpec.DoubleValue value)
    {
        addRenderableWidget(new PercentSlider(x, y, key, value));
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
    private static <T> void write(ForgeConfigSpec.ConfigValue<T> value, T set)
    {
        value.set(set);
    }

    private static void flush()
    {
        SoulHomeClientConfig.SPEC.save();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xE0E0FF);

        graphics.drawCenteredString(
                this.font, Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_SECTION_SOUL),
                leftColumn() + WIDTH / 2, HEADING_Y, 0xC0C0C0);
        graphics.drawCenteredString(
                this.font, Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_SECTION_SUPPRESSION),
                rightColumn() + WIDTH / 2, HEADING_Y, 0xC0C0C0);

        // said on the screen itself, because "cosmetic" is the one thing a player needs to know
        // before deciding, and a config comment is not where they will be looking
        graphics.drawCenteredString(
                this.font, Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_COSMETIC),
                this.width / 2, this.height - 46, 0xA0A0A0);
    }

    @Override
    public void onClose()
    {
        flush();

        this.minecraft.setScreen(this.parent);
    }

    /** A 0-100% slider over one of the config's doubles, showing "Off" rather than "0%" at zero. */
    private static final class PercentSlider extends AbstractSliderButton
    {
        private final String key;
        private final ForgeConfigSpec.DoubleValue config;

        PercentSlider(int x, int y, String key, ForgeConfigSpec.DoubleValue config)
        {
            super(x, y, WIDTH, 20, Component.empty(), config.get());
            this.key = key;
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            final Component amount = this.value <= 0d
                    ? Component.translatable(Constants.StringKeys.AMBIENCE_SCREEN_OFF)
                    : Component.literal(Math.round(this.value * 100d) + "%");

            setMessage(Component.translatable(this.key, amount));
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
