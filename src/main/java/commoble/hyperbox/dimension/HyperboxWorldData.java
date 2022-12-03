package commoble.hyperbox.dimension;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.blocks.ApertureBlockEntity;
import commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// WorldSavedData is used for storing extra data in ServerWorld instances

// this class is used for storing information in a hyperbox world
public class HyperboxWorldData extends SavedData
{
	public static final String DATA_KEY = Hyperbox.MODID;
	public static final String PARENT_WORLD_KEY = "parent_world";
	public static final String PARENT_POS_KEY = "parent_pos";
	public static final BlockPos DEFAULT_PARENT_POS = new BlockPos(0,65,0);
	
	// ID of the world this hyperbox world's parent block is located in
	private ResourceKey<Level> parentWorld = Level.OVERWORLD;
	public ResourceKey<Level> getParentWorld() { return this.parentWorld; }
	// position of this hyperbox world's parent block
	private BlockPos parentPos = DEFAULT_PARENT_POS;
	public BlockPos getParentPos() { return this.parentPos; }
	
	public static HyperboxWorldData getOrCreate(ServerLevel world)
	{
		return world.getDataStorage().computeIfAbsent(HyperboxWorldData::load, HyperboxWorldData::create, DATA_KEY);
	}
	
	public static HyperboxWorldData load(CompoundTag nbt)
	{
		ResourceKey<Level> parentWorld = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(nbt.getString(PARENT_WORLD_KEY)));
		BlockPos parentPos = NbtUtils.readBlockPos(nbt.getCompound(PARENT_POS_KEY));
		return new HyperboxWorldData(parentWorld, parentPos);
	}
	
	public static HyperboxWorldData create()
	{
		return new HyperboxWorldData(Level.OVERWORLD, DEFAULT_PARENT_POS);
	}

	protected HyperboxWorldData(ResourceKey<Level> parentWorld, BlockPos parentPos)
	{
		this.parentWorld = parentWorld;
		this.parentPos = parentPos;
	}
	
	public void setWorldPos(MinecraftServer server, ServerLevel thisWorld, ResourceKey<Level> thisWorldKey, ResourceKey<Level> parentWorldKey, BlockPos parentPos, int color)
	{
		ResourceKey<Level> oldParentWorld = this.parentWorld;
		BlockPos oldParentPos = this.parentPos;
		if (!oldParentWorld.equals(parentWorldKey) || !(oldParentPos.equals(parentPos)))
		{
			clearOldParent(server, thisWorldKey, oldParentWorld, oldParentPos);
		}
		this.parentWorld = parentWorldKey;
		this.parentPos = parentPos;
		for (Direction dir : Direction.values())
		{
			BlockPos aperturePos = HyperboxChunkGenerator.CENTER.relative(dir, 7);
			if (thisWorld.getBlockEntity(aperturePos) instanceof ApertureBlockEntity aperture)
			{
				aperture.setColor(color);
			}
		}
		this.setDirty();
	}
	
	protected static void clearOldParent(MinecraftServer server, ResourceKey<Level> thisWorldKey, ResourceKey<Level> oldParentKey, BlockPos oldParentPos)
	{
		ServerLevel oldParentWorld = server.getLevel(oldParentKey);
		if (oldParentWorld != null
			&& oldParentWorld.getBlockEntity(oldParentPos) instanceof HyperboxBlockEntity hyperbox
			&& hyperbox.getLevelKey().filter(thisWorldKey::equals).isPresent())
		{
			oldParentWorld.removeBlock(oldParentPos, true);
		}
	}

	@Override
	public CompoundTag save(CompoundTag compound)
	{
		compound.putString(PARENT_WORLD_KEY, this.parentWorld.location().toString());
		compound.put(PARENT_POS_KEY, NbtUtils.writeBlockPos(this.parentPos));
		return compound;
	}

}
