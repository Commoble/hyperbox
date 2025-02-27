package net.commoble.hyperbox.blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import net.commoble.exmachina.api.Channel;
import net.commoble.exmachina.api.Face;
import net.commoble.exmachina.api.Receiver;
import net.commoble.exmachina.api.SignalGraphUpdateGameEvent;
import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.DelayedTeleportData;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.commoble.hyperbox.dimension.HyperboxDimension;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.commoble.hyperbox.dimension.ReturnPoint;
import net.commoble.hyperbox.dimension.SpawnPointHelper;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.EventHooks;

public class HyperboxBlockEntity extends BlockEntity implements Nameable
{
	public static final String WORLD_KEY = "world_key";
	public static final String NAME = "CustomName"; // consistency with vanilla custom name data
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String DIGITAL_POWER = "digital_power";
	public static final String COLOR = "color";
	// key to the hyperbox world stored in this te
	private Optional<ResourceKey<Level>> levelKey = Optional.empty();
	private Optional<Component> name = Optional.empty();
	private int color = Hyperbox.DEFAULT_COLOR;
	// power output by side index of "original"/unrotated output side (linked to the aperture on the same side of the subdimension)
	private int[] weakPowerDUNSWE = {0,0,0,0,0,0};
	private int[] strongPowerDUNSWE = {0,0,0,0,0,0};
	private long[] digitalPowerDUNSWE = {0,0,0,0,0,0};
	@SuppressWarnings("unchecked")
	private Map<Channel, Receiver>[] receiversDUNSWE = Util.make(new Map[6], maps -> {
		for (Direction inputSide : Direction.values()) {
			int sideIndex = inputSide.ordinal();
			Map<Channel,Receiver> map = new HashMap<>();
			
			for (DyeColor color : DyeColor.values()) {
				map.put(Channel.single(color), new HyperboxBitwiseListener(color, inputSide, (levelAccess,power) -> this.receiveDigitalPower(inputSide, color.ordinal(), power)));
			}
			
			maps[sideIndex] = map;
		}
	});
	private Collection<Receiver> allReceivers = Util.make(() -> {
		List<Receiver> receivers = new ArrayList<>();
		for (int i=0; i<6; i++) {
			receivers.addAll(this.receiversDUNSWE[i].values());
		}
		return receivers;
	});
	@SuppressWarnings("unchecked")
	private Map<Channel, ToIntFunction<LevelReader>>[] suppliers = Util.make(new Map[6], maps -> {
		for (Direction side : Direction.values())
		{
			int sideIndex = side.ordinal();
			Map<Channel, ToIntFunction<LevelReader>> map = new HashMap<>();
			for (DyeColor color : DyeColor.values())
			{
				int shift = color.ordinal()*4;
				map.put(Channel.single(color), reader -> Math.max((((int)(this.digitalPowerDUNSWE[sideIndex] >> shift)) & 0xF) - 1, 0));
			}
			maps[sideIndex] = map;
		}
	});
	
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
				Direction rotatedDirection = HyperboxBlock.getOriginalFace(thisState, worldSpaceFace);
				targetLevel.registerCapabilityListener(targetPos, () -> {
					serverLevel.invalidateCapabilities(this.getBlockPos());
					return false;
				});
				return targetLevel.getCapability(sidedCap, targetPos, rotatedDirection);
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
	
	public void updateStrongPower(int strongPower, Direction originalFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		if (thisBlock instanceof HyperboxBlock hyperboxBlock)
		{
			Direction worldSpaceFace = HyperboxBlock.getCurrentFacing(thisState, originalFace);
			int originalFaceIndex = originalFace.get3DDataValue();
			int oldStrongPower = this.strongPowerDUNSWE[originalFaceIndex];
			if (oldStrongPower != strongPower)
			{
				this.strongPowerDUNSWE[originalFaceIndex] = strongPower;
				this.setChanged();	// mark te as needing its data saved
				this.level.sendBlockUpdated(this.worldPosition, thisState, thisState, 3); // mark te as needing data synced
				// notify neighbors so they react to the redstone output change
				if (EventHooks.onNeighborNotify(this.level, this.worldPosition, thisState, java.util.EnumSet.of(originalFace), true).isCanceled())
					return;
				BlockPos adjacentPos = this.worldPosition.relative(worldSpaceFace);
				Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(this.level, worldSpaceFace, null);
				this.level.neighborChanged(adjacentPos, thisBlock, orientation);
				this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, worldSpaceFace.getOpposite(), orientation);
			}
		}
	}
	
	public void receiveDigitalPower(Direction side, int color, int power)
	{
		if (this.getLevel() instanceof ServerLevel serverLevel)
		{
			HyperboxBlock.getApertureTileEntityForFace(getBlockState(), serverLevel, worldPosition, side).ifPresent(aperture -> {
				aperture.updateDigitalPower(color, power);
			});
		}
	}
	
	public void updateDigitalPower(Direction originalFace, int color, int power)
	{
		Direction side = HyperboxBlock.getCurrentFacing(this.getBlockState(), originalFace);
		int sideIndex = side.ordinal();
		int shift = 4*color;
		long oldSignalOnSide = this.digitalPowerDUNSWE[sideIndex];
		int oldSignalOnBand = ((int)(oldSignalOnSide >> (shift))) & 0xF;
		if (oldSignalOnBand != power)
		{
			int newSignalOnSide = (oldSignalOnBand & ~(0xF << shift)) | (power << shift);
			this.digitalPowerDUNSWE[sideIndex] = newSignalOnSide;
			this.updateWeakPower(sideIndex);
			this.setChanged();
			this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition.relative(side));
		}
	}
	
	public void receiveDigitalPower(Direction side, long newPower)
	{
		if (this.getLevel() instanceof ServerLevel serverLevel)
		{
			HyperboxBlock.getApertureTileEntityForFace(getBlockState(), serverLevel, worldPosition, side).ifPresent(aperture -> {
				aperture.updateDigitalPower(newPower);
			});
		}
	}
	
	public void updateDigitalPower(Direction originalFace, long newPower)
	{
		Direction side = HyperboxBlock.getCurrentFacing(getBlockState(), originalFace);
		int sideIndex = side.ordinal();
		long oldPower = this.digitalPowerDUNSWE[sideIndex];
		if (oldPower != newPower)
		{
			this.digitalPowerDUNSWE[sideIndex] = newPower;
			this.updateWeakPower(sideIndex);
			this.setChanged();;
			this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition);
			SignalGraphUpdateGameEvent.scheduleSignalGraphUpdate(level, worldPosition.relative(side));
		}
	}
	
	public void updateWeakPower(int side)
	{
		long signalOnSide = this.digitalPowerDUNSWE[side];
		int maxSignal = 0;
		for (int band=0; band<16; band++)
		{
			int bandPower = ((int)(signalOnSide >> (4*band))) & 0xF;
			if (bandPower > maxSignal) {
				maxSignal = bandPower;
			}
		}
		this.weakPowerDUNSWE[side] = maxSignal;
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
	public void saveAdditional(CompoundTag compound, HolderLookup.Provider registries)
	{
		super.saveAdditional(compound, registries);
		this.levelKey.ifPresent(key -> compound.putString(WORLD_KEY, key.location().toString()));
		this.writeClientSensitiveData(compound, registries);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries)
	{
		super.loadAdditional(nbt, registries);
		this.levelKey = nbt.contains(WORLD_KEY)
			? Optional.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString(WORLD_KEY))))
			: Optional.empty();
		this.readClientSensitiveData(nbt, registries);
	}
	
	protected CompoundTag writeClientSensitiveData(CompoundTag nbt, HolderLookup.Provider registries)
	{
		this.name.ifPresent(theName ->
		{
			nbt.putString(NAME, Component.Serializer.toJson(theName, registries));
		});
		if (this.color != Hyperbox.DEFAULT_COLOR)
		{
			nbt.putInt(COLOR, this.color);
		}
		nbt.putIntArray(WEAK_POWER, Arrays.copyOf(this.weakPowerDUNSWE, 6));
		nbt.putIntArray(STRONG_POWER, Arrays.copyOf(this.strongPowerDUNSWE, 6));
		nbt.putLongArray(DIGITAL_POWER, Arrays.copyOf(this.digitalPowerDUNSWE, 6));
		return nbt;
	}
	
	protected void readClientSensitiveData(CompoundTag nbt, HolderLookup.Provider registries)
	{
		this.name = nbt.contains(NAME)
			? Optional.ofNullable(Component.Serializer.fromJson(nbt.getString(NAME), registries))
			: Optional.empty();
		this.color = nbt.contains(COLOR)
			? nbt.getInt(COLOR)
			: Hyperbox.DEFAULT_COLOR;
		this.digitalPowerDUNSWE = Arrays.copyOf(nbt.getLongArray(DIGITAL_POWER), 6);
		this.strongPowerDUNSWE = Arrays.copyOf(nbt.getIntArray(STRONG_POWER), 6);
		this.weakPowerDUNSWE = Arrays.copyOf(nbt.getIntArray(WEAK_POWER), 6);
	}

	// called on server when the TE is initially loaded on client (e.g. when client loads chunk)
	// this is handled by this.handleUpdateTag, which just calls read()
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries)
	{
		CompoundTag nbt = super.getUpdateTag(registries);
		this.writeClientSensitiveData(nbt, registries);
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
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries)
	{
		this.readClientSensitiveData(pkt.getTag(), registries);
	}
	
	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries)
	{
		this.readClientSensitiveData(nbt, registries);
	}
	
	@Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
		super.applyImplicitComponents(input);
		this.name = Optional.ofNullable(input.get(DataComponents.CUSTOM_NAME));
		this.color = input.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(Hyperbox.DEFAULT_COLOR, true)).rgb();
		this.levelKey = Optional.ofNullable(input.get(Hyperbox.INSTANCE.worldKeyDataComponent.get()));
	}
	
	@Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
    	this.name.ifPresent(n -> builder.set(DataComponents.CUSTOM_NAME, n));
    	if (this.color != Hyperbox.DEFAULT_COLOR)
    	{
    		builder.set(DataComponents.DYED_COLOR, new DyedItemColor(this.color, true));
    	}
    	this.levelKey.ifPresent(key -> builder.set(Hyperbox.INSTANCE.worldKeyDataComponent.get(), key));
    }

	public @Nullable Receiver getReceiverEndpoint(BlockGetter level, BlockPos receiverPos, BlockState receiverState, Direction receiverSide,
		Face connectedFace, Channel channel)
	{
		Direction directionToNeighbor = Direction.getNearest(connectedFace.pos().subtract(receiverPos), null);
		if (directionToNeighbor != null
			&& receiverPos.relative(directionToNeighbor).equals(connectedFace.pos()))
		{
			return this.receiversDUNSWE[directionToNeighbor.ordinal()].get(channel);	
		}
		return null;
	}

	public Collection<Receiver> getAllReceivers(BlockGetter level, BlockPos receiverPos, BlockState receiverState, Channel channel)
	{
		return this.allReceivers;
	}

	public void resetUnusedReceivers(List<HyperboxBitwiseListener> listeners)
	{
		long[] newSignalsDUNSWE = Arrays.copyOf(this.digitalPowerDUNSWE, 6);
		for (HyperboxBitwiseListener listener : listeners)
		{
			Direction dir = listener.inputSide();
			DyeColor color = listener.color();
			int sideIndex = dir.ordinal();
			int shift = 4*color.ordinal();
			long oldSignal = newSignalsDUNSWE[sideIndex];
			long newSignal = (oldSignal &= ~(0xF << shift));
			newSignalsDUNSWE[sideIndex] = newSignal;
		}
		for (Direction side : Direction.values())
		{
			this.receiveDigitalPower(side, newSignalsDUNSWE[side.ordinal()]);
		}
	}

	public Map<Channel, ToIntFunction<LevelReader>> getSupplierEndpoints(Direction directionToNeighbor)
	{
		return this.suppliers[directionToNeighbor.ordinal()];
	}
}
