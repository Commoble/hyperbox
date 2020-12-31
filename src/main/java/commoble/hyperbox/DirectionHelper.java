package commoble.hyperbox;

import javax.annotation.Nullable;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public class DirectionHelper
{
	// returns null if the two positions are not orthagonally adjacent
	// otherwise, returns the direction from from to to
	public static @Nullable Direction getDirectionToNeighborPos(BlockPos from, BlockPos to)
	{
		Direction[] dirs = Direction.values();
		int directionCount = dirs.length;
		BlockPos offset = to.subtract(from);
		for (int i=0; i<directionCount; i++)
		{
			Direction dir = dirs[i];
			if (dir.getDirectionVec().equals(offset))
			{
				return dir;
			}
		}
		
		return null;
	}
}
