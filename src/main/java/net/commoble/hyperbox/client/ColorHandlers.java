package net.commoble.hyperbox.client;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.state.BlockState;
import net.commoble.hyperbox.blocks.ApertureBlockEntity;
import net.commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.commoble.hyperbox.blocks.HyperboxBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

public class ColorHandlers
{
	public static final int NO_TINT = 0xFFFFFF;
	public static final int BACKGROUND_TINT = 0;
	
	public static int getHyperboxBlockColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == BACKGROUND_TINT)
		{
			if (level != null && pos != null)
			{
				BlockEntity te = level.getBlockEntity(pos);
				return te instanceof HyperboxBlockEntity hyperbox
					? hyperbox.getColor()
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
	public static int getHyperboxPreviewBlockColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == BACKGROUND_TINT)
		{
			@SuppressWarnings("resource")
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null)
			{
				return HyperboxBlockItem.getColorIfHyperbox(player.getMainHandItem());
			}
			return HyperboxBlockItem.DEFAULT_COLOR;
		}
		else
		{
			return NO_TINT;
		}
	}
	
	public static int getApertureBlockColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex)
	{
		if (tintIndex == BACKGROUND_TINT)
		{
			if (level != null && pos != null)
			{
				BlockEntity te = level.getBlockEntity(pos);
				return te instanceof ApertureBlockEntity aperture
					? aperture.getColor()
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
		if (tintIndex == BACKGROUND_TINT && item instanceof HyperboxBlockItem hyperboxBlockItem)
		{
			return hyperboxBlockItem.getColor(stack);
		}
		else
		{
			return NO_TINT;
		}
	}
}
