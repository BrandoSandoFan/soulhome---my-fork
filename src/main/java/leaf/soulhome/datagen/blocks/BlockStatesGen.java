/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.datagen.blocks;

import leaf.soulhome.SoulHome;
import leaf.soulhome.registry.BlocksRegistry;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** The mod's first block (#83): a single-texture cube is all the Soul Anchor needs. */
public class BlockStatesGen extends BlockStateProvider
{
    public BlockStatesGen(PackOutput output, ExistingFileHelper existingFileHelper)
    {
        super(output, SoulHome.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels()
    {
        simpleBlockWithItem(BlocksRegistry.SOUL_ANCHOR.get(), cubeAll(BlocksRegistry.SOUL_ANCHOR.get()));
    }
}
