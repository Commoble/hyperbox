package commoble.hyperbox.client;

import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;

public class ColorHandlers
{
	public static final int DEFAULT_HYPERBOX_BACKGROUND = 0x4a354a;
	public static final int NO_TINT = 0xFFFFFF;
	
	public static int getHyperboxBlockColor(BlockState state, @Nullable IBlockDisplayReader world, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == 1)
		{
			return DEFAULT_HYPERBOX_BACKGROUND;
		}
		else
		{
			return NO_TINT;
		}
	}
	
	public static int getHyperboxItemColor(ItemStack stack, int tintIndex)
	{
		if (tintIndex == 1)
		{
			return DEFAULT_HYPERBOX_BACKGROUND;
		}
		else
		{
			return NO_TINT;
		}
	}
}
