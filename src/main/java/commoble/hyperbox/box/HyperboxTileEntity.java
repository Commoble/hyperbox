package commoble.hyperbox.box;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.DimensionHelper;
import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.INameable;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class HyperboxTileEntity extends TileEntity implements INameable
{
	public static final String WORLD_KEY = "world_key";
	public static final String NAME = "CustomName"; // consistency with vanilla custom name data
	// key to the hyperbox world stored in this te
	private Optional<RegistryKey<World>> worldKey = Optional.empty();
	private Optional<ITextComponent> name = Optional.empty();
	
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
				.ifPresent(childWorld -> childWorld.forceChunk(0, 0, true));
		}
	}
	
	public void afterBlockPlaced()
	{
		if (this.world instanceof ServerWorld)
		{
			this.getOrCreateWorld(((ServerWorld)this.world).getServer()).forceChunk(0, 0, true);
		}
	}

	// un-forceload the child world chunk when the parent block is removed
	@Override
	public void remove()
	{
		if (this.world instanceof ServerWorld)
		{
			this.getOrCreateWorld(((ServerWorld)this.world).getServer()).forceChunk(0, 0, false);
		}
		super.remove();
	}
	
	// un-forceload the child world chunk when the parent chunk is unloaded
	@Override
	public void onChunkUnloaded()
	{
		if (this.world instanceof ServerWorld)
		{
			this.getOrCreateWorld(((ServerWorld)this.world).getServer()).forceChunk(0, 0, false);
		}
		super.onChunkUnloaded();
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
		HyperboxWorldData.getOrCreate(targetWorld).setWorldPos(this.world.getDimensionKey(), this.pos);
		return targetWorld;
	}
	
	public ServerWorld getChildWorld(MinecraftServer server, RegistryKey<World> key)
	{
		return DimensionHelper.getOrCreateWorld(server, key, HyperboxDimension::createDimension);
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
	{
		// delegate to the capability of the block facing the linked aperture in the hyperspace cube
		if (this.world instanceof ServerWorld)
		{
			ServerWorld targetWorld = this.getOrCreateWorld(((ServerWorld)this.world).getServer());
			BlockPos targetPos = this.getChildTargetPos(side);
			TileEntity delegateTE = targetWorld.getTileEntity(targetPos);
			if (delegateTE != null)
			{
				return delegateTE.getCapability(cap, side);
			}
		}
		return super.getCapability(cap, side);
	}
	
	public BlockPos getChildTargetPos(Direction side)
	{
		// the hyperbox dimension chunk is a 15x15x15 space, with bedrock walls, a corner at 0,0,0, and the center at 7,7,7
		// we want to get the position of the block adjacent to the relevant aperture
		// if side is e.g. west (the west side of the parent block)
		// then the target position is the block one space to the east of the western aperture
		// or six spaces to the west of the center
		return HyperboxChunkGenerator.CENTER.offset(side, 6);
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
	}
	
	/**
	 * Retrives a hyperbox te from the given world-pos if the te at that position exists and is a hyperbox te
	 * @param world world
	 * @param pos pos
	 * @return Optional containing the te if it exists and is a hyperbox te, empty optional otherwise
	 */
	public static Optional<HyperboxTileEntity> get(IBlockReader world, BlockPos pos)
	{
		TileEntity te = world.getTileEntity(pos);
		return te instanceof HyperboxTileEntity
			? Optional.of((HyperboxTileEntity)te)
			: Optional.empty();
	}
}
