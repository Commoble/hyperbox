package commoble.hyperbox.dimension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.blocks.ApertureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	// put the chunk in the center of the region file
		// if we put it in the corner, then the three adjacent regions get loaded
		// and makes the save file 4x larger than it needs to be
	public static final ChunkPos CHUNKPOS = new ChunkPos(16,16);
	public static final long CHUNKID = CHUNKPOS.toLong();
	public static final BlockPos CORNER = CHUNKPOS.getWorldPosition();
	public static final BlockPos CENTER = CORNER.offset(7, 7, 7);
	public static final BlockPos MIN_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(1,1,1);
	// don't want to spawn with head in the bedrock ceiling
	public static final BlockPos MAX_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(13,12,13);

	private final Registry<StructureSet> structureSets;	public Registry<StructureSet> getStructureSetRegistry() { return this.structureSets; }
	private final Registry<Biome> biomes;	public Registry<Biome> getBiomeRegistry() { return this.biomes; }
	
	/** get from Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get(); **/
	public static Codec<HyperboxChunkGenerator> makeCodec()
	{
		return RecordCodecBuilder.create(builder -> builder.group(
		// the registry lookup doesn't actually serialize, so we don't need a field for it
			RegistryOps.retrieveRegistry(Registry.STRUCTURE_SET_REGISTRY).forGetter(HyperboxChunkGenerator::getStructureSetRegistry),
			RegistryOps.retrieveRegistry(Registry.BIOME_REGISTRY).forGetter(HyperboxChunkGenerator::getBiomeRegistry)
		).apply(builder, HyperboxChunkGenerator::new));
	}
	
	// hardcoding this for now, may reconsider later
	public int getHeight() { return 15; }
	
	// create chunk generator at runtime when dynamic dimension is created
	public HyperboxChunkGenerator(MinecraftServer server)
	{
		// get dynamic registry
		this(
			server.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY),
			server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY));
	}

	// create chunk generator when dimension is loaded from the dimension registry on server init
	public HyperboxChunkGenerator(Registry<StructureSet> structureSets, Registry<Biome> biomes)
	{
		super(structureSets, Optional.empty(), new FixedBiomeSource(biomes.getHolderOrThrow(Hyperbox.BIOME_KEY)));
		this.structureSets = structureSets;
		this.biomes = biomes;
	}

	// get codec
	@Override
	protected Codec<? extends ChunkGenerator> codec()
	{
		return Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get();
	}

	// get chunk generator but with seed
	@Override
	public ChunkGenerator withSeed(long p_230349_1_)
	{
		return this;	// there's no RNG in the generation so we don't need seeding
	}

	@Override
	public Climate.Sampler climateSampler()
	{	// no climate -- same as debug chunk generator
		return Climate.empty();
	}
	
	// apply carvers
	@Override
	public void applyCarvers(WorldGenRegion world, long seed, BiomeManager biomeManager, StructureFeatureManager structureManager, ChunkAccess chunkAccess, GenerationStep.Carving carvingStep)
	{
		// noop
	}

	@Override
	public void buildSurface(WorldGenRegion worldGenRegion, StructureFeatureManager structureFeatureManager, ChunkAccess chunk)
	{
		// set bedrock at the floor and ceiling and walls of the chunk
		// ceiling y = height-1, so if height==16, ceiling==15
		// we'll generate bedrock on xz from 0 to 14 rather than from 0 to 15 so sizes of walls are odd numbers
		ChunkPos chunkPos = chunk.getPos();
		if (chunkPos.equals(CHUNKPOS))
		{
			BlockState wallState = Blocks.BEDROCK.defaultBlockState();
			BlockPos.MutableBlockPos mutaPos = new BlockPos.MutableBlockPos();
			mutaPos.set(CORNER);
			int maxHorizontal = 14;
			int ceilingY = this.getHeight() - 1;
			for (int xOff=0; xOff<=maxHorizontal; xOff++)
			{
				int worldX = CORNER.getX() + xOff;
				for (int zOff=0; zOff<=maxHorizontal; zOff++)
				{
					int worldZ = CORNER.getZ() + zOff;
					if (xOff == 0 || xOff == maxHorizontal || zOff == 0 || zOff == maxHorizontal)
					{
						// generate wall
						for (int y=1; y<ceilingY; y++)
						{
							mutaPos.set(worldX,y,worldZ);
							chunk.setBlockState(mutaPos, wallState, false);
						}
					}
					// generate floor and ceiling
					mutaPos.set(worldX, 0, worldZ);
					chunk.setBlockState(mutaPos, wallState, false);
					mutaPos.set(worldX, ceilingY, worldZ);
					chunk.setBlockState(mutaPos, wallState, false);
				}
			}
			
			// set the apertures
			BlockState aperture = Hyperbox.INSTANCE.apertureBlock.get().defaultBlockState();
			Consumer<Direction> apertureSetter = dir -> chunk.setBlockState(mutaPos, aperture.setValue(ApertureBlock.FACING, dir), false);
			int centerX = CENTER.getX();
			int centerY = CENTER.getY();
			int centerZ = CENTER.getZ();
			int west = centerX - 7;
			int east = centerX + 7;
			int down = centerY - 7;
			int up = centerY + 7;
			int north = centerZ - 7;
			int south = centerZ + 7;
			
			mutaPos.set(centerX,up,centerZ);
			apertureSetter.accept(Direction.DOWN);
			mutaPos.set(centerX,down,centerZ);
			apertureSetter.accept(Direction.UP);
			mutaPos.set(centerX,centerY,south);
			apertureSetter.accept(Direction.NORTH);
			mutaPos.set(centerX,centerY,north);
			apertureSetter.accept(Direction.SOUTH);
			mutaPos.set(east,centerY,centerZ);
			apertureSetter.accept(Direction.WEST);
			mutaPos.set(west,centerY,centerZ);
			apertureSetter.accept(Direction.EAST);
			
		}
	}
	
	@Override
	public void spawnOriginalMobs(WorldGenRegion region)
	{
		// NOOP
	}

	@Override
	public int getGenDepth() // total number of available y-levels (between bottom and top)
	{
		return 16;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, StructureFeatureManager structures, ChunkAccess chunk)
	{
		// this is where the flat chunk generator generates flat chunks
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getSeaLevel()
	{
		// only used by features' generate methods
		return 0;
	}

	@Override
	public int getMinY()
	{
		// the lowest y-level in the dimension
		// debug -> 0
		// flat -> 0
		// noise -> NoiseSettings#minY
			// overworld -> -64
			// nether -> 0
		return 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Types heightmapType, LevelHeightAccessor level)
	{
		// flat chunk generator counts the solid blockstates in its list
		// debug chunk generator returns 0
		// the "normal" chunk generator generates a height via noise
		// we can assume that this is what is used to define the "initial" heightmap
		return 0;
	}

	// get base column
	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level)
	{
		// flat chunk generator returns a reader over its blockstate list
		// debug chunk generator returns a reader over an empty array
		// normal chunk generator returns a column whose contents are either default block, default fluid, or air
		
		return new NoiseColumn(0, new BlockState[0]);
	}

	@Override
	public void addDebugScreenInfo(List<String> stringsToRender, BlockPos pos)
	{
		// no info to add
	}
	
	// let's make sure some of the default chunk generator methods aren't doing
	// anything we don't want them to either

	// get structure position
	@Nullable
	@Override
	public Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>> findNearestMapFeature(ServerLevel level, HolderSet<ConfiguredStructureFeature<?, ?>> structures, BlockPos pos, int range, boolean skipKnownStructures)
	{
		return null;
	}
	
	// decorate biomes with features
	@Override
	public void applyBiomeDecoration(WorldGenLevel world, ChunkAccess chunkAccess, StructureFeatureManager structures)
	{
		// noop
	}
	
	@Override
	public int getSpawnHeight(LevelHeightAccessor level)
	{
		return 1;
	}
	
	// create structures
	@Override
	public void createStructures(RegistryAccess registries, StructureFeatureManager structures, ChunkAccess chunk, StructureManager templates, long seed)
	{
		// no structures
	}
	
	// create structure references
	@Override
	public void createReferences(WorldGenLevel world, StructureFeatureManager structures, ChunkAccess chunk)
	{
		// no structures
	}	
}
