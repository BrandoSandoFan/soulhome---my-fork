/*
 * File created ~ 3 - 9 - 2026
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.blocks.SoulAnchorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's first block: the Soul Anchor (#83). Everything else this mod grants is a dimension, an item, or a buff. */
public class BlocksRegistry
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulHome.MODID);

    public static final RegistryObject<Block> SOUL_ANCHOR = BLOCKS.register("soul_anchor", () -> new SoulAnchorBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)));
}
