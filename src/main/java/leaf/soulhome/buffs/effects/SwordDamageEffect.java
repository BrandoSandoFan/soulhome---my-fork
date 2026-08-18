/*
 * File created ~ 17 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Armoury: more damage with swords.
 *
 * <p>Deliberately <i>not</i> an {@code ATTACK_DAMAGE} attribute modifier. An attribute would buff
 * axes, tridents, and bare fists alongside swords, and "you hit harder with a sword" is a
 * weapon-conditional promise. Scaling the damage event keeps the condition where the player can
 * see it.
 *
 * <h2>Scope</h2>
 *
 * Applies only when the player is the <i>direct</i> source of the damage - a swung sword. Arrows,
 * thrown tridents and anything else that puts a projectile between the player and the target are
 * excluded, because the sword in the player's hand had nothing to do with them.
 *
 * <p>Vanilla's sweep attack does count: it is a direct player melee hit with a sword in hand, and
 * excluding it would make the buff behave differently depending on how many mobs a player is
 * standing next to, which is not a distinction anyone would predict.
 */
public class SwordDamageEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.SWORD_DAMAGE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra sword damage as a fraction of the hit";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getSource().getDirectEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final ItemStack weapon = player.getMainHandItem();

        if (!weapon.is(ItemTags.SWORDS))
        {
            return;
        }

        event.setAmount((float) (event.getAmount() * (1d + magnitudeFor(player))));
    }
}
