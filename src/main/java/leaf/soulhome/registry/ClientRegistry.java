/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.client.SoulKeybinds;
import leaf.soulhome.dimensions.SoulDimensionRenderInfo;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
}
