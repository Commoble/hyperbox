package commoble.hyperbox.blocks;

import java.util.Optional;

import commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HyperboxMenu extends AbstractContainerMenu
{
	private final Optional<HyperboxBlockEntity> hyperbox;
	
	public static HyperboxMenu makeClientMenu(int id, Inventory playerInventory)
	{
		return new HyperboxMenu(Hyperbox.INSTANCE.hyperboxMenuType.get(), id, Optional.empty());
	}
	
	public static MenuProvider makeServerMenu(HyperboxBlockEntity hyperbox)
	{
		return new SimpleMenuProvider(
			(id, inventory, player) -> new HyperboxMenu(Hyperbox.INSTANCE.hyperboxMenuType.get(), id, Optional.ofNullable(hyperbox)),
			Component.translatable("menu.hyperbox"));
	}

	protected HyperboxMenu(MenuType<?> type, int id, Optional<HyperboxBlockEntity> hyperbox)
	{
		super(type, id);
		this.hyperbox = hyperbox;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int id)
	{
		return ItemStack.EMPTY;
	}

	// called on the server to check whether the menu is still valid or should be closed
	@Override
	public boolean stillValid(Player player)
	{
		return this.hyperbox.map(box ->
		{
			Level level = box.getLevel();
			BlockPos pos = box.getBlockPos();
			return level != null
				&& level == player.level()
				&& level.getBlockEntity(pos) == box
				&& !(player.distanceToSqr((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D) > 64.0D);
		})
		.orElse(false);
	}
	
	public Optional<HyperboxBlockEntity> hyperbox()
	{
		return this.hyperbox;
	}
}
