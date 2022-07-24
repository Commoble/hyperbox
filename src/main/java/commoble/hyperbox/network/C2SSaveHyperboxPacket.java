package commoble.hyperbox.network;

import java.util.function.Consumer;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.blocks.HyperboxMenu;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SSaveHyperboxPacket(String dimension, String name, boolean enterImmediate) implements Consumer<NetworkEvent.Context>
{
	public static final PacketSerializer<C2SSaveHyperboxPacket> SERIALIZER = new PacketSerializer<>(
		C2SSaveHyperboxPacket.class,
		(packet,buffer) -> {
			buffer.writeUtf(packet.dimension);
			buffer.writeUtf(packet.name);
			buffer.writeBoolean(packet.enterImmediate);
		},
		(buffer) -> new C2SSaveHyperboxPacket(
			buffer.readUtf(),
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
		
		if (this.dimension == null || this.dimension.isBlank())
		{
			player.displayClientMessage(Component.translatable("menu.hyperbox.message.no_id", this.dimension), false);
			player.closeContainer();
			return;
		}
			
		if (!ResourceLocation.isValidPath(this.dimension))
		{
			player.displayClientMessage(Component.translatable("menu.hyperbox.message.invalid_id", this.dimension), false);
			player.closeContainer();
			return;
		}
		
		ResourceLocation dimensionId = new ResourceLocation(Hyperbox.MODID, this.dimension);
		ResourceKey<Level> levelKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
		ServerLevel level = player.getLevel();
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
