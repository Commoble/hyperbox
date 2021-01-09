package commoble.hyperbox.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.IDyeableArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// we set the color info to both the display AND the blockentity tag
// so creative pickblock doesn't yeet the data, but the display info still works otherwise
public class HyperboxBlockItem extends BlockItem implements IDyeableArmorItem
{
	public static final int DEFAULT_COLOR = 0x4a354a;

	public HyperboxBlockItem(Block blockIn, Properties builder)
	{
		super(blockIn, builder);
	}

	@Override
	public int getColor(ItemStack stack)
	{
		CompoundNBT displayTag = stack.getChildTag("display");
		
		if (displayTag != null && displayTag.contains("color", 99))
		{
			return displayTag.getInt("color");
		}
		
		CompoundNBT blockEntityTag = stack.getChildTag("BlockEntityTag");
		
		if (blockEntityTag != null && blockEntityTag.contains("color", 99))
		{
			return blockEntityTag.getInt("color");
		}
		
		return DEFAULT_COLOR;
	}

	@Override
	public boolean hasColor(ItemStack stack)
	{
		CompoundNBT displayTag = stack.getChildTag("display");
		if (displayTag != null && displayTag.contains("color", 99))
			return true;

		CompoundNBT blockEntityTag = stack.getChildTag("BlockEntityTag");
		return blockEntityTag != null && blockEntityTag.contains("color", 99);
	}

	@Override
	public void removeColor(ItemStack stack)
	{
		CompoundNBT displayTag = stack.getChildTag("display");
		if (displayTag != null && displayTag.contains("color"))
		{
			displayTag.remove("color");
		}
		CompoundNBT blockEntityTag = stack.getChildTag("BlockEntityTag");
		if (blockEntityTag != null && blockEntityTag.contains("color"))
		{
			blockEntityTag.remove("color");
		}

	}

	@Override
	public void setColor(ItemStack stack, int color)
	{
		stack.getOrCreateChildTag("display").putInt("color", color);
		stack.getOrCreateChildTag("BlockEntityTag").putInt("color", color);
	}

	@Override
	protected boolean onBlockPlaced(BlockPos pos, World worldIn, PlayerEntity player, ItemStack stack, BlockState state)
	{
		// return super.onBlockPlaced(pos, worldIn, player, stack, state);
		boolean success = super.onBlockPlaced(pos, worldIn, player, stack, state);
		if (success)
		{
			HyperboxTileEntity.get(worldIn, pos).ifPresent(hyperbox -> {
				hyperbox.setColor(getColorIfHyperbox(stack));
			});
		}
		return success;
	}

	public static int getColorIfHyperbox(ItemStack stack)
	{
		Item item = stack.getItem();
		return item instanceof HyperboxBlockItem ? ((HyperboxBlockItem) item).getColor(stack) : DEFAULT_COLOR;
	}
}
