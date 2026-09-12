/*
 * File created ~ 24 - 4 - 2021 ~ Leaf
 * Special Thank you to ChampionAsh5357 from the forge project discord!
 * They provided a series of tutorials with examples of how to add new sections of data generation
 * Generating 20+ different metal related blocks, items, curios etc would have been a nightmare without it.
 */

package leaf.soulhome.datagen;

import leaf.soulhome.SoulHome;
import leaf.soulhome.datagen.advancements.AdvancementGen;
import leaf.soulhome.datagen.blocks.BlockStatesGen;
import leaf.soulhome.datagen.items.ItemModelsGen;
import leaf.soulhome.datagen.language.EngLangGen;
import leaf.soulhome.datagen.loot.LootTablesGen;
import leaf.soulhome.datagen.patchouli.PatchouliGen;
import leaf.soulhome.datagen.recipe.RecipeGen;
import leaf.soulhome.structures.BuiltinBondRelations;
import leaf.soulhome.structures.BuiltinFormClauses;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = SoulHome.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGen
{
    @SubscribeEvent
    public static void onDataGen(GatherDataEvent event)
    {
        // a datagen run never fires FMLCommonSetupEvent, so this has to happen here too - otherwise
        // every shipped archetype's "shape"/"relation" clause resolves to UnknownClause the moment
        // any provider reads the archetype JSON (see SoulHome#commonSetup for the same call on the
        // real game's boot path)
        BuiltinFormClauses.registerAll();
        BuiltinBondRelations.registerAll();

        DataGenerator generator = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        final PackOutput packOutput = generator.getPackOutput();

        // Every provider that can hold a registry reference takes this rather than resolving
        // registries itself; 1.20.5 made it a future because the datagen registry set is itself
        // loaded asynchronously.
        final CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();

        generator.addProvider(true, new EngLangGen(packOutput));

        if (!event.includeClient())
        {
            return;
        }

        generator.addProvider(true, new AdvancementGen(packOutput, registries, existingFileHelper));
        generator.addProvider(true, new ItemModelsGen(packOutput, existingFileHelper));
        generator.addProvider(true, new BlockStatesGen(packOutput, existingFileHelper));
        generator.addProvider(true, new LootTablesGen(packOutput, registries));
        generator.addProvider(true, new RecipeGen(packOutput, registries));

        generator.addProvider(true, new PatchouliGen(packOutput));

    }

}
