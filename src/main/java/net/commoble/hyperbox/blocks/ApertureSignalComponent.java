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

public enum ApertureSource implements SignalSource
{
	INSTANCE;
	public static final MapCodec<? extends ApertureSource> CODEC = MapCodec.unit(INSTANCE);

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
		return directionToNeighbor != null
			&& supplierPos.relative(directionToNeighbor).equals(connectedFace.pos())
			&& level.getBlockEntity(supplierPos) instanceof ApertureBlockEntity aperture
			&& directionToNeighbor == supplierState.getValue(ApertureBlock.FACING)
			? aperture.getSupplierEndpoints()
			: null;
	}

	
}
