package commoble.hyperbox.dimension;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

public record ReturnPoint(Optional<Data> data)
{
	public static final ReturnPoint EMPTY = new ReturnPoint(Optional.empty());
	
	public static final Codec<ReturnPoint> CODEC = Data.CODEC.optionalFieldOf("data")
		.xmap(ReturnPoint::new, ReturnPoint::data)
		.codec();
	
	public static void setReturnPoint(ServerPlayer player, ResourceKey<Level> key, BlockPos pos)
	{
		player.setData(Hyperbox.INSTANCE.returnPointAttachment.get(), new ReturnPoint(Optional.of(new Data(key, pos))));
	}
	
	public static ContainerLevelAccess getReturnPoint(ServerPlayer player)
	{
		MinecraftServer server = player.getServer();
		return player.getData(Hyperbox.INSTANCE.returnPointAttachment.get())
			.data()
			.flatMap(data -> data.getWorldPosCallable(server))
			.orElseGet(() -> {
				ServerLevel targetWorld = server.getLevel(player.getRespawnDimension()); // get respawn world
				if (targetWorld == null)
					targetWorld = server.getLevel(Level.OVERWORLD);
				
				return ContainerLevelAccess.create(targetWorld, targetWorld.getSharedSpawnPos());
			});
	}
	
	private static record Data(ResourceKey<Level> lastWorld, BlockPos lastPos)
	{
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.xmap(s -> ResourceKey.create(Registries.DIMENSION, s), ResourceKey::location).fieldOf("last_world").forGetter(Data::lastWorld),
				BlockPos.CODEC.fieldOf("last_pos").forGetter(Data::lastPos)
			).apply(instance, Data::new));

		public Optional<ContainerLevelAccess> getWorldPosCallable(MinecraftServer server)
		{
			ServerLevel world = server.getLevel(this.lastWorld);
			if (world == null)
			{
				return Optional.empty();
			}
			return Optional.of(ContainerLevelAccess.create(world,this.lastPos));
		}
	}
}
