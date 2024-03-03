package net.commoble.hyperbox.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class HyperboxBlockItem extends BlockItem implements DyeableLeatherItem
{
	public static final int DEFAULT_COLOR = 0x4a354a;
	public static final String BLOCKENTITY_KEY = "BlockEntityTag";
	public static final String DISPLAY_KEY = "display";
	public static final String COLOR_KEY = HyperboxBlockEntity.COLOR;

	public HyperboxBlockItem(Block block, Properties builder)
	{
		super(block, builder);
	}

	@Override
	public int getColor(ItemStack stack)
	{
		CompoundTag displayTag = stack.getTagElement(DISPLAY_KEY);
		
		if (displayTag != null && displayTag.contains(COLOR_KEY, 99))
		{
			return displayTag.getInt(COLOR_KEY);
		}
		
		CompoundTag blockEntityTag = stack.getTagElement(BLOCKENTITY_KEY);
		
		if (blockEntityTag != null && blockEntityTag.contains(COLOR_KEY, 99))
		{
			return blockEntityTag.getInt(COLOR_KEY);
		}
		
		return DEFAULT_COLOR;
	}

	@Override
	public boolean hasCustomColor(ItemStack stack)
	{
		CompoundTag displayTag = stack.getTagElement(DISPLAY_KEY);
		if (displayTag != null && displayTag.contains(COLOR_KEY, 99))
			return true;

		CompoundTag blockEntityTag = stack.getTagElement(BLOCKENTITY_KEY);
		return blockEntityTag != null && blockEntityTag.contains(COLOR_KEY, 99);
	}

	@Override
	public void clearColor(ItemStack stack)
	{
		CompoundTag displayTag = stack.getTagElement(DISPLAY_KEY);
		if (displayTag != null && displayTag.contains(COLOR_KEY))
		{
			displayTag.remove(COLOR_KEY);
		}
		CompoundTag blockEntityTag = stack.getTagElement(BLOCKENTITY_KEY);
		if (blockEntityTag != null && blockEntityTag.contains(COLOR_KEY))
		{
			blockEntityTag.remove(COLOR_KEY);
		}

	}

	@Override
	public void setColor(ItemStack stack, int color)
	{
		stack.getOrCreateTagElement(DISPLAY_KEY).putInt(COLOR_KEY, color);
		stack.getOrCreateTagElement(BLOCKENTITY_KEY).putInt(COLOR_KEY, color);
	}

	@Override
	protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state)
	{
		boolean success = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
		if (success && level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox)
		{
			hyperbox.setColor(getColorIfHyperbox(stack));
		}
		return success;
	}

	public static int getColorIfHyperbox(ItemStack stack)
	{
		Item item = stack.getItem();
		return item instanceof HyperboxBlockItem hyperboxItem
			? hyperboxItem.getColor(stack)
			: DEFAULT_COLOR;
	}
}
