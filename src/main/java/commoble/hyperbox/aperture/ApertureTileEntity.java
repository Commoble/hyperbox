package commoble.hyperbox.aperture;

import java.util.Optional;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.box.HyperboxBlock;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class ApertureTileEntity extends TileEntity
{
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	
	private int weakPower = 0;
	private int strongPower = 0;

	public ApertureTileEntity()
	{
		super(Hyperbox.INSTANCE.apertureTileEntityType.get());
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
	{
		if (this.world instanceof ServerWorld)
		{
			ServerWorld serverWorld = (ServerWorld)this.world;
			MinecraftServer server = serverWorld.getServer();
			HyperboxWorldData data = HyperboxWorldData.getOrCreate(serverWorld);
			BlockPos parentPos = data.getParentPos();
			RegistryKey<World> parentWorldKey = data.getParentWorld();
			ServerWorld parentWorld = server.getWorld(parentWorldKey);
			if (parentWorld != null)
			{
				// delegate to the potential TE on the other side of the parent hyperbox
				// accounting for the rotation of the hyperbox's blockstate
				// if we can't find a hyperbox block, then don't return a valid capability
				BlockState parentState = parentWorld.getBlockState(parentPos);
				Block parentBlock = parentState.getBlock();
				if (parentBlock instanceof HyperboxBlock)
				{
					HyperboxBlock hyperboxBlock = (HyperboxBlock)parentBlock;
					Direction hyperboxFace = hyperboxBlock.getCurrentFacing(parentState, side.getOpposite());
					BlockPos delegatePos = parentPos.offset(hyperboxFace);
					TileEntity delegateTileEntity = parentWorld.getTileEntity(delegatePos);
					if (delegateTileEntity != null)
					{
						return delegateTileEntity.getCapability(cap, hyperboxFace.getOpposite());
					}
				}
			}
		}
		return super.getCapability(cap, side);
	}
	
	public int getPower(boolean strong)
	{
		int output = (strong ? this.strongPower : this.weakPower) - 1;
		return MathHelper.clamp(output, 0, 15);
	}
	
	public void updatePower(ServerWorld parentWorld, BlockPos neighborPos, BlockState neighborState, Direction directionToNeighbor)
	{
		// get power from neighbor
		int weakPower = neighborState.getWeakPower(parentWorld, neighborPos, directionToNeighbor);
		int strongPower = neighborState.getStrongPower(parentWorld, neighborPos, directionToNeighbor);
		this.updatePower(weakPower,strongPower);
	}
	
	public void updatePower(int weakPower, int strongPower)
	{
		if (this.weakPower != weakPower || this.strongPower != strongPower)
		{
			this.weakPower = weakPower;
			this.strongPower = strongPower;
			BlockState thisState = this.getBlockState();
			this.markDirty();	// mark te as needing its data saved
			this.world.notifyBlockUpdate(this.pos, thisState, thisState, 3); // mark te as needing data synced
			// notify neighbors so they react to the redstone output change
			// notify neighbors so they react to the redstone output change
			Direction outputSide = thisState.get(ApertureBlock.FACING);
			if (net.minecraftforge.event.ForgeEventFactory.onNeighborNotify(this.world, this.pos, thisState, java.util.EnumSet.of(outputSide), true).isCanceled())
				return;
			BlockPos adjacentPos = this.pos.offset(outputSide);
			Block thisBlock = thisState.getBlock();
			this.world.neighborChanged(adjacentPos, thisBlock, this.pos);
			this.world.notifyNeighborsOfStateExcept(adjacentPos, thisBlock, outputSide.getOpposite());
		}
	}
	
	@Override
	public CompoundNBT write(CompoundNBT compound)
	{
		super.write(compound);
		return this.writeExtraData(compound);
	}
	
	public CompoundNBT writeExtraData(CompoundNBT compound)
	{
		this.writeClientSensitiveData(compound);
		return compound;
	}

	@Override
	public void read(BlockState state, CompoundNBT nbt)
	{
		super.read(state, nbt);
		this.readExtraData(nbt);
	}
	
	public void readExtraData(CompoundNBT nbt)
	{
		this.readClientSensitiveData(nbt);
	}
	
	public CompoundNBT writeClientSensitiveData(CompoundNBT nbt)
	{
		nbt.putInt(WEAK_POWER, this.weakPower);
		nbt.putInt(STRONG_POWER, this.strongPower);
		return nbt;
	}
	
	public void readClientSensitiveData(CompoundNBT nbt)
	{
		this.weakPower = nbt.getInt(WEAK_POWER);
		this.strongPower = nbt.getInt(STRONG_POWER);
	}

	// called on server when the TE is initially loaded on client (e.g. when client loads chunk)
	// this is handled by this.handleUpdateTag, which just calls read()
	@Override
	public CompoundNBT getUpdateTag()
	{
		CompoundNBT nbt = super.getUpdateTag();
		this.writeClientSensitiveData(nbt);
		return nbt;
	}

	// called on server when notifyBlockUpdate is called, packet will be sent to client
	@Override
	public SUpdateTileEntityPacket getUpdatePacket()
	{
		return new SUpdateTileEntityPacket(this.pos, 0, this.writeClientSensitiveData(new CompoundNBT()));
	}

	// called on client to read the packet sent from getUpdatePacket
	@Override
	public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt)
	{
		this.readClientSensitiveData(pkt.getNbtCompound());
	}

	/**
	 * Retrives an aperture te from the given world-pos if the te at that position exists and is an aperture te
	 * @param world The world to check, can be null, an empty optional is returned if world is null
	 * @param pos pos
	 * @return Optional containing the te if it exists and is an aperture te, empty optional otherwise
	 */
	public static Optional<ApertureTileEntity> get(@Nullable IBlockReader world, BlockPos pos)
	{
		if (world==null)
			return Optional.empty();
		TileEntity te = world.getTileEntity(pos);
		return te instanceof ApertureTileEntity
			? Optional.of((ApertureTileEntity)te)
			: Optional.empty();
	}
}
