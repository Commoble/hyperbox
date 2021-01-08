package commoble.hyperbox.client;

import java.util.Set;

import commoble.hyperbox.network.SoundPacket;
import commoble.hyperbox.network.UpdateDimensionsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound.AttenuationType;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

public class ClientPacketHandlers
{
	public static void handleSoundPacket(SoundPacket packet)
	{
		SoundEvent event = packet.getEvent();
		if (event != null)
		{
			SoundHandler soundHandler = Minecraft.getInstance().getSoundHandler();
			for (int i=1; i<5; i++)
			{
				SimpleSound sound = new SimpleSound(
					packet.getEvent().getRegistryName(),
					packet.getCategory(),
					packet.getVolume() / i,
					packet.getPitch() / i,
					packet.getRepeat(),
					packet.getRepeatdelay(),
					packet.getAttenuate() ? AttenuationType.LINEAR : AttenuationType.NONE,
					packet.getX(),
					packet.getY(),
					packet.getZ(),
					packet.getGlobal());
				soundHandler.playDelayed(sound, i*i);
			}

		}
	}
	
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
