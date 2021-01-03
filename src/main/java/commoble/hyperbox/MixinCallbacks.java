package commoble.hyperbox;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxRegionFileCache;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.server.ServerWorld;

public class MixinCallbacks
{
	public static int modifyChunkManagerViewDistance(Supplier<ServerWorld> worldSupplier, int viewDistanceIn)
	{
		return HyperboxDimension.isHyperboxDimension(worldSupplier.get().getDimensionKey())
			? 2
			: viewDistanceIn;
	}
	
//	public static void onChunkHolderCannotGenerateChunks(Supplier<ServerWorld> worldSupplier, CallbackInfoReturnable<Boolean> info)
//	{
////		if (HyperboxDimension.isHyperboxDimension(worldSupplier.get().getDimensionKey()))
////		{
////			info.setReturnValue(true);
////		}
//	}
	
	public static void onIOWorkerConstruction(File file, boolean sync, Consumer<RegionFileCache> cacheConsumer)
	{
		if (file.getPath().contains("generated_hyperbox"))
		{
			cacheConsumer.accept(new HyperboxRegionFileCache(file,sync));
		}
	}
}
