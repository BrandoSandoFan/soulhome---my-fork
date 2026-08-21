/*
 * File created ~ 19 - 8 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.structures.core.SoulBuffTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Hearth: whatever you hit with catches alight, the way the Fire Aspect enchantment does.
 *
 * <p>Unlike {@link SwordDamageEffect}, this is not weapon-conditional: a hearth is about carrying
 * fire with you, not about a specific blade, so any direct melee hit lights the target regardless
 * of what is in the player's hand - a sword, an axe, or a bare fist.
 *
 * <p>Still scoped to a direct hit. {@code LivingHurtEvent#getSource().getDirectEntity()} is the
 * player only for melee; an arrow or a thrown trident is its own entity, so this never fires for
 * something that merely left the player at some point - the same distinction
 * {@link SwordDamageEffect} draws, just without the weapon check on top of it.
 *
 * <p>{@code LivingEntity#setSecondsOnFire} already keeps the larger of the target's current burn
 * time and the new one, so a target already alight for longer than this hit would grant is left
 * alone rather than having its fire cut short.
 */
public class FireAspectEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.FIRE_ASPECT;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "seconds the target is set on fire";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getSource().getDirectEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final int seconds = (int) Math.round(magnitudeFor(player));

        if (seconds <= 0)
        {
            return;
        }

        event.getEntity().setSecondsOnFire(seconds);
    }
}
