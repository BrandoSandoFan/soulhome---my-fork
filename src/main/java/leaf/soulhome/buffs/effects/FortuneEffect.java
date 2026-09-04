/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;

/**
 * Treasury: a hoard of your own draws a little more out of the ground.
 *
 * <p>A flat chance per block broken of one extra copy of whatever it dropped, rolled once against
 * the room's own magnitude rather than pushed through vanilla's fortune enchantment maths - this
 * is a soulhome buff stacking with any tool the player is holding, not a bigger fortune level on
 * it, so it has no enchantment curve of its own to reproduce.
 *
 * <p>Grows an existing stack by one where that fits under the item's max stack size, and adds a
 * fresh one-count copy otherwise, so a bonus drop is never silently lost to a stack that was
 * already full.
 *
 * <p>The roll itself lives in {@link FortuneLootModifier}, not here - Forge 1.20.1 dropped the bus
 * event that used to carry a block's generated drops, and the loot table system's replacement
 * (a Global Loot Modifier) isn't a {@code @SubscribeEvent} listener, so it can't be this class's
 * own hook the way every other buff's is. This class stays the {@link SoulBuffEffect} that
 * describes the buff to config, feedback and the rest of the buff pipeline.
 */
public class FortuneEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FORTUNE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "chance of an extra drop when breaking a block, as a fraction chance";
    }

    @Override
    public void register()
    {
        // No bus event to subscribe to - see FortuneLootModifier.
    }
}
