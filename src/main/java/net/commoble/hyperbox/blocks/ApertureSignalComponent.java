package net.commoble.hyperbox.blocks;

import java.util.Collection;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.commoble.exmachina.api.Channel;
import net.commoble.exmachina.api.SignalComponent;
import net.commoble.exmachina.api.TransmissionNode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public enum ApertureSignalComponent implements SignalComponent
{
	INSTANCE;
	public static final MapCodec<? extends ApertureSignalComponent> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<? extends SignalComponent> codec()
	{
		return CODEC;
	}

	@Override
	public Collection<TransmissionNode> getTransmissionNodes(ResourceKey<Level> levelKey, BlockGetter level, BlockPos pos, BlockState state, Channel channel)
	{
		if (level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture)
		{
			return aperture.getTransmissionNodes(levelKey, level, pos, state, channel);
		}
		return List.of();
	}
}
