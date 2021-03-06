package commoble.hyperbox;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxDimension.IterationResult;
import commoble.hyperbox.dimension.HyperboxRegionFileCache;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SPlaySoundEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.server.ServerWorld;

public class MixinCallbacks
{
	public static int modifyChunkManagerViewDistance(Supplier<ServerWorld> worldSupplier, int viewDistanceIn)
	{
		return HyperboxDimension.isHyperboxDimension(worldSupplier.get().getDimensionKey())
			? 2
			: viewDistanceIn;
	}
	
	public static void onIOWorkerConstruction(File file, boolean sync, Consumer<RegionFileCache> cacheConsumer)
	{
		if (file.getPath().contains("generated_hyperbox"))
		{
			cacheConsumer.accept(new HyperboxRegionFileCache(file,sync));
		}
	}
	
	public static void onServerWorldPlaySound(ServerWorld serverWorld, @Nullable PlayerEntity ignoredPlayer, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, CallbackInfo info)
	{
		MinecraftServer server = serverWorld.getServer();
		
		List<ServerPlayerEntity> players = server.getPlayerList().getPlayers();
		int playerCount = players.size();
		for (int i=0; i<playerCount; i++)
		{
			ServerPlayerEntity serverPlayer = players.get(i);
			if (serverPlayer == null || serverPlayer == ignoredPlayer)
				continue;

			ServerWorld playerWorld = serverPlayer.getServerWorld();
			if (playerWorld == null)
				continue;
			
			RegistryKey<World> playerWorldKey = playerWorld.getDimensionKey();
			
			if (!HyperboxDimension.isHyperboxDimension(playerWorldKey))
				continue;
			
			// find original parent world of hyperbox
			IterationResult result = HyperboxDimension.getHyperboxIterationDepth(server, serverWorld, playerWorld);
			int iterations = result.iterations;
			BlockPos pos = result.parentPos;
			if (iterations >= 0 && pos != null)
			{
				double dx = x - pos.getX();
				double dy = y - pos.getY();
				double dz = z - pos.getZ();
				double radius = volume > 1F ? volume * 16D : 16D;
				if (dx*dx + dy*dy + dz*dz < radius*radius)
				{
					double soundMultiplier = Math.pow(0.5D, iterations);
					float scaledVolume = (float) (soundMultiplier * volume * 0.5F);
					float scaledPitch = (float) (soundMultiplier * pitch);
					
					double packetX = HyperboxChunkGenerator.CENTER.getX() + dx;
					double packetY = HyperboxChunkGenerator.CENTER.getY() + dy;
					double packetZ = HyperboxChunkGenerator.CENTER.getZ() + dz;
					
					SPlaySoundEffectPacket packet = new SPlaySoundEffectPacket(sound,category,packetX,packetY,packetZ,scaledVolume,scaledPitch);
					serverPlayer.connection.sendPacket(packet);
				}
				
			}
		}
	}
}
