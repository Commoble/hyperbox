package commoble.hyperbox.dimension;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.aperture.ApertureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryLookupCodec;
import net.minecraft.world.Blockreader;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.SingleBiomeProvider;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.Heightmap.Type;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureManager;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;
import net.minecraft.world.server.ServerWorld;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	// put the chunk in the center of the region file
		// if we put it in the corner, then the three adjacent regions get loaded
		// and makes the save file 4x larger than it needs to be
	public static final ChunkPos CHUNKPOS = new ChunkPos(16,16);
	public static final long CHUNKID = CHUNKPOS.asLong();
	public static final BlockPos CORNER = CHUNKPOS.asBlockPos();
	public static final BlockPos CENTER = CORNER.add(7, 7, 7);
	public static final BlockPos MIN_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.add(1,1,1);
	// don't want to spawn with head in the bedrock ceiling
	public static final BlockPos MAX_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.add(13,12,13);
	
	public static final Codec<HyperboxChunkGenerator> CODEC =
		// the registry lookup doesn't actually serialize, so we don't need a field for it
		RegistryLookupCodec.getLookUpCodec(Registry.BIOME_KEY)
			.xmap(HyperboxChunkGenerator::new,HyperboxChunkGenerator::getBiomeRegistry)
			.codec();

	private final Registry<Biome> biomes;	public Registry<Biome> getBiomeRegistry() { return this.biomes; }
	
	// hardcoding this for now, may reconsider later
	public int getHeight() { return 15; }
	
	// create chunk generator at runtime when dynamic dimension is created
	public HyperboxChunkGenerator(MinecraftServer server)
	{
		this(server.func_244267_aX() // get dynamic registry
			.getRegistry(Registry.BIOME_KEY));
	}

	// create chunk generator when dimension is loaded from the dimension registry on server init
	public HyperboxChunkGenerator(Registry<Biome> biomes)
	{
		super(new SingleBiomeProvider(biomes.getOrThrow(Hyperbox.BIOME_KEY)), new DimensionStructuresSettings(false));
		this.biomes = biomes;
	}

	// get codec
	@Override
	protected Codec<? extends ChunkGenerator> func_230347_a_()
	{
		return CODEC;
	}

	// get chunk generator but with seed
	@Override
	public ChunkGenerator func_230349_a_(long p_230349_1_)
	{
		return this;	// there's no RNG in the generation so we don't need seeding
	}

	@Override
	public void generateSurface(WorldGenRegion worldGenRegion, IChunk chunk)
	{
		// set bedrock at the floor and ceiling and walls of the chunk
		// ceiling y = height-1, so if height==16, ceiling==15
		// we'll generate bedrock on xz from 0 to 14 rather than from 0 to 15 so sizes of walls are odd numbers
		ChunkPos chunkPos = chunk.getPos();
		if (chunkPos.equals(CHUNKPOS))
		{
			BlockState wallState = Blocks.BEDROCK.getDefaultState();
			BlockPos.Mutable mutaPos = new BlockPos.Mutable();
			mutaPos.setPos(CORNER);
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
							mutaPos.setPos(worldX,y,worldZ);
							chunk.setBlockState(mutaPos, wallState, false);
						}
					}
					// generate floor and ceiling
					mutaPos.setPos(worldX, 0, worldZ);
					chunk.setBlockState(mutaPos, wallState, false);
					mutaPos.setPos(worldX, ceilingY, worldZ);
					chunk.setBlockState(mutaPos, wallState, false);
				}
			}
			
			// set the apertures
			BlockState aperture = Hyperbox.INSTANCE.apertureBlock.get().getDefaultState();
			Consumer<Direction> apertureSetter = dir -> chunk.setBlockState(mutaPos, aperture.with(ApertureBlock.FACING, dir), false);
			int centerX = CENTER.getX();
			int centerY = CENTER.getY();
			int centerZ = CENTER.getZ();
			int west = centerX - 7;
			int east = centerX + 7;
			int down = centerY - 7;
			int up = centerY + 7;
			int north = centerZ - 7;
			int south = centerZ + 7;
			
			mutaPos.setPos(centerX,up,centerZ);
			apertureSetter.accept(Direction.DOWN);
			mutaPos.setPos(centerX,down,centerZ);
			apertureSetter.accept(Direction.UP);
			mutaPos.setPos(centerX,centerY,south);
			apertureSetter.accept(Direction.NORTH);
			mutaPos.setPos(centerX,centerY,north);
			apertureSetter.accept(Direction.SOUTH);
			mutaPos.setPos(east,centerY,centerZ);
			apertureSetter.accept(Direction.WEST);
			mutaPos.setPos(west,centerY,centerZ);
			apertureSetter.accept(Direction.EAST);
			
		}
	}

	// fill from noise
	@Override
	public void func_230352_b_(IWorld world, StructureManager structures, IChunk chunk)
	{
		// this is where the flat chunk generator generates flat chunks
	}

	@Override
	public int getHeight(int x, int z, Type heightmapType)
	{
		// flat chunk generator counts the solid blockstates in its list
		// debug chunk generator returns 0
		// the "normal" chunk generator generates a height via noise
		// we can assume that this is what is used to define the "initial" heightmap
		return 0;
	}

	// get base column
	@Override
	public IBlockReader func_230348_a_(int x, int z)
	{
		// flat chunk generator returns a reader over its blockstate list
		// debug chunk generator returns a reader over an empty array
		// normal chunk generator returns a column whose contents are either default block, default fluid, or air
		
		return new Blockreader(new BlockState[0]);
	}
	
	@Override
	public int getGroundHeight()
	{
		return 1;
	}

	@Override
	public int getMaxBuildHeight()
	{
		return 16;
	}
	
	// let's make sure some of the default chunk generator methods aren't doing
	// anything we don't want them to either
	
	// apply carvers
	@Override
	public void func_230350_a_(long seed, BiomeManager biomes, IChunk chunk, GenerationStage.Carving carvingStage)
	{
		// noop
	}

	// get structure position
	@Nullable
	@Override
	public BlockPos func_235956_a_(ServerWorld world, Structure<?> structure, BlockPos start, int radius, boolean skipExistingChunks)
	{
		return null;
	}
	
	// decorate biomes with features
	@Override
	public void func_230351_a_(WorldGenRegion world, StructureManager structures)
	{
		// noop
	}
	
	// has stronghold
	@Override
	public boolean func_235952_a_(ChunkPos chunkPos)
	{
		return false;
	}
	
	// create structures
	@Override
	public void func_242707_a(DynamicRegistries registries, StructureManager structures, IChunk chunk, TemplateManager templates, long seed)
	{
	}
	
	// create structure references
	@Override
	public void func_235953_a_(ISeedReader world, StructureManager structures, IChunk chunk)
	{
	}
}
