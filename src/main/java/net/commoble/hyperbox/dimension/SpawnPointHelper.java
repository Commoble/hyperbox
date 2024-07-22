package net.commoble.hyperbox.dimension;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;

public class SpawnPointHelper
{
	
	public static BlockPos getBestSpawnPosition(BlockGetter world, BlockPos target, BlockPos minSpawnCorner, BlockPos maxSpawnCorner)
	{
		BlockPos clampedTarget = clamp(target, minSpawnCorner, maxSpawnCorner);
		BlockPos bestPos = clampedTarget;
		// amount of player space at best pos
		// empty block, one empty block above, one solid block below -> 3
		// empty block with one empty block above, no empty block below -> 2
		// non-empty block with one empty block above -> 1
		// non-empty block above target -> 0
		int bestPosViability = -1;
		Set<BlockPos> visited = new HashSet<>();
		LinkedList<BlockPos> remaining = new LinkedList<>();
		remaining.add(clampedTarget);
		// do a breadth-first search from starting point, within the bounds specified
		while(remaining.size() > 0)
		{
			// each block we iterate over is going to be no closer to target than any other block we've iterated over
			// (may be the same distance as a previously iterated block)
			BlockPos nextPos = remaining.removeFirst();
			int viability = getViability(world, nextPos);
			// this is the first viability-3 block we've found
			if (viability == 3)
			{
				return nextPos; // closest viability-2 block to target is the best possible result
			}
			else
			{
				// a more viable block than the current best-known block is strictly better
				if (viability > bestPosViability)
				{
					bestPos = nextPos;
					bestPosViability = viability;
				}
				
				Direction[] dirs = Direction.values();
				int dirCount = dirs.length;
				for (int i=0; i<dirCount; i++)
				{
					Direction dir = dirs[i];
					BlockPos nextPosToVisit = nextPos.relative(dir);
					if (!visited.contains(nextPosToVisit)
						&& isPosAllowed(world, nextPosToVisit, minSpawnCorner, maxSpawnCorner))
					{
						remaining.add(nextPosToVisit);
						visited.add(nextPosToVisit);
					}
				}
			}
		}
		
		return bestPos;
		
	}
	
	public static BlockPos clamp(BlockPos pos, BlockPos min, BlockPos max)
	{
		int x = Mth.clamp(pos.getX(), min.getX(), max.getX());
		int y = Mth.clamp(pos.getY(), min.getY(), max.getY());
		int z = Mth.clamp(pos.getZ(), min.getZ(), max.getZ());
		return new BlockPos(x,y,z);
	}
	
	public static boolean isPosAllowed(BlockGetter world, BlockPos pos, BlockPos min, BlockPos max)
	{
		return isPosWithinBounds(pos, min, max)
			&& world.getBlockState(pos).getDestroySpeed(world, pos) >= 0; // don't search through the indestructible walls
	}
	
	public static boolean isPosWithinBounds(BlockPos pos, BlockPos min, BlockPos max)
	{		
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		return x >= min.getX()
			&& x <= max.getX()
			&& y >= min.getY()
			&& y <= max.getY()
			&& z >= min.getZ()
			&& z <= max.getZ();
	}
	
	public static int getViability(BlockGetter world, BlockPos target)
	{
		return doesBlockBlockHead(world, target.above())
				? 0
				: doesBlockBlockFeet(world, target)
					? 1
					: doesBlockBlockFeet(world,target.below())
						? 3
						: 2;
	}

	// return true if the block has no interaction shape (doesn't block cursor interactions)
	public static boolean doesBlockBlockHead(BlockGetter world, BlockPos pos)
	{
		return !world.getBlockState(pos).getShape(world,pos).isEmpty();
	}
	
	// return true if the block has no collision shape (doesn't prevent movement)
	public static boolean doesBlockBlockFeet(BlockGetter world, BlockPos pos)
	{
		return !world.getBlockState(pos).getCollisionShape(world,pos).isEmpty();
	}
}
