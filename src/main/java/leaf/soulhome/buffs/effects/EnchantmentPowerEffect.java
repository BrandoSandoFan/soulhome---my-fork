/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Enchanting room: everything you enchant comes out stronger.
 *
 * <p>Raises the effective level offered in each slot at an enchanting table, which is the number
 * vanilla derives from surrounding bookshelves - so this buff is, in effect, a way to push past
 * the fifteen-bookshelf ceiling by building a room that deserves it.
 *
 * <h2>Two caveats worth knowing</h2>
 *
 * {@code EnchantmentLevelSetEvent} carries a level and a position but no player, so the player is
 * found by proximity to the table. In singleplayer and in any normal multiplayer enchanting that
 * is the person standing at it; two players shoulder to shoulder at one table is a case this
 * cannot distinguish, and the nearer one wins.
 *
 * <p>Scope is the enchanting table only, not anvils or grindstones. The vanilla bookshelf cap is
 * the thing this buff is meant to push past, and keeping it there keeps the balance conversation
 * contained to one screen.
 */
public class EnchantmentPowerEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.ENCHANTMENT_POWER;

    /** How far from the table to look for the enchanter. */
    private static final double PLAYER_SEARCH_RADIUS = 8.0d;

    /**
     * Ceiling on the effective level handed to the enchantment logic. Vanilla tops out at 30;
     * this leaves room for the buff to matter without handing the cost display numbers nobody has
     * ever looked at.
     */
    private static final int MAX_EFFECTIVE_LEVEL = 45;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra effective enchanting levels";
    }

    @SubscribeEvent
    public void onEnchantmentLevelSet(EnchantmentLevelSetEvent event)
    {
        // getLevel() is typed as a LevelAccessor in some Forge builds; the instanceof both narrows
        // it and keeps this compiling either way
        if (!(event.getLevel() instanceof Level level) || level.isClientSide)
        {
            return;
        }

        final BlockPos pos = event.getPos();
        final Player player = level.getNearestPlayer(
                pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, PLAYER_SEARCH_RADIUS, false);

        if (!appliesTo(player))
        {
            return;
        }

        final int bonus = (int) Math.round(magnitudeFor(player));

        if (bonus <= 0)
        {
            return;
        }

        event.setEnchantLevel(Mth.clamp(event.getEnchantLevel() + bonus, 1, MAX_EFFECTIVE_LEVEL));
    }
}
