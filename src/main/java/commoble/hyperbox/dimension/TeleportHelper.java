package commoble.hyperbox.dimension;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class TeleportHelper
{	
	// helper for sending a given player to another dimension
	// for static dimensions (from datapacks, etc) use MinecraftServer::getWorld to get the world object
	// for dynamic dimensions (mystcrafty) use infiniverse to get the target world
	public static void sendPlayerToDimension(ServerPlayer serverPlayer, ServerLevel targetLevel, Vec3 targetVec)
	{
		// ensure destination chunk is loaded before we put the player in it
		targetLevel.getChunk(new BlockPos(targetVec));
		serverPlayer.teleportTo(targetLevel, targetVec.x(), targetVec.y(), targetVec.z(), serverPlayer.getYRot(), serverPlayer.getXRot());
	}
	
	public static void ejectPlayerFromDeadWorld(ServerPlayer serverPlayer)
	{
		// get best world to send the player to
		ReturnPointCapability.getReturnPoint(serverPlayer)
			.evaluate((targetLevel,pos) ->
			{
				if (targetLevel instanceof ServerLevel serverLevel)
				{
					sendPlayerToDimension(serverPlayer, serverLevel, Vec3.atCenterOf(pos));
				}
				return Optional.empty();	// make the worldposcallable happy
			});
	}
}