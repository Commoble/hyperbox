package commoble.hyperbox.dimension;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

public class HyperboxDimension
{	
	public static LevelStem createDimension(MinecraftServer server)
	{
		return new LevelStem(() -> getDimensionType(server), new HyperboxChunkGenerator(server));
	}
	
	public static DimensionType getDimensionType(MinecraftServer server)
	{
		return server.registryAccess() // get dynamic registries
			.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
			.getOrThrow(Hyperbox.DIMENSION_TYPE_KEY);
	}
	
//	public static boolean isHyperboxDimension(ResourceKey<Level> key)
//	{
//		return key.location().getNamespace().equals(Hyperbox.MODID);
//	}
	
	/**
	 * Returns whether a given world is reachable from another given world using hyperbox parenting.
	 * If either world is null, return -1.
	 * If the worlds are the same world, returns 0.
	 * Otherwise, searches upward for hyperbox parenting until one of the following scenarios is encountered:
	 * If a nonexistant parent world is encountered, returns -1.
	 * If a previously encountered world is encountered, returns -1.
	 * If a non-hyperbox world is encountered before the target world, returns -1.
	 * If the target world is encountered, returns the iteration depth between the two worlds (positive integer).
	 * 
	 * @param server a MinecraftServer
	 * @param targetWorld The world we want to know is reachable from startWorld
	 * @param startWorld The world we are starting our search from
	 * @return A result including the target world's hyperbox position and the parenting distance between the two worlds, or -1 if the target is not reachable.
	 * The result's position will be null if the iteration depth is not positive.
	 */
	public static IterationResult getHyperboxIterationDepth(MinecraftServer server, ServerLevel targetWorld, ServerLevel hyperboxWorld)
	{
		if (hyperboxWorld == null || targetWorld == null)
			return IterationResult.FAILURE;
		
		if (hyperboxWorld == targetWorld)
		{
			return IterationResult.NONE;
		}
		
		Set<ResourceKey<Level>> foundKeys = new HashSet<>();
		
		ServerLevel nextWorld = hyperboxWorld;
		ResourceKey<Level> nextKey = nextWorld.dimension();
		int iterations = 0;
		DimensionType hyperboxDimensionType = getDimensionType(server);
		while (nextWorld.dimensionType() == hyperboxDimensionType && !foundKeys.contains(nextKey))
		{
			foundKeys.add(nextKey);
			HyperboxWorldData data = HyperboxWorldData.getOrCreate(nextWorld);
			ResourceKey<Level> parentKey = data.getParentWorld();
			ServerLevel parentWorld = server.getLevel(parentKey);
			iterations++;
			if (parentWorld == targetWorld)
				return new IterationResult(iterations, data.getParentPos());
			if (parentWorld == null)
				return IterationResult.FAILURE;
			nextKey = parentKey;
			nextWorld = parentWorld;
		}
		
		return IterationResult.FAILURE;
	}
	
	public static record IterationResult(int iterations, @Nullable BlockPos parentPos)
	{
		public static final IterationResult FAILURE = new IterationResult(-1, null);
		public static final IterationResult NONE = new IterationResult(0, null);
	}
}
