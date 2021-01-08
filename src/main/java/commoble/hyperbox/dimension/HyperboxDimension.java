package commoble.hyperbox.dimension;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Dimension;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class HyperboxDimension
{	
	public static Dimension createDimension(MinecraftServer server, RegistryKey<Dimension> key)
	{
		return new Dimension(() -> getDimensionType(server), new HyperboxChunkGenerator(server));
	}
	
	public static DimensionType getDimensionType(MinecraftServer server)
	{
		return server.func_244267_aX() // get dynamic registries
			.getRegistry(Registry.DIMENSION_TYPE_KEY)
			.getOrThrow(Hyperbox.DIMENSION_TYPE_KEY);
	}
	
	public static boolean isHyperboxDimension(RegistryKey<World> key)
	{
		return key.getLocation().getNamespace().equals(Hyperbox.MODID);
	}
	
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
	public static IterationResult getHyperboxIterationDepth(MinecraftServer server, ServerWorld targetWorld, ServerWorld hyperboxWorld)
	{
		if (hyperboxWorld == null || targetWorld == null)
			return IterationResult.FAILURE;
		
		if (hyperboxWorld == targetWorld)
		{
			return IterationResult.NONE;
		}
		
		Set<RegistryKey<World>> foundKeys = new HashSet<>();
		
		ServerWorld nextWorld = hyperboxWorld;
		RegistryKey<World> nextKey = nextWorld.getDimensionKey();
		int iterations = 0;
		while (isHyperboxDimension(nextKey) && !foundKeys.contains(nextKey))
		{
			foundKeys.add(nextKey);
			HyperboxWorldData data = HyperboxWorldData.getOrCreate(nextWorld);
			RegistryKey<World> parentKey = data.getParentWorld();
			ServerWorld parentWorld = server.getWorld(parentKey);
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
	
	public static class IterationResult
	{
		public static final IterationResult FAILURE = new IterationResult(-1, null);
		public static final IterationResult NONE = new IterationResult(0, null);
		
		public final int iterations;
		public final @Nullable BlockPos parentPos;
		
		public IterationResult(int iterations, @Nullable BlockPos pos)
		{
			this.iterations = iterations;
			this.parentPos = pos;
		}
	}
}
