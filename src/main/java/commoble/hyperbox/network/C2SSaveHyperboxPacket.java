package commoble.hyperbox.network;

import java.util.function.Consumer;

import commoble.hyperbox.blocks.HyperboxMenu;
import commoble.hyperbox.dimension.HyperboxDimension;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SSaveHyperboxPacket(String name, boolean enterImmediate) implements Consumer<NetworkEvent.Context>
{
	public static final PacketSerializer<C2SSaveHyperboxPacket> SERIALIZER = new PacketSerializer<>(
		C2SSaveHyperboxPacket.class,
		(packet,buffer) -> {
			buffer.writeUtf(packet.name);
			buffer.writeBoolean(packet.enterImmediate);
		},
		(buffer) -> new C2SSaveHyperboxPacket(
			buffer.readUtf(),
			buffer.readBoolean()));
	
	@Override
	public void accept(Context context)
	{
		ServerPlayer player = context.getSender();
		if (player == null || !(player.containerMenu instanceof HyperboxMenu menu))
		{
			// don't do anything else if menu isn't open (averts possible spam from bad actors)
			return;
		}
		ResourceLocation dimensionId = HyperboxDimension.generateId(player, this.name);
		
		ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
		ServerLevel level = player.serverLevel();
		if (level.getServer().getLevel(levelKey) != null)
		{
			// send invalid dimension id packet: existing id
			player.displayClientMessage(Component.translatable("menu.hyperbox.message.existing_id", dimensionId), false);
			player.closeContainer();
			return;
		}
		
		// everything is valid, we can set things now
		menu.hyperbox().ifPresentOrElse(hyperbox ->
		{
			hyperbox.setLevelKey(levelKey);
			if (this.name != null && !this.name.isEmpty())
			{
				hyperbox.setName(Component.literal(this.name));
			}
			if (this.enterImmediate)
			{
				// teleporting the player will close the menu as the player is no longer near the block
				// DOWN will place the player in the middle of the empty hyperbox
				hyperbox.teleportPlayerOrOpenMenu(player, Direction.DOWN);
			}
			else
			{
				player.closeContainer();
			}
		}, player::closeContainer); // menu should always have a hyperbox on the server but we'll handle the case anyway
	}
}
