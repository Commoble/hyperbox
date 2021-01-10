package commoble.hyperbox.dimension;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.storage.RegionFileCache;

// ensure the hyperbox dimensions only serialize the one chunk they have to reduce hard drive space per hyperbox 
public class HyperboxRegionFileCache extends RegionFileCache
{

	public HyperboxRegionFileCache(File file, boolean sync)
	{
		super(file, sync);
	}

	@Override
	protected void writeChunk(ChunkPos pos, CompoundNBT compound) throws IOException
	{
		if (pos.equals(HyperboxChunkGenerator.CHUNKPOS))
		{
			super.writeChunk(pos, compound);
		}
	}

	@Override
	@Nullable
	public CompoundNBT readChunk(ChunkPos pos) throws IOException
	{
		if (pos.equals(HyperboxChunkGenerator.CHUNKPOS))
		{
			return super.readChunk(pos);
		}
		else
		{
			return null;
		}
	}

	
}
