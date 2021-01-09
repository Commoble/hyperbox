package commoble.hyperbox.blocks;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.DimensionHelper;
import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.INameable;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;

public class HyperboxTileEntity extends TileEntity implements INameable
{
	public static final String WORLD_KEY = "world_key";
	public static final String NAME = "CustomName"; // consistency with vanilla custom name data
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String COLOR = "color";
	// key to the hyperbox world stored in this te
	private Optional<RegistryKey<World>> worldKey = Optional.empty();
	private Optional<ITextComponent> name = Optional.empty();
	private int color = HyperboxBlockItem.DEFAULT_COLOR;
	// power output by side index of "original"/unrotated output side (linked to the aperture on the same side of the subdimension)
	private int[] weakPowerDUNSWE = {0,0,0,0,0,0};
	private int[] strongPowerDUNSWE = {0,0,0,0,0,0};
	
	public HyperboxTileEntity()
	{
		super(Hyperbox.INSTANCE.hyperboxTileEntityType.get());
	}
	
	// when a chunk is loaded, setWorldAndPos is called after reading nbt data
	// when a block is placed, setWorldAndPos is called *before* reading nbt data
	// so we want to handle forceloading of child chunk here,
	// but only when the parent chunk becomes loaded
	// and then we'll handle forceloading on-block-place in the block class
	@Override
	public void setWorldAndPos(World world, BlockPos pos)
	{
		super.setWorldAndPos(world, pos);
		
		if (this.world instanceof ServerWorld)
		{
			this.worldKey.map(key -> this.getChildWorld(((ServerWorld)world).getServer(), key))
				.ifPresent(childWorld -> childWorld.forceChunk(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, true));
		}
	}
	
	public void afterBlockPlaced()
	{
		if (this.world instanceof ServerWorld)
		{
			ServerWorld thisServerWorld = (ServerWorld)this.world;
			MinecraftServer server = thisServerWorld.getServer();
			ServerWorld childWorld = this.getOrCreateWorld(server);
			BlockState thisState = this.getBlockState();
			childWorld.forceChunk(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, true);
			Direction[] dirs = Direction.values();
			for (Direction dir : dirs)
			{
				thisState.onNeighborChange(this.world, this.pos, this.pos.offset(dir));
			}
			this.world.updateComparatorOutputLevel(this.pos, thisState.getBlock());
			HyperboxBlock.notifyNeighborsOfStrongSignalChange(thisState, childWorld, this.pos);
			for (Direction sideOfChildWorld : dirs)
			{
				this.getAperture(server, sideOfChildWorld).ifPresent(aperture ->{
					BlockPos aperturePos = aperture.getPos();
					aperture.getBlockState().onNeighborChange(aperture.getWorld(), aperturePos, aperturePos.offset(sideOfChildWorld.getOpposite()));
				});
			}
			
		}
	}

	// un-forceload the child world chunk when the parent block is removed
	@Override
	public void remove()
	{
		if (this.world instanceof ServerWorld)
		{
			ServerWorld thisWorld = (ServerWorld)this.world;
			MinecraftServer server = thisWorld.getServer();
			this.getWorldKey().ifPresent(childKey ->
			{
				ServerWorld childWorld = server.getWorld(childKey);
				if (childWorld != null)
				{
					
					HyperboxWorldData data = HyperboxWorldData.getOrCreate(childWorld);
					if (data.getParentWorld().equals(this.world.getDimensionKey()) && data.getParentPos().equals(this.pos))
					{
						childWorld.forceChunk(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, false);
//						DelayedTeleportData.getOrCreate(childWorld).scheduleDimensionUnload(childKey);
					}
				}
			});
		}
		super.remove();
	}
	
	// un-forceload the child world chunk when the parent chunk is unloaded
	@Override
	public void onChunkUnloaded()
	{
		if (this.world instanceof ServerWorld)
		{
			this.getOrCreateWorld(((ServerWorld)this.world).getServer()).forceChunk(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, false);
		}
		super.onChunkUnloaded();
	}
	
	public void setColor(int color)
	{
		if (this.color != color)
		{
			this.color = color;
			this.markDirty();
			BlockState state = this.getBlockState();
			this.world.notifyBlockUpdate(this.pos, state, state, Constants.BlockFlags.DEFAULT);
			this.world.markBlockRangeForRenderUpdate(this.pos, state, state);
		}
	}
	
	public int getColor()
	{
		return this.color;
	}

	public Optional<RegistryKey<World>> getWorldKey()
	{
		return this.worldKey;
	}
	
	public void setWorldKey(RegistryKey<World> key)
	{
		this.worldKey = Optional.ofNullable(key);
		this.markDirty();
	}

	@Override
	public ITextComponent getName()
	{
		return this.name.orElse(new TranslationTextComponent("block.hyperbox.hyperbox"));
	}

	@Override
	@Nullable
	public ITextComponent getCustomName()
	{
		return this.name.orElse(null);
	}
	
	public void setName(@Nullable ITextComponent name)
	{
		this.name = Optional.ofNullable(name);
		this.markDirty();
	}
	
	public RegistryKey<World> setNewWorldKey()
	{
		UUID uuid = UUID.randomUUID();
		String path = "generated_hyperbox/" + uuid.toString();
		ResourceLocation rl = new ResourceLocation(Hyperbox.MODID, path);
		RegistryKey<World> key = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, rl);
		this.setWorldKey(key);
		return key;
	}
	
	public RegistryKey<World> getOrCreateWorldKey()
	{
		return this.worldKey.orElseGet(() -> this.setNewWorldKey());
	}
	
	public ServerWorld getOrCreateWorld(MinecraftServer server)
	{
		ServerWorld targetWorld = this.getChildWorld(server, this.getOrCreateWorldKey());
		HyperboxWorldData.getOrCreate(targetWorld).setWorldPos(server, targetWorld, targetWorld.getDimensionKey(), this.world.getDimensionKey(), this.pos, this.getColor());
		return targetWorld;
	}
	
	public ServerWorld getChildWorld(MinecraftServer server, RegistryKey<World> key)
	{
		return DimensionHelper.getOrCreateWorld(server, key, HyperboxDimension::createDimension);
	}
	
	public int getPower(boolean strong, Direction originalFace)
	{
		int output = (strong ? this.strongPowerDUNSWE : this.weakPowerDUNSWE)[originalFace.getIndex()] - 1;
		return MathHelper.clamp(output,0,15);
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction worldSpaceFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		// delegate to the capability of the block facing the linked aperture in the hyperspace cube
		if (thisBlock instanceof HyperboxBlock && this.world instanceof ServerWorld)
		{
			HyperboxBlock hyperboxBlock = (HyperboxBlock)thisBlock;
			ServerWorld targetWorld = this.getOrCreateWorld(((ServerWorld)this.world).getServer());
			BlockPos targetPos = hyperboxBlock.getPosAdjacentToAperture(this.getBlockState(), worldSpaceFace);
			TileEntity delegateTE = targetWorld.getTileEntity(targetPos);
			if (delegateTE != null)
			{
				Direction rotatedDirection = hyperboxBlock.getOriginalFace(thisState, worldSpaceFace);
				return delegateTE.getCapability(cap, rotatedDirection);
			}
		}
		return super.getCapability(cap, worldSpaceFace);
	}
	
	public Optional<ApertureTileEntity> getAperture(MinecraftServer server, Direction sideOfChildWorld)
	{
		BlockPos aperturePos = HyperboxChunkGenerator.CENTER.offset(sideOfChildWorld, 7);;
		return ApertureTileEntity.get(this.getOrCreateWorld(server), aperturePos);
	}
	
	public void updatePower(int weakPower, int strongPower, Direction originalFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		if (thisBlock instanceof HyperboxBlock)
		{
			Direction worldSpaceFace = ((HyperboxBlock)thisBlock).getCurrentFacing(thisState, originalFace);
			int originalFaceIndex = originalFace.getIndex();
			int oldWeakPower = this.weakPowerDUNSWE[originalFaceIndex];
			int oldStrongPower = this.strongPowerDUNSWE[originalFaceIndex];
			if (oldWeakPower != weakPower || oldStrongPower != strongPower)
			{
				this.weakPowerDUNSWE[originalFaceIndex] = weakPower;
				this.strongPowerDUNSWE[originalFaceIndex] = strongPower;
				this.markDirty();	// mark te as needing its data saved
				this.world.notifyBlockUpdate(this.pos, thisState, thisState, 3); // mark te as needing data synced
				// notify neighbors so they react to the redstone output change
				if (net.minecraftforge.event.ForgeEventFactory.onNeighborNotify(this.world, this.pos, thisState, java.util.EnumSet.of(originalFace), true).isCanceled())
					return;
				BlockPos adjacentPos = this.pos.offset(worldSpaceFace);
				this.world.neighborChanged(adjacentPos, thisBlock, this.pos);
				this.world.notifyNeighborsOfStateExcept(adjacentPos, thisBlock, worldSpaceFace.getOpposite());
			}
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
		this.worldKey.ifPresent(key ->
		{
			compound.putString(WORLD_KEY, key.getLocation().toString());
		});
		this.name.ifPresent(name ->
		{
			compound.putString(NAME, ITextComponent.Serializer.toJson(name));
		});
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
		this.worldKey = nbt.contains(WORLD_KEY)
			? Optional.of(RegistryKey.getOrCreateKey(Registry.WORLD_KEY, new ResourceLocation(nbt.getString(WORLD_KEY))))
			: Optional.empty();
		this.name = nbt.contains(NAME)
			? Optional.ofNullable(ITextComponent.Serializer.getComponentFromJson(nbt.getString(NAME)))
			: Optional.empty();
		this.readClientSensitiveData(nbt);
	}
	
	public CompoundNBT writeClientSensitiveData(CompoundNBT nbt)
	{
		if (this.color != HyperboxBlockItem.DEFAULT_COLOR)
		{
			nbt.putInt(COLOR, this.color);
		}
		nbt.putIntArray(WEAK_POWER, this.weakPowerDUNSWE);
		nbt.putIntArray(STRONG_POWER, this.strongPowerDUNSWE);
		return nbt;
	}
	
	public void readClientSensitiveData(CompoundNBT nbt)
	{
		this.color = nbt.contains(COLOR)
			? nbt.getInt(COLOR)
			: HyperboxBlockItem.DEFAULT_COLOR;
		this.weakPowerDUNSWE = nbt.getIntArray(WEAK_POWER);
		this.strongPowerDUNSWE = nbt.getIntArray(STRONG_POWER);
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
	 * Retrives a hyperbox te from the given world-pos if the te at that position exists and is a hyperbox te
	 * @param world world, can be null, return value will be empty if this is null
	 * @param pos pos
	 * @return Optional containing the te if it exists and is a hyperbox te, empty optional otherwise
	 */
	public static Optional<HyperboxTileEntity> get(@Nullable IBlockReader world, BlockPos pos)
	{
		if (world == null)
			return Optional.empty();
		TileEntity te = world.getTileEntity(pos);
		return te instanceof HyperboxTileEntity
			? Optional.of((HyperboxTileEntity)te)
			: Optional.empty();
	}
}
