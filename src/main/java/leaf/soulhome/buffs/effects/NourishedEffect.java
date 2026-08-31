/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Greenhouse: hunger builds up slower.
 *
 * <p>Vanilla has no event for "exhaustion was just added" to hook, and no public setter either -
 * {@link FoodData#addExhaustion(float)} only ever adds. So this claws exhaustion back after the
 * fact instead: a few times a second, it takes a fraction of whatever exhaustion has accumulated
 * since the last check and hands it straight back with a negative {@code addExhaustion}. Sprinting,
 * jumping, mining, healing - every source of exhaustion is dampened alike, the same way
 * {@link HealingEffect} does not carve out exceptions among sources of healing.
 *
 * <p>Reconciled on a tick rather than through a mixin into {@code FoodData} itself, so a well-tended
 * garden earns this the same low-risk way every other tick-driven buff in this package does -
 * see {@link AttributeBuffEffect} for why a tick check is what the rest of this package already
 * does when there is no change event to hook instead.
 */
public class NourishedEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.NOURISHED;

    /** A few times a second is plenty for something that only ever nudges a slow-moving stat. */
    private static final int CHECK_INTERVAL_TICKS = 10;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "less hunger exhaustion built up, as a fraction of it";
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side.isClient())
        {
            return;
        }

        final Player player = event.player;

        if (!appliesTo(player) || player.tickCount % CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }

        final FoodData food = player.getFoodData();
        final float exhaustion = food.getExhaustionLevel();

        if (exhaustion <= 0f)
        {
            return;
        }

        final double reduction = Math.min(1d, magnitudeFor(player));
        food.addExhaustion((float) (-exhaustion * reduction));
    }
}
