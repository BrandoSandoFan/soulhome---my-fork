/*
 * File created ~ 23 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulActiveEffect;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.structures.GazeService;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.server.level.ServerPlayer;

/**
 * Observatory: the character this is drawn from sees into other people's souls through a telescope,
 * and so does the room (#187). Put a player - or the body one left behind - in your crosshair, and
 * enter their soul as a spectator for a while.
 *
 * <p>This class is only the ability's face to {@code SoulAbilities}: charges, recharge and the HUD
 * come from there with no new infrastructure, and every number comes from {@code GazeSettings}. The
 * session itself - the body left standing, the spectator mode lent and returned, every way a gaze
 * can end - is {@link GazeService}'s, because it outlives any one keypress.
 *
 * <p>Firing with nothing valid in the crosshair returns false, which spends no charge;
 * {@code GazeService#begin} says why.
 */
public class SoulgazeEffect implements SoulActiveEffect
{
    public static final String TYPE = SoulBuffTypes.SOULGAZE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how far Soulgaze reaches, how long a gaze lasts, and how faintly its target feels it";
    }

    @Override
    public int chargesFor(double magnitude)
    {
        return 1;
    }

    @Override
    public int rechargeTicksFor(double magnitude)
    {
        return SoulHomeConfig.gazeSettings().rechargeTicksFor(magnitude);
    }

    @Override
    public boolean activate(ServerPlayer player, double magnitude)
    {
        return GazeService.begin(player, magnitude);
    }
}
