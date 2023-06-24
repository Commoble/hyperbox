package commoble.hyperbox.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;

public record PacketSerializer<T extends Consumer<NetworkEvent.Context>>(Class<T> packetClass, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder)
{
	
	/**
	 * <PACKET> network context consumer to run on the main thread
	 * @param id int id unique to the packet class within the context of the channel we are registering to
	 * @param channel SimpleChannel to register a packet type to
	 * @param serializer PacketType with the encoder and decoder
	 */
	public static <PACKET extends Consumer<NetworkEvent.Context>> void register(int id, SimpleChannel channel, PacketSerializer<PACKET> serializer)
	{
		final BiConsumer<PACKET,Supplier<Context>> handler = (packet,context) -> {
			NetworkEvent.Context ctx = context.get();
			ctx.enqueueWork(() -> packet.accept(ctx));
			ctx.setPacketHandled(true);
		};
		
		channel.registerMessage(id, serializer.packetClass, serializer.encoder, serializer.decoder, handler);
	}
}
