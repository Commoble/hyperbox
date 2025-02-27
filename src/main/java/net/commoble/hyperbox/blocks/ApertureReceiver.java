package net.commoble.hyperbox.blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.commoble.exmachina.api.Channel;
import net.commoble.exmachina.api.Face;
import net.commoble.exmachina.api.Receiver;
import net.commoble.exmachina.api.SignalReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public enum ApertureReceiver implements SignalReceiver
{
	INSTANCE;
	
	public static final MapCodec<ApertureReceiver> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<? extends SignalReceiver> codec()
	{
		return CODEC;
	}

	@Override
	public @Nullable Receiver getReceiverEndpoint(BlockGetter level, BlockPos receiverPos, BlockState receiverState, Direction receiverSide,
		Face connectedFace, Channel channel)
	{
		if (level.getBlockEntity(receiverPos) instanceof ApertureBlockEntity aperture)
		{
			return aperture.getReceiverEndpoint(level, receiverPos, receiverState, receiverSide, connectedFace, channel);
		}
		return null;
	}

	@Override
	public Collection<Receiver> getAllReceivers(BlockGetter level, BlockPos receiverPos, BlockState receiverState, Channel channel)
	{
		if (level.getBlockEntity(receiverPos) instanceof ApertureBlockEntity aperture)
		{
			return aperture.getAllReceivers(level, receiverPos, receiverState, channel);
		}
		return List.of();
	}

	@Override
	public void resetUnusedReceivers(LevelAccessor level, BlockPos pos, Collection<Receiver> receivers)
	{
		List<ApertureBitwiseListener> listeners = new ArrayList<>();
		for (Receiver receiver : receivers)
		{
			if (receiver instanceof ApertureBitwiseListener listener)
			{
				listeners.add(listener);
			}
		}
		if (level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture)
		{
			aperture.resetUnusedReceivers(listeners);
		}
	}
}