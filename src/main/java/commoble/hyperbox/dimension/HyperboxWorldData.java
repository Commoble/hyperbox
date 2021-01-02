package commoble.hyperbox.dimension;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.blocks.HyperboxTileEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

// WorldSavedData is used for storing extra data in ServerWorld instances

// this class is used for storing information in a hyperbox world
public class HyperboxWorldData extends WorldSavedData
{
	public static final String DATA_KEY = Hyperbox.MODID + ":hyperbox";
	public static final String PARENT_WORLD_KEY = "parent_world";
	public static final String PARENT_POS_KEY = "parent_pos";
	public static final BlockPos DEFAULT_PARENT_POS = new BlockPos(0,65,0);
	
	// ID of the world this hyperbox world's parent block is located in
	private RegistryKey<World> parentWorld = World.OVERWORLD;
	public RegistryKey<World> getParentWorld() { return this.parentWorld; }
	// position of this hyperbox world's parent block
	private BlockPos parentPos = DEFAULT_PARENT_POS;
	public BlockPos getParentPos() { return this.parentPos; }

	public HyperboxWorldData()
	{
		super(DATA_KEY);
	}
	
	public static HyperboxWorldData getOrCreate(ServerWorld world)
	{
		return world.getSavedData().getOrCreate(HyperboxWorldData::new, DATA_KEY);
	}
	
	public void setWorldPos(MinecraftServer server, RegistryKey<World> thisWorld, RegistryKey<World> parentWorld, BlockPos parentPos)
	{
		RegistryKey<World> oldParentWorld = this.parentWorld;
		BlockPos oldParentPos = this.parentPos;
		if (!oldParentWorld.equals(parentWorld) || !(oldParentPos.equals(parentPos)))
		{
			this.clearOldParent(server, thisWorld, oldParentWorld, oldParentPos);
		}
		this.parentWorld = parentWorld;
		this.parentPos = parentPos;
		this.markDirty();
	}
	
	protected void clearOldParent(MinecraftServer server, RegistryKey<World> thisWorldKey, RegistryKey<World> oldParentKey, BlockPos oldParentPos)
	{
		ServerWorld oldParentWorld = server.getWorld(oldParentKey);
		if (oldParentWorld != null)
		{
			HyperboxTileEntity.get(oldParentWorld, oldParentPos)
				.flatMap(HyperboxTileEntity::getWorldKey)
				.filter(thisWorldKey::equals)
				.ifPresent($ -> oldParentWorld.removeBlock(oldParentPos, true));
		}
	}

	@Override
	public void read(CompoundNBT nbt)
	{
		this.parentWorld = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, new ResourceLocation(nbt.getString(PARENT_WORLD_KEY)));
		this.parentPos = NBTUtil.readBlockPos(nbt.getCompound(PARENT_POS_KEY));
	}

	@Override
	public CompoundNBT write(CompoundNBT compound)
	{
		compound.putString(PARENT_WORLD_KEY, this.parentWorld.getLocation().toString());
		compound.put(PARENT_POS_KEY, NBTUtil.writeBlockPos(this.parentPos));
		return compound;
	}

}
