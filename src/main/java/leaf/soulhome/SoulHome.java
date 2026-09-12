/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome;

import leaf.soulhome.advancements.SoulAdvancements;
import leaf.soulhome.buffs.SoulBuffEffects;
import leaf.soulhome.compat.patchouli.PatchouliCompat;
import leaf.soulhome.config.SoulHomeConfig;
import leaf.soulhome.network.Network;
import leaf.soulhome.registry.*;
import leaf.soulhome.structures.BuiltinBondRelations;
import leaf.soulhome.structures.BuiltinFormClauses;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.resources.ResourceLocation;
import leaf.soulhome.buffs.SoulBuffsAttachment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SoulHome.MODID)
public class SoulHome
{
    public static final String MODID = "soulhome";
    public static final ResourceLocation SOULHOME_LOC = ResourceLocationHelper.prefix(SoulHome.MODID);

    public SoulHome(IEventBus modBus, ModContainer container)
    {
        LogHelper.info("Registering Soulhome related mcgubbins!");

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);


        // Nothing on this class subscribes to a game-bus event - every handler is a static
        // @EventBusSubscriber elsewhere - and NeoForge's bus rejects a listener object with no
        // @SubscribeEvent methods outright rather than registering nothing, so the old
        // NeoForge.EVENT_BUS.register(this) now fails mod construction.

        //Register our deferred registries
        BlocksRegistry.BLOCKS.register(modBus);
        ItemsRegistry.ITEMS.register(modBus);
        CreativeTabsRegistry.CREATIVE_TABS.register(modBus);
        BiomeRegistry.BIOMES.register(modBus);
        DimensionRegistry.CHUNK_GENERATORS.register(modBus);
        //EffectsRegistry.EFFECTS.register(modBus);
        LootModifierRegistry.LOOT_MODIFIERS.register(modBus);
        SoulBuffsAttachment.ATTACHMENT_TYPES.register(modBus);
        DataComponentsRegistry.DATA_COMPONENTS.register(modBus);
        SoulAdvancements.TRIGGERS.register(modBus);
        DataSerializersRegistry.DATA_SERIALIZERS.register(modBus);
        //AttributesRegistry.ATTRIBUTES.register(modBus);
        //EntityRegistry.ENTITIES.register(modBus);

        //FeatureRegistry.FEATURES.register(modBus);
        //RecipeRegistry.SPECIAL_RECIPES.register(modBus);

        Network.init(modBus);

        // every number the structure buffs are tuned by; registered here so the file exists before
        // a world is loaded
        SoulHomeConfig.register(container);

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

        // the shape/relation vocabulary structural forms are written against (#29-#32) - must
        // happen before any datapack is read, or every "shape"/"relation" clause in it resolves to
        // UnknownClause the way an unrecognised one always does
        BuiltinFormClauses.registerAll();

        // and the bond relations (#140) - the same rule: before any datapack is read, or every
        // bond in it names a relation nothing knows and is dropped
        BuiltinBondRelations.registerAll();

        //each buff type subscribes its own hook; see SoulBuffEffect
        SoulBuffEffects.init();

        LogHelper.info("Common setup complete!");
    }

    private void loadComplete(FMLLoadCompleteEvent event)
    {
        event.enqueueWork(() ->
        {
            //ColorHandler.init();
        });
    }

}
