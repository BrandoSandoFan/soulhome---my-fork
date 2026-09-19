/*
 * File created ~ 16 - 9 - 2026
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.entity.SoulBarrageShotEntity;
import leaf.soulhome.entity.SoulVesselEntity;
import leaf.soulhome.structures.VesselLifecycleService;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entities this mod owns outright - Barrage's own shell (#194), and the Soul Vessel (#182).
 */
@EventBusSubscriber(modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.MOD)
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

    /**
     * The body a player leaves behind when they enter their soul - see {@link SoulVesselEntity}.
     * Saved and player-sized: it is meant to sit out for as long as its owner is away, including
     * across a server restart, which is the opposite of Barrage's shell above.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SoulVesselEntity>> SOUL_VESSEL = ENTITIES.register(
            "soul_vessel",
            () -> EntityType.Builder.<SoulVesselEntity>of(SoulVesselEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.05f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("soul_vessel"));

    /** Every attribute {@link SoulVesselEntity} reads has to exist here, or {@code getAttribute} throws. */
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event)
    {
        event.put(SOUL_VESSEL.get(), SoulVesselEntity.createAttributes().build());
    }

    /** The controller {@link SoulVesselEntity}'s forced chunk ticket is registered against - see #182. */
    @SubscribeEvent
    public static void registerTicketControllers(RegisterTicketControllersEvent event)
    {
        event.register(VesselLifecycleService.TICKET_CONTROLLER);
    }

    private EntityRegistry()
    {
    }
}
