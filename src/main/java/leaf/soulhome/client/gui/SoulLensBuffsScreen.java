/*
 * File created ~ 30 - 8 - 2026
 */

package leaf.soulhome.client.gui;

import leaf.soulhome.constants.Constants;
import leaf.soulhome.feedback.LensBuffReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Locale;

/**
 * What the Soul Lens shows when used outside a soul (#50, deliverable D) - the question a player
 * out in the world actually has: is any of this doing anything, and which room earned it.
 *
 * <p>Reads straight off {@link LensBuffReport}, the network shape of the same
 * {@code BuffBreakdown} the chat command and the buff registry itself are built from.
 */
@OnlyIn(Dist.CLIENT)
public class SoulLensBuffsScreen extends Screen
{
    private static final int LEFT = 14;
    private static final int TOP = 30;
    private static final int LINE_HEIGHT = 11;

    private static final int COLOR_TITLE = 0xE0E0FF;
    private static final int COLOR_HEADER = 0xC7A6FF;
    private static final int COLOR_TEXT = 0xE0E0E0;
    private static final int COLOR_MUTED = 0xA0A0A0;
    private static final int COLOR_CAPPED = 0xFFAA00;

    private final List<LensBuffReport> buffs;

    public SoulLensBuffsScreen(List<LensBuffReport> buffs)
    {
        super(Component.translatable(Constants.StringKeys.LENS_SCREEN_BUFFS_TITLE));
        this.buffs = buffs;
    }

    @Override
    protected void init()
    {
        this.addRenderableWidget(Button.builder(
                        Component.translatable(Constants.StringKeys.LENS_SCREEN_CLOSE), button -> this.onClose())
                .bounds(this.width - 90, this.height - 26, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);

        if (this.buffs.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable(Constants.StringKeys.BUFFS_NONE), LEFT, TOP, COLOR_MUTED);
            return;
        }

        int y = TOP;

        for (LensBuffReport buff : this.buffs)
        {
            String line = buff.buffType() + " +" + score(buff.magnitude());

            if (buff.capped())
            {
                line += " *";
            }

            graphics.drawString(this.font, Component.literal(line), LEFT, y,
                    buff.capped() ? COLOR_CAPPED : COLOR_HEADER);
            y += LINE_HEIGHT;

            for (LensBuffReport.Source source : buff.sources())
            {
                graphics.drawString(this.font, Component.translatable(
                                Constants.StringKeys.LENS_SCREEN_BUFFS_FROM,
                                Component.translatable(source.displayName()), source.rooms(), source.bestTier()),
                        LEFT + 6, y, COLOR_TEXT);
                y += LINE_HEIGHT;
            }

            y += 3;
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private static String score(double value)
    {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
