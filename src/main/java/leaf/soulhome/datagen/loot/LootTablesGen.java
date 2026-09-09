/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.datagen.loot;

import leaf.soulhome.registry.BlocksRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.core.HolderLookup;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Breaking the Soul Anchor (#83) drops itself - rank lives in the soulhome's own saved data, never on the block. */
public class LootTablesGen extends LootTableProvider
{
    public LootTablesGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries)
    {
        super(output, Set.of(), List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)), registries);
    }

    public static class BlockLoot extends BlockLootSubProvider
    {
        // 1.20.5 threads the datagen registry lookup through every loot sub provider, so that a
        // table holding a registry reference - an enchantment, a banner pattern - can resolve it.
        // Nothing here does; the one table is dropSelf.
        protected BlockLoot(HolderLookup.Provider registries)
        {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate()
        {
            dropSelf(BlocksRegistry.SOUL_ANCHOR.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks()
        {
            return List.of(BlocksRegistry.SOUL_ANCHOR.get());
        }
    }
}
