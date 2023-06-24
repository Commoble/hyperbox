package commoble.hyperbox.dimension;

import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.Names;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class ReturnPointCapability implements ICapabilitySerializable<CompoundTag>
{
	public static final Capability<ReturnPointCapability> INSTANCE = CapabilityManager.get(new CapabilityToken<>(){});
	public static final ResourceLocation ID = new ResourceLocation(Hyperbox.MODID, Names.RETURN_POINT);
	
	private final LazyOptional<ReturnPointCapability> holder = LazyOptional.of(() -> this);
	
	public Optional<Data> data = Optional.empty();
	
	public void setReturnPoint(ResourceKey<Level> key, BlockPos pos)
	{
		this.data = Optional.of(new Data(key, pos));
	}
	
	public static ContainerLevelAccess getReturnPoint(ServerPlayer player)
	{
		MinecraftServer server = player.getServer();
		return player.getCapability(INSTANCE)
			.resolve()
			.flatMap(cap -> cap.data.flatMap(data -> data.getWorldPosCallable(cap, server)))
			.orElseGet(() -> {
				ServerLevel targetWorld = server.getLevel(player.getRespawnDimension()); // get respawn world
				if (targetWorld == null)
					targetWorld = server.getLevel(Level.OVERWORLD);
				
				return ContainerLevelAccess.create(targetWorld, targetWorld.getSharedSpawnPos());
			});
	}

	@Override
	public CompoundTag serializeNBT()
	{
		return (CompoundTag) this.data.flatMap(d -> Data.CODEC.encodeStart(NbtOps.INSTANCE, d).result()).orElseGet(CompoundTag::new);
	}

	@Override
	public void deserializeNBT(CompoundTag tag)
	{
		this.data = Data.CODEC.parse(NbtOps.INSTANCE, tag).result();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side)
	{
		return cap == INSTANCE
			? INSTANCE.orEmpty(cap, this.holder)
			: LazyOptional.empty();
	}
	
	private static record Data(ResourceKey<Level> lastWorld, BlockPos lastPos)
	{
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.xmap(s -> ResourceKey.create(Registries.DIMENSION, s), ResourceKey::location).fieldOf("last_world").forGetter(Data::lastWorld),
				BlockPos.CODEC.fieldOf("last_pos").forGetter(Data::lastPos)
			).apply(instance, Data::new));

		public Optional<ContainerLevelAccess> getWorldPosCallable(ReturnPointCapability cap, MinecraftServer server)
		{
			ServerLevel world = server.getLevel(this.lastWorld);
			if (world == null)
			{
				cap.data = Optional.empty();
				return Optional.empty();
			}
			return Optional.of(ContainerLevelAccess.create(world,this.lastPos));
		}
	}
}
