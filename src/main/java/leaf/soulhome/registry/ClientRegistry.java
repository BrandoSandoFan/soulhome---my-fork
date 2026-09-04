/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.client.SoulKeybinds;
import leaf.soulhome.dimensions.SoulDimensionRenderInfo;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SoulHome.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientRegistry
{
    public static final ResourceLocation SOUL_SKY_PROPERTY_LOC = ResourceLocationHelper.prefix("soul_sky_property");

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event)
    {
        DimensionSpecialEffects.EFFECTS.put(SOUL_SKY_PROPERTY_LOC, new SoulDimensionRenderInfo());
    }

    /**
     * The active-ability binds (#87). Registered here rather than beside the mappings themselves
     * because this is where the mod already does its client-side mod-bus registration, and every
     * other {@code @Mod.EventBusSubscriber} in the mod is a top-level class - a keybind that
     * quietly failed to register would look exactly like an ability that does not work.
     */
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(SoulKeybinds.USE_ABILITY);
        event.register(SoulKeybinds.CYCLE_ABILITY);
    }
}
