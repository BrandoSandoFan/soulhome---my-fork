/*
 * File created ~ 25 - 4 - 2021 ~ Leaf
 */

package leaf.soulhome.utils;

import leaf.soulhome.entity.SoulVesselEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

public class EntityHelper
{
    public static List<LivingEntity> getLivingEntitiesInRange(LivingEntity selfEntity, int range, boolean includeSelf)
    {
        AABB areaOfEffect = new AABB(selfEntity.blockPosition());
        areaOfEffect = areaOfEffect.inflate(range, range, range);

        List<LivingEntity> entitiesFound = selfEntity.level().getEntitiesOfClass(LivingEntity.class, areaOfEffect);

        if (!includeSelf)
        {
            //removes self entity if it exists in the list
            //otherwise list unchanged
            entitiesFound.remove(selfEntity);
        }

        return entitiesFound;
    }

    // a vessel (#182) is spawned exactly where its owner stands (see SoulVesselEntity#spawn), so it
    // is always inside its own owner's 2.5-block sweep the moment a key or cushion finishes - and
    // canChangeDimensions says nothing against it, since a vessel is otherwise an ordinary
    // LivingEntity. Left in, it rides along into the soul instead of staying behind, and its own
    // cross-dimension removal reads as a disturbance (VesselLifecycleService#onVesselRemoved) that
    // resyncs the owner's return position from wherever the vessel just landed - inside the soul -
    // which is how a swept-in vessel turns into a Soul Key that cannot find its way back out.
    private static final Predicate<Entity> ALLOWED_TO_TELEPORT =
            EntitySelector.NO_SPECTATORS
                    .and(EntitySelector.LIVING_ENTITY_STILL_ALIVE)
                    .and(entity -> entity.canChangeDimensions(entity.level(), entity.level()))
                    .and((entity)->!(entity instanceof Enemy))
                    .and((entity)->!(entity instanceof SoulVesselEntity));

    public static List<Entity> getEntitiesInRange(Entity entity, double range, boolean includeSelf)
    {
        AABB areaOfEffect = new AABB(entity.blockPosition());
        areaOfEffect = areaOfEffect.inflate(range, range, range);

        List<Entity> entitiesFound = entity.level().getEntitiesOfClass(Entity.class, areaOfEffect);

        // removeIf rather than remove-inside-a-for-each: the latter needs a break to dodge
        // ConcurrentModificationException, which meant only the *first* disallowed entity was
        // ever dropped and every one after it still got teleported along with the player.
        entitiesFound.removeIf(ent -> (ent == entity && !includeSelf) || !ALLOWED_TO_TELEPORT.test(ent));

        return entitiesFound;
    }


}
