package net.commoble.hyperbox.blocks;

import net.commoble.exmachina.api.Receiver;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LevelAccessor;

public record HyperboxBitwiseListener(DyeColor color, Direction inputSide, Receiver receiver) implements Receiver {
	@Override
	public void accept(LevelAccessor level, int power)
	{
		this.receiver.accept(level, power);
	}
}