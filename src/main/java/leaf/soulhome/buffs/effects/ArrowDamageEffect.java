/*
 * File created ~ 16 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Armoury, as a quiver: more damage with arrows.
 *
 * <p>The mirror of {@link SwordDamageEffect}, and for the same reason it scales the damage event
 * rather than writing an attribute: an {@code ATTACK_DAMAGE} modifier would do nothing for a
 * projectile, and "your arrows hit harder" is a promise about arrows. Only a shot loosed by the
 * player counts - the arrow's owner, not whoever it happens to land beside - so a skeleton's arrow
 * is never improved by the player's room. Tridents are {@link AbstractArrow}s too and are left
 * out on purpose: the room is a quiver, not a throwing rack.
 */
public class ArrowDamageEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.ARROW_DAMAGE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra arrow damage as a fraction of the hit";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event)
    {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
                || arrow instanceof net.minecraft.world.entity.projectile.ThrownTrident
                || !(arrow.getOwner() instanceof Player player)
                || !appliesTo(player))
        {
            return;
        }

        event.setAmount((float) (event.getAmount() * (1d + magnitudeFor(player))));
    }
}
