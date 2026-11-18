/*
 * File created ~ 18 - 11 - 2026
 */

package leaf.soulhome.buffs.effects;

import leaf.soulhome.buffs.SoulBuffEffect;
import leaf.soulhome.buffs.SoulBuffs;
import leaf.soulhome.structures.core.SoulBuffTypes;
import leaf.soulhome.structures.core.TrophyMobs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Trophy room: a head on the wall is a grudge against the thing it came from (#305).
 *
 * <p>The amount is the room's ordinary buff magnitude, so tier, ramp, falloff and rank all behave as
 * they do for every other buff; what is special is only <i>against whom</i> it applies. Which mobs a
 * head counts against is an entity-type tag per group ({@code soulhome:trophy/zombie}), so a datapack
 * can widen it, and the groups a player holds are the ones their carried trophy rooms mount - two
 * zombie heads are still just "zombies", never twice the damage.
 *
 * <p>Unlike {@link SwordDamageEffect} it applies to any hit the player is responsible for, direct or
 * through an arrow or a thrown trident, because the head says who to hurt, not with what.
 */
public class TrophyDamageEffect implements SoulBuffEffect
{
    public static final String TYPE = SoulBuffTypes.TROPHY_DAMAGE;

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public String describeMagnitude()
    {
        return "extra damage against the mobs whose heads are mounted, as a fraction of the hit";
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event)
    {
        if (!(event.getSource().getEntity() instanceof Player player) || !appliesTo(player))
        {
            return;
        }

        final EntityType<?> victim = event.getEntity().getType();

        for (String group : TrophyMobs.GROUP_BY_HEAD.values())
        {
            if (SoulBuffs.hasMobTrophy(player, group) && victim.is(tagFor(group)))
            {
                event.setAmount((float) (event.getAmount() * (1d + magnitudeFor(player))));
                // one head, one bonus: a mob that is in two groups is not hit twice as hard
                return;
            }
        }
    }

    public static TagKey<EntityType<?>> tagFor(String group)
    {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("soulhome", "trophy/" + group));
    }
}
