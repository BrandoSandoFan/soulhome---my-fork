/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.entity.SoulBarrageShotEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entities this mod owns outright - just Barrage's own shell (#194) today.
 */
public class EntityRegistry
{
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SoulHome.MODID);

    /** A small, fast shell - see {@link SoulBarrageShotEntity}. Never summonable or saveable: it lives for a few ticks. */
    public static final RegistryObject<EntityType<SoulBarrageShotEntity>> SOUL_BARRAGE_SHOT = ENTITIES.register(
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
