/*
 * File created ~ 19 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.mixin.FishingHookAccessor;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Fishery: a better catch, and a faster bite (#308).
 *
 * <p>The magnitude is a number of levels, read the way an enchantment is: {@link #luckLevels} of
 * Luck of the Sea and {@link #lureLevels} of Lure, added to whatever the rod already carries. It is
 * deliberately <i>not</i> the player's {@code generic.luck} attribute, which every loot table in the
 * game reads - a fishery that also improved chests would be a different room. Writing the levels onto
 * the bobber as it is cast keeps the buff to fishing and lets it stack with an enchanted rod.
 *
 * <p>Lure is worth a good deal more per level than Luck (each level takes five seconds off the wait),
 * so it gets two thirds of the magnitude: tier 1 is Luck I and Lure I, tier 3 is Luck III and Lure II.
 */
public class AnglingEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.ANGLING;

    /** Lure levels per level of Luck of the Sea - see the class comment. */
    static final double LURE_SHARE = 2d / 3d;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "levels of Luck of the Sea, and two thirds as many of Lure, added to every cast";
    }

    /** Whole levels of Luck of the Sea for a magnitude. */
    public static int luckLevels(double magnitude)
    {
        return magnitude <= 0d ? 0 : (int) Math.floor(magnitude + 0.5d);
    }

    /** Whole levels of Lure for a magnitude. */
    public static int lureLevels(double magnitude)
    {
        return magnitude <= 0d ? 0 : (int) Math.floor(magnitude * LURE_SHARE + 0.5d);
    }

    @SubscribeEvent
    public void onBobberCast(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof FishingHook hook)
                || !(hook.getOwner() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final double magnitude = magnitudeFor(player);
        final FishingHookAccessor accessor = (FishingHookAccessor) hook;

        accessor.setLuck(accessor.getLuck() + luckLevels(magnitude));
        accessor.setLureSpeed(accessor.getLureSpeed() + lureLevels(magnitude));
    }
}
