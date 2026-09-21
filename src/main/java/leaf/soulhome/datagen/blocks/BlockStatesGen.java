/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.datagen.blocks;

import leaf.soulhome.SoulHome;
import leaf.soulhome.registry.BlocksRegistry;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** The Soul Anchor (#83) and the Meditation Cushion (#183): both a single-texture shape. */
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

        // a single-texture cube visually, same as the Soul Anchor - MeditationCushionBlock cuts
        // its own collision shape down separately, in Java, rather than through the model
        simpleBlockWithItem(BlocksRegistry.MEDITATION_CUSHION.get(), cubeAll(BlocksRegistry.MEDITATION_CUSHION.get()));
    }
}
