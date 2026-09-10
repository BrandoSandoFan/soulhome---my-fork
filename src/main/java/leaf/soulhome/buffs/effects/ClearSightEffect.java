/*
 * File created ~ 10 - 9 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;

/**
 * Observatory: eyes trained on the sky see further into the dark, in proportion to how well
 * trained they are.
 *
 * <p>Nothing server-side acts on this one. A discrete potion effect is either on or off, which is
 * the wrong shape for a buff whose whole point is that it should brighten smoothly as the room
 * improves rather than snap from nothing to full strength at some threshold - so this is applied
 * as a genuine post-processing shader instead, driven every frame by {@code ClearSightRenderer}
 * off the magnitude the server already syncs to the client for display. This class exists to
 * register the type, describe it and be found by {@code SoulBuffEffects.get} - the same shape
 * {@code FortuneEffect} takes for the same reason: the actual work happens somewhere a
 * {@code @SubscribeEvent} on this class could not reach.
 */
public class ClearSightEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.CLEAR_SIGHT;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "how strong the shader's brightening runs, as a fraction of its own ceiling";
    }

    @Override
    public void register()
    {
        // no bus event to subscribe to - see ClearSightRenderer, client-side
    }
}
