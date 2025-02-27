package net.commoble.hyperbox.blocks;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import net.commoble.exmachina.api.Channel;
import net.commoble.exmachina.api.Face;
import net.commoble.exmachina.api.Receiver;
import net.commoble.exmachina.api.SignalGraphUpdateGameEvent;
import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.EventHooks;

public class ApertureBlockEntity extends BlockEntity
{
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String DIGITAL_POWER = "digital_power";
	public static final String COLOR = "color";
	
	private int weakPower = 0;
	private int strongPower = 0;
	private long digitalPower = 0L;
	
	private int color = Hyperbox.DEFAULT_COLOR;
	
	private Map<Channel, Receiver> receivers = Util.make(() -> {
		Map<Channel,Receiver> map = new HashMap<>();
		
		for (DyeColor color : DyeColor.values()) {
			map.put(Channel.single(color), new ApertureBitwiseListener(color, (levelAccess,power) -> this.receiveDigitalPower(color.ordinal(), power)));
		}
		
		return map;
	});
	private Collection<Receiver> allReceivers = this.receivers.values();
	private Map<Channel, ToIntFunction<LevelReader>> suppliers = Util.make(() -> {
		Map<Channel, ToIntFunction<LevelReader>> map = new HashMap<>();
		for (DyeColor color : DyeColor.values())
		{
			long shift = color.ordinal()*4;
			map.put(Channel.single(color), reader -> Math.max((int)((this.digitalPower >> shift) & 15L) - 1, 0));
		}
		return map;
	});

	public static ApertureBlockEntity create(BlockPos pos, BlockState state)
	{
		return new ApertureBlockEntity(Hyperbox.INSTANCE.apertureBlockEntityType.get(), pos, state);
	}
	
	public ApertureBlockEntity(BlockEntityType<? extends ApertureBlockEntity> type, BlockPos pos, BlockState state)
	{
		super(type, pos, state);
	}

	@Nullable
	public <T> T getCapability(BlockCapability<T, Direction> sidedCap, Direction side)
	{
		if (this.level instanceof ServerLevel serverLevel)
		{
			MinecraftServer server = serverLevel.getServer();
			HyperboxSaveData data = HyperboxSaveData.getOrCreate(serverLevel);
			BlockPos parentPos = data.getParentPos();
			ResourceKey<Level> parentLevelKey = data.getParentWorld();
			ServerLevel parentLevel = server.getLevel(parentLevelKey);
			if (parentLevel != null)
			{
				// delegate to the potential TE on the other side of the parent hyperbox
				// accounting for the rotation of the hyperbox's blockstate
				// if we can't find a hyperbox block, then don't return a valid capability
				BlockState parentState = parentLevel.getBlockState(parentPos);
				Block parentBlock = parentState.getBlock();
				if (parentBlock instanceof HyperboxBlock hyperboxBlock)
				{
					parentLevel.registerCapabilityListener(parentPos, () -> {
						serverLevel.invalidateCapabilities(this.getBlockPos());
						return false;
					});
					Direction hyperboxFace = HyperboxBlock.getCurrentFacing(parentState, side.getOpposite());
					BlockPos delegatePos = parentPos.relative(hyperboxFace);
					return parentLevel.getCapability(sidedCap, delegatePos, hyperboxFace.getOpposite());
				}
			}
		}
		return null;
	}
	
	public int getColor()
	{
		return this.color;
	}
	
	public void setColor(int color)
	{
		if (this.color != color)
		{
			this.color = color;
			this.setChanged();
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
		}
	}
	
	public int getPower(boolean strong)
	{
		int output = (strong ? this.strongPower : this.weakPower) - 1;
		return Mth.clamp(output, 0, 15);
	}
	
	public void updateStrongPower(ServerLevel parentWorld, BlockPos neighborPos, BlockState neighborState, Direction directionToNeighbor)
	{
		// get power from neighbor
		int strongPower = neighborState.getDirectSignal(parentWorld, neighborPos, directionToNeighbor);
		this.updateStrongPower(strongPower);
	}
	
	public void updateStrongPower(int strongPower)
	{
		if (this.strongPower != strongPower)
		{
			this.strongPower = strongPower;
			BlockState thisState = this.getBlockState();
			this.setChanged();	// mark te as needing its data saved
			this.level.sendBlockUpdated(this.worldPosition, thisState, thisState, 3); // mark te as needing data synced
			// notify neighbors so they react to the redstone output change
			// notify neighbors so they react to the redstone output change
			Direction outputSide = thisState.getValue(ApertureBlock.FACING);
			if (EventHooks.onNeighborNotify(this.level, this.worldPosition, thisState, java.util.EnumSet.of(outputSide), true).isCanceled())
				return;
			BlockPos adjacentPos = this.worldPosition.relative(outputSide);
			Block thisBlock = thisState.getBlock();
			Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(this.level, outputSide, null);
			this.level.neighborChanged(adjacentPos, thisBlock, orientation);
			this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, outputSide.getOpposite(), orientation);
		}
	}
	
	@Override
	public void saveAdditional(CompoundTag compound, HolderLookup.Provider registries)
	{
		super.saveAdditional(compound, registries);
		this.writeClientSensitiveData(compound);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries)
	{
		super.loadAdditional(nbt, registries);
		this.readClientSensitiveData(nbt);
	}
	
	public CompoundTag writeClientSensitiveData(CompoundTag nbt)
	{
		nbt.putInt(WEAK_POWER, this.weakPower);
		nbt.putInt(STRONG_POWER, this.strongPower);
		nbt.putLong(DIGITAL_POWER, this.digitalPower);
		if (this.color != Hyperbox.DEFAULT_COLOR)
		{
			nbt.putInt(COLOR, this.color);
		}
		return nbt;
	}
	
	public void readClientSensitiveData(CompoundTag nbt)
	{
		this.weakPower = nbt.getInt(WEAK_POWER);
		this.strongPower = nbt.getInt(STRONG_POWER);
		this.digitalPower = nbt.getLong(DIGITAL_POWER);
		if (nbt.contains(COLOR))
		{
			this.color = nbt.getInt(COLOR);
		}
	}

	// called on server when the TE is initially loaded on client (e.g. when client loads chunk)
	// this is handled by this.handleUpdateTag, which just calls read()
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries)
	{
		CompoundTag nbt = super.getUpdateTag(registries);
		this.writeClientSensitiveData(nbt);
		return nbt;
	}

	// called on server when notifyBlockUpdate is called, packet will be sent to client
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket()
	{
		// by default, this writes getUpdateTag into the packet
		// we could theoretically write less data to reduce network traffic
		return ClientboundBlockEntityDataPacket.create(this);
	}

	// called on client to read the packet sent from getUpdatePacket
	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries)
	{
		this.readClientSensitiveData(pkt.getTag());
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries)
	{
		this.readClientSensitiveData(tag);
	}
	
	private void receiveDigitalPower(int colorIndex, int newPower)
	{
		if (this.getLevel() instanceof ServerLevel serverLevel)
		{
			ApertureBlock.getLinkedHyperbox(serverLevel, worldPosition).ifPresent(hyperbox -> {
				hyperbox.updateDigitalPower(this.getBlockState().getValue(ApertureBlock.FACING).getOpposite(), colorIndex, newPower);
			});
		}
	}

	private void receiveDigitalPower(long newPower)
	{
		if (this.getLevel() instanceof ServerLevel serverLevel)
		{
			ApertureBlock.getLinkedHyperbox(serverLevel, worldPosition).ifPresent(hyperbox -> {
				hyperbox.updateDigitalPower(this.getBlockState().getValue(ApertureBlock.FACING).getOpposite(), newPower);
			});
		}
	}

	public void updateDigitalPower(int colorIndex, int power)
	{
		long shift = 4*colorIndex;
		int oldSignalOnBand = (int)(this.digitalPower >> (shift)) & 0xF;
		if (oldSignalOnBand != power)
		{
			int newSignalOnSide = (oldSignalOnBand & ~(0xF << shift)) | (power << shift);
			this.digitalPower = newSignalOnSide;
			this.updateWeakPower();
			this.setChanged();
			this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition.relative(getBlockState().getValue(ApertureBlock.FACING)));
		}
	}

	public void updateDigitalPower(long newPower)
	{
		long oldPower = this.digitalPower;
		if (oldPower != newPower)
		{
			this.digitalPower = newPower;
			this.updateWeakPower();
			this.setChanged();;
			this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition.relative(this.getBlockState().getValue(ApertureBlock.FACING)));
		}
	}
	
	public void updateWeakPower()
	{
		long signalOnSide = this.digitalPower;
		int maxSignal = 0;
		for (int band=0; band<16; band++)
		{
			int bandPower = (int)(signalOnSide >> (4*band)) & 0xF;
			if (bandPower > maxSignal) {
				maxSignal = bandPower;
			}
		}
		this.weakPower = maxSignal;
	}

	public @Nullable Receiver getReceiverEndpoint(BlockGetter level, BlockPos receiverPos, BlockState receiverState, Direction receiverSide,
		Face connectedFace, Channel channel)
	{
		Direction directionToNeighbor = Direction.getNearest(connectedFace.pos().subtract(receiverPos), null);
		if (directionToNeighbor == receiverState.getValue(ApertureBlock.FACING)
			&& receiverPos.relative(directionToNeighbor).equals(connectedFace.pos()))
		{
			return this.receivers.get(channel);	
		}
		return null;
	}

	public Collection<Receiver> getAllReceivers(BlockGetter level, BlockPos receiverPos, BlockState receiverState, Channel channel)
	{
		return this.allReceivers;
	}

	public void resetUnusedReceivers(List<ApertureBitwiseListener> listeners)
	{
		long newPower = this.digitalPower;
		for (ApertureBitwiseListener listener : listeners)
		{
			DyeColor color = listener.color();
			int shift = 4*color.ordinal();
			newPower = (newPower &= ~(0xF << shift));
		}
		this.receiveDigitalPower(newPower);
	}

	public Map<Channel, ToIntFunction<LevelReader>> getSupplierEndpoints()
	{
		return this.suppliers;
	}
}
