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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The switches for the Ambience epic (#167), reachable from Mods -> SoulHome -> Config.
 *
 * <p>The knobs live in {@code config/soulhome-client.toml} and a player editing that file gets the
 * same result. This screen exists because "there is a switch" and "a player can find the switch"
 * are different claims, and the second is the one the issue asks for - somebody who finds the fog
 * uncomfortable should not have to be told there is a toml file.
 *
 * <p>Six controls, and the first of them turns off the other five. {@code Intensity} reaches zero
 * through the same slider it is set by, which is deliberately the one control that does everything:
 * a player sensitive to motion or contrast has one thing to find rather than four.
 */
@OnlyIn(Dist.CLIENT)
public class SoulAmbienceOptionsScreen extends Screen
{
    private static final int ROW_HEIGHT = 24;
    private static final int WIDTH = 200;

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
        final int left = this.width / 2 - WIDTH / 2;

        int y = 40;

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

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    private void addToggle(int x, int y, String key, ModConfigSpec.BooleanValue value)
    {
        addRenderableWidget(CycleButton.onOffBuilder(value.get())
                .create(x, y, WIDTH, 20, Component.translatable(key), (button, set) ->
                {
                    write(value, set);
                    flush();
                }));
    }

    private void addSlider(int x, int y, String key, ModConfigSpec.DoubleValue value)
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
    private static <T> void write(ModConfigSpec.ConfigValue<T> value, T set)
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
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xE0E0FF);

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
        private final ModConfigSpec.DoubleValue config;

        PercentSlider(int x, int y, String key, ModConfigSpec.DoubleValue config)
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
