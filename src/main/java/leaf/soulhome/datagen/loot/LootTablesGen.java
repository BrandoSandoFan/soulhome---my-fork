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

import java.util.List;
import java.util.Set;

/** Breaking the Soul Anchor (#83) drops itself - rank lives in the soulhome's own saved data, never on the block. */
public class LootTablesGen extends LootTableProvider
{
    public LootTablesGen(PackOutput output)
    {
        super(output, Set.of(), List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)));
    }

    public static class BlockLoot extends BlockLootSubProvider
    {
        protected BlockLoot()
        {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
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
