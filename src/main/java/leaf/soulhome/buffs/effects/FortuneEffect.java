/*
 * File created ~ 31 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;

/**
 * Treasury: a hoard of your own draws a little more out of the ground.
 *
 * <p>A flat chance per block broken of +1 effective Fortune level on top of whatever the player's
 * own tool already carries - see #195, which replaced the buff's original design (a flat chance of
 * one extra copy of whatever a block happened to drop). That version duplicated any block that
 * dropped itself, ignored Silk Touch, and affected loot tables vanilla's own Fortune has no opinion
 * about; reading the magnitude as a chance of a level instead means the block's own loot table
 * decides what a Fortune level is worth, exactly as it would for a level on the tool itself.
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
        return "chance per block broken of +1 effective Fortune level, on top of the tool's own, as a fraction chance";
    }

    @Override
    public void register()
    {
        // No bus event to subscribe to - see FortuneLootModifier.
    }
}
