package net.commoble.hyperbox.dimension;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

public class HyperboxDimension
{	
	public static LevelStem createDimension(MinecraftServer server)
	{
		return new LevelStem(getDimensionTypeHolder(server), new HyperboxChunkGenerator(server));
	}
	
	public static Holder<DimensionType> getDimensionTypeHolder(MinecraftServer server)
	{
		return server.registryAccess() // get dynamic registries
			.registryOrThrow(Registries.DIMENSION_TYPE)
			.getHolderOrThrow(Hyperbox.DIMENSION_TYPE_KEY);
	}
	
	public static DimensionType getDimensionType(MinecraftServer server)
	{
		return getDimensionTypeHolder(server).value();
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
			HyperboxSaveData data = HyperboxSaveData.getOrCreate(nextWorld);
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
	
	/**
	 * Generates a dimension id based on the display name a player assigns to a hyperbox.
	 * The dimension will be in a subdirectory under the uuid of the player.
	 * If Alice names a hyperbox "Cheeses! 43 Flavors.", the dimension id will be `uuid/cheeses_43_flavors`.
	 * If the display name contains no valid alphanumeric characters, a random id will be created instead
	 * (otherwise non-latin keyboards can't make hyperboxes at all).
	 * @param player
	 * @param displayName
	 * @return
	 */
	public static ResourceLocation generateId(Player player, String displayName)
	{
		String sanitizedName = displayName
			.replace(" ", "_")
			.replaceAll("\\W", ""); // remove non-"word characters", word characters being alphanumbers and underscores
		if (sanitizedName.isBlank())
		{
			// generate random time-based UUID
			long time = player.level().getGameTime();
			long randLong = player.level().getRandom().nextLong();
			UUID uuid = new UUID(time, randLong);
			sanitizedName = uuid.toString();
		}
		String path = String.format("%s/%s", player.getStringUUID(), sanitizedName).toLowerCase(Locale.ROOT);
		return Hyperbox.id(path);
	}
}
