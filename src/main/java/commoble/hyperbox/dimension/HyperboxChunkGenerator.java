package commoble.hyperbox.dimension;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.aperture.ApertureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryLookupCodec;
import net.minecraft.world.Blockreader;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.SingleBiomeProvider;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap.Type;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.structure.StructureManager;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	public static final BlockPos CENTER = new BlockPos(7,7,7);
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
		return this;
	}

	@Override
	public void generateSurface(WorldGenRegion worldGenRegion, IChunk chunk)
	{
		// set bedrock at the floor and ceiling and walls of the chunk
		// ceiling y = height-1, so if height==16, ceiling==15
		// we'll generate bedrock on xz from 0 to 14 rather than from 0 to 15 so sizes of walls are odd numbers
		ChunkPos chunkPos = chunk.getPos();
		if (chunkPos.x == 0 && chunkPos.z == 0)
		{
			BlockState wallState = Blocks.BEDROCK.getDefaultState();
			BlockPos.Mutable mutaPos = new BlockPos.Mutable();
			int maxHorizontal = 14;
			int ceilingY = this.getHeight() - 1;
			for (int x=0; x<=maxHorizontal; x++)
			{
				for (int z=0; z<=maxHorizontal; z++)
				{
					if (x == 0 || x == maxHorizontal || z == 0 || z == maxHorizontal)
					{
						// generate wall
						for (int y=1; y<ceilingY; y++)
						{
							mutaPos.setPos(x,y,z);
							chunk.setBlockState(mutaPos, wallState, false);
						}
					}
					// generate floor and ceiling
					mutaPos.setPos(x, 0, z);
					chunk.setBlockState(mutaPos, wallState, false);
					mutaPos.setPos(x, ceilingY, z);
					chunk.setBlockState(mutaPos, wallState, false);
				}
			}
			
			// set the apertures
			BlockState aperture = Hyperbox.INSTANCE.apertureBlock.get().getDefaultState();
			Consumer<Direction> apertureSetter = dir -> chunk.setBlockState(mutaPos, aperture.with(ApertureBlock.FACING, dir), false);
			mutaPos.setPos(7,14,7);
			apertureSetter.accept(Direction.DOWN);
			mutaPos.setPos(7,0,7);
			apertureSetter.accept(Direction.UP);
			mutaPos.setPos(7,7,14);
			apertureSetter.accept(Direction.NORTH);
			mutaPos.setPos(7,7,0);
			apertureSetter.accept(Direction.SOUTH);
			mutaPos.setPos(14,7,7);
			apertureSetter.accept(Direction.WEST);
			mutaPos.setPos(0,7,7);
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
	
	

}
