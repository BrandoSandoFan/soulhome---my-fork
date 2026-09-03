/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.buffs.PlayerSoulBuffs;
import leaf.soulhome.buffs.SoulBuffEffects;
import leaf.soulhome.compat.patchouli.PatchouliCompat;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.registry.*;
import leaf.soulhome.structures.BuiltinFormClauses;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SoulHome.MODID)
public class SoulHome
{
    public static final String MODID = "soulhome";
    public static final ResourceLocation SOULHOME_LOC = ResourceLocationHelper.prefix(SoulHome.MODID);

    public SoulHome()
    {
        LogHelper.info("Registering Soulhome related mcgubbins!");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
        modBus.addListener(this::registerCapabilities);

        //DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::init));
        //DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::registerIconTextures));
        //DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::retrieveRegisteredIconSprites));


        MinecraftForge.EVENT_BUS.register(this);

        //Register our deferred registries
        BlocksRegistry.BLOCKS.register(modBus);
        ItemsRegistry.ITEMS.register(modBus);
        CreativeTabsRegistry.CREATIVE_TABS.register(modBus);
        BiomeRegistry.BIOMES.register(modBus);
        DimensionRegistry.CHUNK_GENERATORS.register(modBus);
        //EffectsRegistry.EFFECTS.register(modBus);
        LootModifierRegistry.LOOT_MODIFIERS.register(modBus);
        //AttributesRegistry.ATTRIBUTES.register(modBus);
        //EntityRegistry.ENTITIES.register(modBus);

        //FeatureRegistry.FEATURES.register(modBus);
        //RecipeRegistry.SPECIAL_RECIPES.register(modBus);

        Network.init();

        // every number the structure buffs are tuned by; registered here so the file exists before
        // a world is loaded
        SoulHomeConfig.register();

        // init cross mod compatibility stuff, if relevant
        PatchouliCompat.init();
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            //FeatureRegistry.registerConfiguredFeatures();
            //EntityRegistry.PrepareEntityAttributes();
        });

        //Entity Caps

        DataSerializersRegistry.register();

        // the shape/relation vocabulary structural forms are written against (#29-#32) - must
        // happen before any datapack is read, or every "shape"/"relation" clause in it resolves to
        // UnknownClause the way an unrecognised one always does
        BuiltinFormClauses.registerAll();

        //must happen before any datapack is read, or advancements naming these triggers are dropped
        SoulAdvancements.register();

        //each buff type subscribes its own hook; see SoulBuffEffect
        SoulBuffEffects.init();

        LogHelper.info("Common setup complete!");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(PlayerSoulBuffs.class);
    }

    private void loadComplete(FMLLoadCompleteEvent event)
    {
        event.enqueueWork(() ->
        {
            //ColorHandler.init();
        });
    }

}
