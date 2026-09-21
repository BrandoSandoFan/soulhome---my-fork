/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.blocks.MeditationCushionBlock;
import leaf.soulhome.blocks.SoulAnchorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Soul Anchor (#83) and the Meditation Cushion (#183). Everything else this mod grants is a dimension, an item, or a buff. */
public class BlocksRegistry
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, SoulHome.MODID);

    public static final DeferredHolder<Block, Block> SOUL_ANCHOR = BLOCKS.register("soul_anchor", () -> new SoulAnchorBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)));

    /** The good way in (#183) - cheap and soft, so it reads as furniture rather than a fixture. */
    public static final DeferredHolder<Block, Block> MEDITATION_CUSHION = BLOCKS.register("meditation_cushion", () -> new MeditationCushionBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.5f)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));
}
