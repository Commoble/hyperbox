package net.commoble.hyperbox.blocks;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.EventHooks;

public class ApertureBlockEntity extends BlockEntity
{
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String COLOR = "color";
	
	private int weakPower = 0;
	private int strongPower = 0;
	
	private int color = Hyperbox.DEFAULT_COLOR;

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
					Direction hyperboxFace = hyperboxBlock.getCurrentFacing(parentState, side.getOpposite());
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
	
	public void updatePower(ServerLevel parentWorld, BlockPos neighborPos, BlockState neighborState, Direction directionToNeighbor)
	{
		// get power from neighbor
		int weakPower = neighborState.getSignal(parentWorld, neighborPos, directionToNeighbor);
		int strongPower = neighborState.getDirectSignal(parentWorld, neighborPos, directionToNeighbor);
		this.updatePower(weakPower,strongPower);
	}
	
	public void updatePower(int weakPower, int strongPower)
	{
		if (this.weakPower != weakPower || this.strongPower != strongPower)
		{
			this.weakPower = weakPower;
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
			this.level.neighborChanged(adjacentPos, thisBlock, this.worldPosition);
			this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, outputSide.getOpposite());
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
}
