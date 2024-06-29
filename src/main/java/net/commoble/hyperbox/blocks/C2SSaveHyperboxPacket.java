package net.commoble.hyperbox.blocks;

import io.netty.buffer.ByteBuf;
import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.HyperboxDimension;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SSaveHyperboxPacket(String name, boolean enterImmediate) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<C2SSaveHyperboxPacket> TYPE = new CustomPacketPayload.Type<>(Hyperbox.id("save_hyperbox"));
	
	public static final StreamCodec<ByteBuf, C2SSaveHyperboxPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, C2SSaveHyperboxPacket::name,
		ByteBufCodecs.BOOL, C2SSaveHyperboxPacket::enterImmediate,
		C2SSaveHyperboxPacket::new);
	

	public void handle(IPayloadContext context)
	{
		context.enqueueWork(() -> this.handleMainThread(context));
	}
	
	private void handleMainThread(IPayloadContext context)
	{
		Player p = context.player();
		if (!(p instanceof ServerPlayer player) || !(player.containerMenu instanceof HyperboxMenu menu))
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

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
