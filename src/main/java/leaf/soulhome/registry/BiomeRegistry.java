/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.registry;

import leaf.soulhome.SoulHome;
import leaf.soulhome.utils.ResourceLocationHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.biome.OverworldBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class BiomeRegistry
{
	public static ResourceKey<Biome> SOUL_BIOME_KEY = ResourceKey.create(Registries.BIOME, SoulHome.SOULHOME_LOC);
	public static final DeferredRegister<Biome> BIOMES = DeferredRegister.create(ForgeRegistries.BIOMES, SoulHome.MODID);

	//public static final RegistryObject<Biome> SOUL_BIOME = BIOMES.register(SoulHome.MODID, () -> OverworldBiomes.plains(false,false,false));
}
