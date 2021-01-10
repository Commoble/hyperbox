package commoble.hyperbox.client;

import java.util.Set;

import commoble.hyperbox.dimension.UpdateDimensionsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;

public class ClientPacketHandlers
{	
	public static void handleUpdateDimensionsPacket(UpdateDimensionsPacket packet)
	{
		@SuppressWarnings("resource")
		ClientPlayerEntity player = Minecraft.getInstance().player;
		RegistryKey<World> key = packet.getId();
		if (player == null || key == null)
			return;
		
		Set<RegistryKey<World>> worlds = player.connection.func_239164_m_();
		if (worlds == null)
			return;
		
		if (packet.getAdd())
		{
			worlds.add(key);
		}
		else
		{
			worlds.remove(key);
		}
	}
}
