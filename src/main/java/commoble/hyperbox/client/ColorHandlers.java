package commoble.hyperbox.client;

import javax.annotation.Nullable;

import commoble.hyperbox.blocks.ApertureTileEntity;
import commoble.hyperbox.blocks.HyperboxBlockItem;
import commoble.hyperbox.blocks.HyperboxTileEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;

public class ColorHandlers
{
	public static final int NO_TINT = 0xFFFFFF;
	public static final int BACKGROUND_TINT = 0;
	
	public static int getHyperboxBlockColor(BlockState state, @Nullable IBlockDisplayReader world, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == BACKGROUND_TINT)
		{
			if (world != null && pos != null)
			{
				TileEntity te = world.getTileEntity(pos);
				return te instanceof HyperboxTileEntity
					? ((HyperboxTileEntity)te).getColor()
					: HyperboxBlockItem.DEFAULT_COLOR;
			}
			else
			{
				return HyperboxBlockItem.DEFAULT_COLOR;
			}
		}
		else
		{
			return NO_TINT;
		}
	}
	
	// the preview renderer needs to use the color from the itemstack as TE data is not available
	public static int getHyperboxPreviewBlockColor(BlockState state, @Nullable IBlockDisplayReader world, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == BACKGROUND_TINT)
		{
			@SuppressWarnings("resource")
			ClientPlayerEntity player = Minecraft.getInstance().player;
			if (player != null)
			{
				return HyperboxBlockItem.getColorIfHyperbox(player.getHeldItemMainhand());
			}
			return HyperboxBlockItem.DEFAULT_COLOR;
		}
		else
		{
			return NO_TINT;
		}
	}
	
	public static int getApertureBlockColor(BlockState state, @Nullable IBlockDisplayReader world, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == BACKGROUND_TINT)
		{
			if (world != null && pos != null)
			{
				TileEntity te = world.getTileEntity(pos);
				return te instanceof ApertureTileEntity
					? ((ApertureTileEntity)te).getColor()
					: HyperboxBlockItem.DEFAULT_COLOR;
			}
			else
			{
				return HyperboxBlockItem.DEFAULT_COLOR;
			}
		}
		else
		{
			return NO_TINT;
		}
	}
	
	public static int getHyperboxItemColor(ItemStack stack, int tintIndex)
	{
		Item item = stack.getItem();
		if (tintIndex == BACKGROUND_TINT && item instanceof HyperboxBlockItem)
		{
			return ((HyperboxBlockItem)item).getColor(stack);
		}
		else
		{
			return NO_TINT;
		}
	}
}
