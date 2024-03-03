package commoble.hyperbox.blocks;

import java.util.Optional;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.DelayedTeleportData;
import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxSaveData;
import commoble.hyperbox.dimension.ReturnPoint;
import commoble.hyperbox.dimension.SpawnPointHelper;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.EventHooks;

public class HyperboxBlockEntity extends BlockEntity implements Nameable
{
	public static final String WORLD_KEY = "world_key";
	public static final String NAME = "CustomName"; // consistency with vanilla custom name data
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String COLOR = "color";
	// key to the hyperbox world stored in this te
	private Optional<ResourceKey<Level>> levelKey = Optional.empty();
	private Optional<Component> name = Optional.empty();
	private int color = HyperboxBlockItem.DEFAULT_COLOR;
	// power output by side index of "original"/unrotated output side (linked to the aperture on the same side of the subdimension)
	private int[] weakPowerDUNSWE = {0,0,0,0,0,0};
	private int[] strongPowerDUNSWE = {0,0,0,0,0,0};
	
	public static HyperboxBlockEntity create(BlockPos pos, BlockState state)
	{
		return new HyperboxBlockEntity(Hyperbox.INSTANCE.hyperboxBlockEntityType.get(), pos, state);
	}
	
	public HyperboxBlockEntity(BlockEntityType<? extends HyperboxBlockEntity> type, BlockPos pos, BlockState state)
	{
		super(type, pos, state);
	}
	
	public void updateDimensionAfterPlacingBlock()
	{
		if (this.level instanceof ServerLevel thisServerLevel)
		{
			MinecraftServer server = thisServerLevel.getServer();
			ServerLevel childLevel = this.getLevelIfKeySet(server);
			if (childLevel == null)
				return;
			if (Hyperbox.INSTANCE.commonConfig.autoForceHyperboxChunks.get())
			{
				childLevel.getChunk(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z);
				childLevel.setChunkForced(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, true);
				// we have to do this to make the child world's chunk start ticking
				childLevel.getChunkSource().updateChunkForced(HyperboxChunkGenerator.CHUNKPOS, true);
			}
			BlockState thisState = this.getBlockState();
			Direction[] dirs = Direction.values();
			for (Direction dir : dirs)
			{
				thisState.onNeighborChange(this.level, this.worldPosition, this.worldPosition.relative(dir));
			}
			this.level.updateNeighbourForOutputSignal(this.worldPosition, thisState.getBlock());
			HyperboxBlock.notifyNeighborsOfStrongSignalChange(thisState, childLevel, this.worldPosition);
			for (Direction sideOfChildLevel : dirs)
			{
				this.getAperture(server, sideOfChildLevel).ifPresent(aperture ->{
					BlockPos aperturePos = aperture.getBlockPos();
					aperture.getBlockState().onNeighborChange(aperture.getLevel(), aperturePos, aperturePos.relative(sideOfChildLevel.getOpposite()));
				});
			}
			
		}
	}
	
	public void setColor(int color)
	{
		if (this.color != color)
		{
			this.color = color;
			this.setChanged();
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
			this.level.setBlocksDirty(this.worldPosition, state, state);
		}
	}
	
	public int getColor()
	{
		return this.color;
	}

	public Optional<ResourceKey<Level>> getLevelKey()
	{
		return this.levelKey;
	}
	
	public void setLevelKey(ResourceKey<Level> key)
	{
		this.levelKey = Optional.ofNullable(key);
		// force creation of level key to reserve it and sync key to client dimension lists
		if (this.level instanceof ServerLevel level)
		{
			this.getLevelIfKeySet(level.getServer());
		}
		this.setChanged();
	}

	@Override
	public Component getName()
	{
		return this.name.orElse(Component.translatable("block.hyperbox.hyperbox"));
	}

	@Override
	@Nullable
	public Component getCustomName()
	{
		return this.name.orElse(null);
	}
	
	public void setName(@Nullable Component name)
	{
		this.name = Optional.ofNullable(name);
		this.setChanged();
		this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}
	
	@Nullable
	public ServerLevel getLevelIfKeySet(MinecraftServer server)
	{
		return this.levelKey.map(key ->
		{
			ServerLevel targetWorld = this.getChildWorld(server, key);
			HyperboxSaveData.getOrCreate(targetWorld).setWorldPos(server, targetWorld, targetWorld.dimension(), this.level.dimension(), this.worldPosition, this.getColor());
			return targetWorld;
		})
			.orElse(null);
	}
	
	public ServerLevel getChildWorld(MinecraftServer server, ResourceKey<Level> key)
	{
		return InfiniverseAPI.get().getOrCreateLevel(server, key, () -> HyperboxDimension.createDimension(server));
	}
	
	public int getPower(boolean strong, Direction originalFace)
	{
		int output = (strong ? this.strongPowerDUNSWE : this.weakPowerDUNSWE)[originalFace.get3DDataValue()] - 1;
		return Mth.clamp(output,0,15);
	}

	@Nullable
	public <T> T getCapability(BlockCapability<T, Direction> sidedCap, Direction worldSpaceFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		// delegate to the capability of the block facing the linked aperture in the hyperspace cube
		if (thisBlock instanceof HyperboxBlock hyperboxBlock && this.level instanceof ServerLevel serverLevel)
		{
			ServerLevel targetLevel = this.getLevelIfKeySet(serverLevel.getServer());
			if (targetLevel != null)
			{
				BlockPos targetPos = hyperboxBlock.getPosAdjacentToAperture(this.getBlockState(), worldSpaceFace);
				Direction rotatedDirection = hyperboxBlock.getOriginalFace(thisState, worldSpaceFace);
				targetLevel.registerCapabilityListener(targetPos, () -> {
					serverLevel.invalidateCapabilities(this.getBlockPos());
					return false;
				});
				return level.getCapability(sidedCap, targetPos, rotatedDirection);
			}
		}
		return null;
	}
	
	public Optional<ApertureBlockEntity> getAperture(MinecraftServer server, Direction sideOfChildLevel)
	{
		BlockPos aperturePos = HyperboxChunkGenerator.CENTER.relative(sideOfChildLevel, 7);
		ServerLevel targetLevel = this.getLevelIfKeySet(server);
		return targetLevel == null
			? Optional.empty()
			: targetLevel.getBlockEntity(aperturePos) instanceof ApertureBlockEntity aperture
				? Optional.of(aperture)
				: Optional.empty();
	}
	
	public void updatePower(int weakPower, int strongPower, Direction originalFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		if (thisBlock instanceof HyperboxBlock hyperboxBlock)
		{
			Direction worldSpaceFace = hyperboxBlock.getCurrentFacing(thisState, originalFace);
			int originalFaceIndex = originalFace.get3DDataValue();
			int oldWeakPower = this.weakPowerDUNSWE[originalFaceIndex];
			int oldStrongPower = this.strongPowerDUNSWE[originalFaceIndex];
			if (oldWeakPower != weakPower || oldStrongPower != strongPower)
			{
				this.weakPowerDUNSWE[originalFaceIndex] = weakPower;
				this.strongPowerDUNSWE[originalFaceIndex] = strongPower;
				this.setChanged();	// mark te as needing its data saved
				this.level.sendBlockUpdated(this.worldPosition, thisState, thisState, 3); // mark te as needing data synced
				// notify neighbors so they react to the redstone output change
				if (EventHooks.onNeighborNotify(this.level, this.worldPosition, thisState, java.util.EnumSet.of(originalFace), true).isCanceled())
					return;
				BlockPos adjacentPos = this.worldPosition.relative(worldSpaceFace);
				this.level.neighborChanged(adjacentPos, thisBlock, this.worldPosition);
				this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, worldSpaceFace.getOpposite());
			}
		}
	}
	
	public void teleportPlayerOrOpenMenu(ServerPlayer serverPlayer, Direction faceActivated)
	{
		ServerLevel level = serverPlayer.serverLevel();
		MinecraftServer server = level.getServer();
		ServerLevel targetLevel = this.getLevelIfKeySet(server);
		if (targetLevel == null)
		{
			// if hyperbox doesn't have a dimension bound yet
			serverPlayer.openMenu(HyperboxMenu.makeServerMenu(this));
		}
		else
		{
			// if hyperbox already has a dimension bound
			BlockPos pos = this.getBlockPos();
			BlockState state = this.getBlockState();
			DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
			if (hyperboxDimensionType != level.dimensionType())
			{
				ReturnPoint.setReturnPoint(serverPlayer, level.dimension(), pos);
			}
			BlockPos posAdjacentToAperture = ((HyperboxBlock)state.getBlock()).getPosAdjacentToAperture(state, faceActivated);
			BlockPos spawnPoint = SpawnPointHelper.getBestSpawnPosition(
				targetLevel,
				posAdjacentToAperture,
				HyperboxChunkGenerator.MIN_SPAWN_CORNER,
				HyperboxChunkGenerator.MAX_SPAWN_CORNER);
			DelayedTeleportData.getOrCreate(serverPlayer.serverLevel()).schedulePlayerTeleport(serverPlayer, targetLevel.dimension(), Vec3.atCenterOf(spawnPoint));
		}
	}

	@Override
	public void saveAdditional(CompoundTag compound)
	{
		super.saveAdditional(compound);
		this.levelKey.ifPresent(key -> compound.putString(WORLD_KEY, key.location().toString()));
		this.writeClientSensitiveData(compound);
	}

	@Override
	public void load(CompoundTag nbt)
	{
		super.load(nbt);
		this.levelKey = nbt.contains(WORLD_KEY)
			? Optional.of(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(nbt.getString(WORLD_KEY))))
			: Optional.empty();
		this.readClientSensitiveData(nbt);
	}
	
	protected CompoundTag writeClientSensitiveData(CompoundTag nbt)
	{
		this.name.ifPresent(theName ->
		{
			nbt.putString(NAME, Component.Serializer.toJson(theName));
		});
		if (this.color != HyperboxBlockItem.DEFAULT_COLOR)
		{
			nbt.putInt(COLOR, this.color);
		}
		nbt.putIntArray(WEAK_POWER, this.weakPowerDUNSWE);
		nbt.putIntArray(STRONG_POWER, this.strongPowerDUNSWE);
		return nbt;
	}
	
	protected void readClientSensitiveData(CompoundTag nbt)
	{
		this.name = nbt.contains(NAME)
			? Optional.ofNullable(Component.Serializer.fromJson(nbt.getString(NAME)))
			: Optional.empty();
		this.color = nbt.contains(COLOR)
			? nbt.getInt(COLOR)
			: HyperboxBlockItem.DEFAULT_COLOR;
		this.weakPowerDUNSWE = nbt.getIntArray(WEAK_POWER);
		this.strongPowerDUNSWE = nbt.getIntArray(STRONG_POWER);
	}

	// called on server when the TE is initially loaded on client (e.g. when client loads chunk)
	// this is handled by this.handleUpdateTag, which just calls read()
	@Override
	public CompoundTag getUpdateTag()
	{
		CompoundTag nbt = super.getUpdateTag();
		this.writeClientSensitiveData(nbt);
		return nbt;
	}

	// called on server when notifyBlockUpdate is called, packet will be sent to client
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket()
	{
		// just defers to getUpdateTag
		return ClientboundBlockEntityDataPacket.create(this);
	}

	// called on client to read the packet sent from getUpdatePacket
	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt)
	{
		this.readClientSensitiveData(pkt.getTag());
	}
	
	@Override
	public void handleUpdateTag(CompoundTag nbt)
	{
		this.readClientSensitiveData(nbt);
	}
}
