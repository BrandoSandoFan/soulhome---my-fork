/*
 * File created ~ 27 - 1 - 2022 ~Leaf
 */

package leaf.soulhome.registry;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import leaf.soulhome.SoulHome;
import leaf.soulhome.dimensions.SoulChunkGenerator;
import leaf.soulhome.network.Network;
import leaf.soulhome.network.SyncDimensionListMessage;
import leaf.soulhome.utils.DimensionHelper;
import leaf.soulhome.utils.LogHelper;
import leaf.soulhome.mixin.DefrostedRegistry;
import leaf.soulhome.mixin.StructureTemplateAccessor;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

public class DimensionRegistry
{
	public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, SoulHome.MODID);
	public static final ResourceKey<Biome> SOULHOME_BIOME = ResourceKey.create(Registries.BIOME, new ResourceLocation(SoulHome.MODID, SoulHome.MODID));
	public static final RegistryObject<Codec<? extends ChunkGenerator>> CHUNK_GENERATOR = CHUNK_GENERATORS.register(SoulHome.MODID, () -> SoulChunkGenerator.providerCodec);

	// Number of soul_island<n> structure templates that ship with the mod.
	// TODO: add more islands, bump this as more are added.
	private static final int ISLAND_STYLE_COUNT = 3;


	public static class DimensionTypes
	{
		public static final ResourceKey<DimensionType> SOUL_DIMENSION_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, SoulHome.SOULHOME_LOC);
	}


	public static LevelStem soulDimensionBuilder(MinecraftServer server, ResourceKey<LevelStem> dimensionKey)
	{
		RegistryAccess registries = server.registryAccess(); // get dynamic registries
		return new LevelStem(
				registries.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(DimensionTypes.SOUL_DIMENSION_TYPE),
				new SoulChunkGenerator(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(BiomeRegistry.SOUL_BIOME_KEY)));
	}


	// Once a dimension is created using this method, it will load automatically on server boot
	// Special thanks to the NewTardisMod team. This would have been a nightmare to figure out
	// https://gitlab.com/Spectre0987/TardisMod-1-14/-/tree/1.16
	public static ServerLevel createSoulDimension(MinecraftServer server, ResourceKey<Level> worldKey, String userUUID)
	{
		ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, worldKey.location());

		BiFunction<MinecraftServer, ResourceKey<LevelStem>, LevelStem> dimensionFactory = DimensionRegistry::soulDimensionBuilder;
		LevelStem dimension = dimensionFactory.apply(server, dimensionKey);

		// Refer to META-INF/accesstransformer.cfg here for changing private fields to public
		Executor executor = server.executor;
		LevelStorageSource.LevelStorageAccess levelSave = server.storageSource;
		ChunkProgressListener chunkProgressListener = server.progressListenerFactory.create(11);

		//configs
		WorldData serverConfiguration = server.getWorldData();

		// register the dimension
		Registry<LevelStem> dimensionRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
		if (dimensionRegistry instanceof WritableRegistry)
		{
			final WritableRegistry<LevelStem> writableRegistry = (WritableRegistry<LevelStem>) dimensionRegistry;
			boolean wasFrozen = ((DefrostedRegistry) writableRegistry).getFrozen();
			((DefrostedRegistry) writableRegistry).setFrozen(false);
			writableRegistry.register(dimensionKey, dimension, Lifecycle.stable());

			if (wasFrozen)
			{
				((DefrostedRegistry) writableRegistry).setFrozen(true);
			}
		}
		else
		{
			throw new IllegalStateException("Unable to register dimension '" + dimensionKey.location() + "'! Registry not writable!");
		}

		//base the world info on overworld? Not actually sure if that's what I want for soul dimensions
		//todo revisit this later. Don't just forget about it.
		// ^ LOL
		// ^ it's the year 2025 and I still haven't revisited this

		DerivedLevelData derivedWorldInfo = new DerivedLevelData(serverConfiguration, serverConfiguration.overworldData());

		ServerLevel newSoulWorld = new ServerLevel(
				server,
				executor,
				levelSave,
				derivedWorldInfo,
				worldKey,
				dimension,
				chunkProgressListener,
				serverConfiguration.isDebugWorld(),
				BiomeManager.obfuscateSeed(serverConfiguration.worldGenOptions().seed()),
				ImmutableList.of(),
				false,
				(RandomSequences) null);


		// pay attention to borders?
		// why do we link the soul world borders to the over world borders?
		// todo ask whether this is actually needed
		server.getLevel(Level.OVERWORLD).getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(newSoulWorld.getWorldBorder()));

		// add the new dimension to the map, so that it auto loads on server boot.
		// forgeGetWorldMap is marked as deprecated because you can
		// screw up a lot of things if you mess with this map.
		// So be very very careful when touching it.
		Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
		map.put(worldKey, newSoulWorld);

		// increment forge worldArrayMarker, so that the world will tick()
		server.markWorldsDirty();

		//then post an event for our new world. Welcome :)
		MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(newSoulWorld));
		LogHelper.info("New soul dimension has been created: " + dimensionKey.location());

		StructurePlaceSettings settings = (new StructurePlaceSettings()).setIgnoreEntities(true).setMirror(Mirror.NONE).setRotation(Rotation.NONE);
		StructureTemplateManager manager = newSoulWorld.getStructureManager();

		// Use the UUID of the player to choose an island structure, different players will get different islands representitive of their 'souls'
		UUID soul = UUID.fromString(userUUID);
		Random rand = new Random(soul.getLeastSignificantBits() ^ soul.getMostSignificantBits());
		// nextInt(bound) rather than nextInt() % bound: the latter returns the full int range,
		// so a third of players got a negative style and a soul_island-1 / soul_island-2 that
		// does not exist, silently falling through to the legacy platform below.
		int islandStyle = rand.nextInt(ISLAND_STYLE_COUNT);
		ResourceLocation soulIslandLocation = new ResourceLocation(SoulHome.MODID, "soul_island" + islandStyle);

		Optional<StructureTemplate> templateOptional = manager.get(soulIslandLocation);
		if (templateOptional.isPresent())
		{
			StructureTemplate template = templateOptional.get();

			// Anchoring by the template's bounding-box height drops the player onto whatever the
			// template's *tallest column* happens to be, not onto the ground under their feet - all
			// three shipped islands carry air headroom (trees, hills) above their walkable surface,
			// so the top of the box is sky and players fell 6-19 blocks before landing. Anchor by
			// the highest solid block in the spawn column instead, so the entry point at
			// FLOOR_LEVEL + 2 always lands the player one block above solid ground. See #97.
			int localSpawnX = template.getSize().getX() / 2;
			int localSpawnZ = template.getSize().getZ() / 2;
			int highestSolidLocalY = highestSolidBlockY(template, localSpawnX, localSpawnZ);

			if (highestSolidLocalY == Integer.MIN_VALUE)
			{
				// nothing solid directly under the spawn column (an odd or floating template) -
				// fall back to the template's global highest solid block rather than dropping the
				// player through open air regardless
				highestSolidLocalY = highestSolidBlockY(template);
			}

			int originY = highestSolidLocalY == Integer.MIN_VALUE
					// no solid block anywhere in the template - nothing to anchor to, keep the
					// previous behaviour rather than guessing
					? DimensionHelper.FLOOR_LEVEL - template.getSize().getY()
					: DimensionHelper.FLOOR_LEVEL - highestSolidLocalY;

			if (originY < 0)
			{
				// the dimension's min_y is 0; a template taller than FLOOR_LEVEL allows would
				// otherwise be silently truncated by the world border instead of placed whole
				LogHelper.warn("Soul island template '" + soulIslandLocation
						+ "' is taller than the space below FLOOR_LEVEL allows (origin Y would be "
						+ originY + "); clamping to 0.");
				originY = 0;
			}

			BlockPos pos = new BlockPos(-template.getSize().getX() / 2, originY, -template.getSize().getZ() / 2);
			template.placeInWorld(newSoulWorld, pos, new BlockPos(0, 0, 0), settings, newSoulWorld.random, 0);
		}
		else
		{
			//put in the platform via legacy method if structure fails to load
			LogHelper.warn("Dimension generated via legacy method!!");
			final int PLATFORM_RADIUS = 16;
			for (int x = -PLATFORM_RADIUS; x < PLATFORM_RADIUS; x++)
			{
				for (int z = -PLATFORM_RADIUS; z < PLATFORM_RADIUS; z++)
				{
					newSoulWorld.setBlockAndUpdate(new BlockPos(x, DimensionHelper.FLOOR_LEVEL, z), Blocks.GRASS_BLOCK.defaultBlockState());
					newSoulWorld.setBlockAndUpdate(new BlockPos(x, DimensionHelper.FLOOR_LEVEL - 1, z), Blocks.DIRT.defaultBlockState());
					newSoulWorld.setBlockAndUpdate(new BlockPos(x, DimensionHelper.FLOOR_LEVEL - 2, z), Blocks.DIRT.defaultBlockState());
					newSoulWorld.setBlockAndUpdate(new BlockPos(x, DimensionHelper.FLOOR_LEVEL - 3, z), Blocks.DIRT.defaultBlockState());
					newSoulWorld.setBlockAndUpdate(new BlockPos(x, DimensionHelper.FLOOR_LEVEL - 4, z), Blocks.STONE.defaultBlockState());
				}
			}
		}
		//send a packet to all players, requesting that they refresh their dimension list.
		Network.sendPacketToAll(new SyncDimensionListMessage(worldKey, true));
		//finally return the new world so the player can finish teleporting there
		return newSoulWorld;
	}

	public static ServerLevel createSoulDimension(MinecraftServer server, ResourceKey<Level> worldKey)
	{
		return createSoulDimension(server, worldKey, UUID.randomUUID().toString());
	}

	// Highest local Y (template space, before placement) whose block is not air, restricted to one
	// XZ column. Integer.MIN_VALUE if the column has no solid block at all.
	private static int highestSolidBlockY(StructureTemplate template, int localX, int localZ)
	{
		int highest = Integer.MIN_VALUE;

		for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).getPalettes())
		{
			for (StructureTemplate.StructureBlockInfo info : palette.blocks())
			{
				if (info.pos.getX() == localX && info.pos.getZ() == localZ && !info.state.isAir())
				{
					highest = Math.max(highest, info.pos.getY());
				}
			}
		}

		return highest;
	}

	// Same, but across every column - the fallback when the spawn column itself is empty.
	private static int highestSolidBlockY(StructureTemplate template)
	{
		int highest = Integer.MIN_VALUE;

		for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).getPalettes())
		{
			for (StructureTemplate.StructureBlockInfo info : palette.blocks())
			{
				if (!info.state.isAir())
				{
					highest = Math.max(highest, info.pos.getY());
				}
			}
		}

		return highest;
	}
}
