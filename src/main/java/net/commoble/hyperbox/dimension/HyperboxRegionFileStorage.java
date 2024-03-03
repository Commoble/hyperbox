package net.commoble.hyperbox.dimension;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

// ensure the hyperbox dimensions only serialize the one chunk they have to reduce hard drive space per hyperbox 
public class HyperboxRegionFileStorage extends RegionFileStorage
{

	public HyperboxRegionFileStorage(Path path, boolean sync)
	{
		super(path, sync);
	}

	@Override
	protected void write(ChunkPos pos, CompoundTag compound) throws IOException
	{
		if (pos.equals(HyperboxChunkGenerator.CHUNKPOS))
		{
			super.write(pos, compound);
		}
	}

	@Override
	@Nullable
	public CompoundTag read(ChunkPos pos) throws IOException
	{
		if (pos.equals(HyperboxChunkGenerator.CHUNKPOS))
		{
			return super.read(pos);
		}
		else
		{
			return null;
		}
	}

	
}
