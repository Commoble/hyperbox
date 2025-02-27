package net.commoble.hyperbox.blocks;

import java.util.Map;
import java.util.function.ToIntFunction;

import com.mojang.serialization.MapCodec;

import net.commoble.exmachina.api.Channel;
import net.commoble.exmachina.api.Face;
import net.commoble.exmachina.api.SignalSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public enum HyperboxSource implements SignalSource
{
	INSTANCE;
	public static final MapCodec<? extends HyperboxSource> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<? extends SignalSource> codec()
	{
		return CODEC;
	}

	@Override
	public Map<Channel, ToIntFunction<LevelReader>> getSupplierEndpoints(BlockGetter level, BlockPos supplierPos, BlockState supplierState, Direction supplierSide,
		Face connectedFace)
	{
		Direction directionToNeighbor = Direction.getNearest(connectedFace.pos().subtract(supplierPos), null);
		if (level.getBlockEntity(supplierPos) instanceof HyperboxBlockEntity hyperbox
			&& directionToNeighbor != null && supplierPos.relative(directionToNeighbor).equals(connectedFace.pos()))
		{
			return hyperbox.getSupplierEndpoints(directionToNeighbor);
		}
		return null;
	}

}
