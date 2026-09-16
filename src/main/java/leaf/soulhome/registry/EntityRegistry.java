/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.entity.SoulBarrageShotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entities this mod owns outright - just Barrage's own shell (#194) today.
 */
public class EntityRegistry
{
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SoulHome.MODID);

    /** A small, fast shell - see {@link SoulBarrageShotEntity}. Never summonable or saveable: it lives for a few ticks. */
    public static final DeferredHolder<EntityType<?>, EntityType<SoulBarrageShotEntity>> SOUL_BARRAGE_SHOT = ENTITIES.register(
            "soul_barrage_shot",
            () -> EntityType.Builder.<SoulBarrageShotEntity>of(SoulBarrageShotEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .noSave()
                    .noSummon()
                    .build("soul_barrage_shot"));

    private EntityRegistry()
    {
    }
}
