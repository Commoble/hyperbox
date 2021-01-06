package commoble.hyperbox.capability;

import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.Names;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.IWorldPosCallable;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

public class ReturnPointCapability implements Capability.IStorage<ReturnPointCapability>, ICapabilityProvider
{
	// initialized by forge
	@CapabilityInject(ReturnPointCapability.class)
	public static final Capability<ReturnPointCapability> INSTANCE = null; 
	public static final ResourceLocation ID = new ResourceLocation(Hyperbox.MODID, Names.RETURN_POINT);
	
	private final LazyOptional<ReturnPointCapability> holder = LazyOptional.of(() -> this);
	
	public Optional<Data> data = Optional.empty();
	
	public void setReturnPoint(RegistryKey<World> key, BlockPos pos)
	{
		this.data = Optional.of(new Data(key, pos));
	}
	
	public static IWorldPosCallable getReturnPoint(ServerPlayerEntity player)
	{
		MinecraftServer server = player.getServer();
		return player.getCapability(INSTANCE)
			.resolve()
			.flatMap(cap -> cap.data.flatMap(data -> data.getWorldPosCallable(cap, server)))
			.orElseGet(() -> {
				ServerWorld targetWorld = server.getWorld(player.func_241141_L_()); // get respawn world
				if (targetWorld == null)
					targetWorld = server.getWorld(World.OVERWORLD);
				
				return IWorldPosCallable.of(targetWorld, targetWorld.getSpawnPoint());
			});
	}

	@Override
	public INBT writeNBT(Capability<ReturnPointCapability> capability, ReturnPointCapability instance, Direction side)
	{
		return this.data.flatMap(d -> Data.CODEC.encodeStart(NBTDynamicOps.INSTANCE, d).result()).orElseGet(CompoundNBT::new);
	}

	@Override
	public void readNBT(Capability<ReturnPointCapability> capability, ReturnPointCapability instance, Direction side, INBT nbt)
	{
		this.data = Data.CODEC.parse(NBTDynamicOps.INSTANCE, nbt).result();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side)
	{
		return cap == INSTANCE
			? INSTANCE.orEmpty(cap, this.holder)
			: LazyOptional.empty();
	}
	
	static class Data
	{
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.xmap(s -> RegistryKey.getOrCreateKey(Registry.WORLD_KEY, s), RegistryKey::getLocation).fieldOf("last_world").forGetter(Data::getLastWorld),
				BlockPos.CODEC.fieldOf("last_pos").forGetter(Data::getLastPos)
			).apply(instance, Data::new));
			
			private final RegistryKey<World> lastWorld;	public RegistryKey<World> getLastWorld() { return this.lastWorld; }
			private final BlockPos lastPos;	public BlockPos getLastPos() { return this.lastPos; }
			
			public Data(RegistryKey<World> lastWorld, BlockPos lastPos)
			{
				this.lastWorld = lastWorld;
				this.lastPos = lastPos;
			}

			public Optional<IWorldPosCallable> getWorldPosCallable(ReturnPointCapability cap, MinecraftServer server)
			{
				ServerWorld world = server.getWorld(this.lastWorld);
				if (world == null)
				{
					cap.data = Optional.empty();
					return Optional.empty();
				}
				return Optional.of(IWorldPosCallable.of(world,this.lastPos));
			}
			
	}

}
