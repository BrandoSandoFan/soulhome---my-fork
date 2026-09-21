/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.client.SoulKeybinds;
import leaf.soulhome.client.gui.SoulAmbienceOptionsScreen;
import leaf.soulhome.client.render.SoulBarrageShotModel;
import leaf.soulhome.client.render.SoulBarrageShotRenderer;
import leaf.soulhome.client.render.SoulVesselRenderer;
import leaf.soulhome.dimensions.SoulDimensionRenderInfo;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ClientRegistry
{
    public static final ResourceLocation SOUL_SKY_PROPERTY_LOC = ResourceLocationHelper.prefix("soul_sky_property");

    /**
     * The soul dimension's sky.
     *
     * <p>Used to be a put straight into {@code DimensionSpecialEffects.EFFECTS} during client setup,
     * behind an access transformer. NeoForge has an event for it, which is both the supported route
     * and the correctly timed one - client setup runs off-thread, and the map it was writing to is
     * not concurrent.
     */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event)
    {
        event.register(SOUL_SKY_PROPERTY_LOC, new SoulDimensionRenderInfo());
    }

    /**
     * The ambience switches, under Mods -> SoulHome -> Config (#167).
     *
     * <p>NeoForge would generate a screen from the spec on its own, and it would be a perfectly
     * good one. This is a hand-written screen instead for one reason: it says on the screen itself
     * that all of this is cosmetic, which is the single thing a player needs to know before
     * deciding, and a generated screen has nowhere to put it.
     */
    @SubscribeEvent
    public static void registerConfigScreen(FMLClientSetupEvent event)
    {
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new SoulAmbienceOptionsScreen(parent));
    }

    /**
     * The active-ability binds (#87). Registered here rather than beside the mappings themselves
     * because this is where the mod already does its client-side mod-bus registration, and every
     * other {@code @EventBusSubscriber} in the mod is a top-level class - a keybind that
     * quietly failed to register would look exactly like an ability that does not work.
     */
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(SoulKeybinds.USE_ABILITY);
        event.register(SoulKeybinds.CYCLE_ABILITY);
    }

    /** Barrage's shell (#194) - see {@link SoulBarrageShotRenderer}. */
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(EntityRegistry.SOUL_BARRAGE_SHOT.get(), SoulBarrageShotRenderer::new);

        // the body a player leaves behind when they enter their soul (#182)
        event.registerEntityRenderer(EntityRegistry.SOUL_VESSEL.get(), SoulVesselRenderer::new);
    }

    /** {@link SoulBarrageShotModel}'s single cube. */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(SoulBarrageShotModel.LAYER_LOCATION, SoulBarrageShotModel::createBodyLayer);
    }
}
