package commoble.hyperbox.blocks;

import java.util.OptionalInt;

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

public class HyperboxBlockItem extends BlockItem implements IDyeableArmorItem
{
	public static final int DEFAULT_COLOR = 0x4a354a;

	public HyperboxBlockItem(Block blockIn, Properties builder)
	{
		super(blockIn, builder);
	}
	
	public OptionalInt getColorIfPresent(ItemStack stack)
	{
		CompoundNBT compoundnbt = stack.getChildTag("display");
		return compoundnbt != null && compoundnbt.contains("color", 99)
			? OptionalInt.of(compoundnbt.getInt("color"))
			: OptionalInt.empty();
	}

	@Override
	public int getColor(ItemStack stack)
	{
		CompoundNBT compoundnbt = stack.getChildTag("display");
		return compoundnbt != null && compoundnbt.contains("color", 99) ? compoundnbt.getInt("color") : DEFAULT_COLOR;
	}

	@Override
	protected boolean onBlockPlaced(BlockPos pos, World worldIn, PlayerEntity player, ItemStack stack, BlockState state)
	{
//		return super.onBlockPlaced(pos, worldIn, player, stack, state);
		boolean success = super.onBlockPlaced(pos, worldIn, player, stack, state);
		if (success)
		{
			HyperboxTileEntity.get(worldIn, pos).ifPresent(hyperbox ->
			{
				hyperbox.setColor(getColorIfHyperbox(stack));
			});
		}
		return success;
	}
	
	public static OptionalInt getColorIfHyperbox(ItemStack stack)
	{
		Item item = stack.getItem();
		return item instanceof HyperboxBlockItem
			? ((HyperboxBlockItem)item).getColorIfPresent(stack)
			: OptionalInt.empty();
	}
}
