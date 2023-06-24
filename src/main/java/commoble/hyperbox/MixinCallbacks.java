package commoble.hyperbox;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxDimension.IterationResult;
import commoble.hyperbox.dimension.HyperboxRegionFileStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.dimension.DimensionType;

public class MixinCallbacks
{
	public static int modifyChunkManagerViewDistance(Supplier<ServerLevel> levelSupplier, int viewDistanceIn)
	{
		ServerLevel level = levelSupplier.get();
		MinecraftServer server = level.getServer();
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		return hyperboxDimensionType == level.dimensionType()
			? 2
			: viewDistanceIn;
	}
	
	public static void onIOWorkerConstruction(Path path, boolean sync, Consumer<RegionFileStorage> cacheConsumer)
	{
		String s = path.toString();
		if (s.contains("dimensions/hyperbox") || s.contains("dimensions\\hyperbox"))
		{
			cacheConsumer.accept(new HyperboxRegionFileStorage(path,sync));
		}
	}
	
	public static void onServerWorldPlaySound(ServerLevel serverLevel, @Nullable Player ignoredPlayer, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed, CallbackInfo info)
	{
		MinecraftServer server = serverLevel.getServer();
		
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		int playerCount = players.size();
		for (int i=0; i<playerCount; i++)
		{
			ServerPlayer serverPlayer = players.get(i);
			if (serverPlayer == null || serverPlayer == ignoredPlayer)
				continue;

			ServerLevel playerLevel = serverPlayer.serverLevel();
			if (playerLevel == null)
				continue;
			
			DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
			if (hyperboxDimensionType != playerLevel.dimensionType())
				continue;
			
			// find original parent world of hyperbox
			IterationResult result = HyperboxDimension.getHyperboxIterationDepth(server, serverLevel, playerLevel);
			int iterations = result.iterations();
			BlockPos pos = result.parentPos();
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
					
					ClientboundSoundPacket packet = new ClientboundSoundPacket(sound,category,packetX,packetY,packetZ,scaledVolume,scaledPitch,seed);
					serverPlayer.connection.send(packet);
				}
				
			}
		}
	}
}
