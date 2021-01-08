package commoble.hyperbox.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkEvent.Context;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class PacketType<PACKET extends Consumer<NetworkEvent.Context>>
{
	
	public static <PACKET extends Consumer<NetworkEvent.Context>> PacketType<PACKET> register(int id, SimpleChannel channel, Codec<PACKET> codec, PACKET invalidPacket)
	{
		return new PacketType<>(codec, invalidPacket).registerMessage(id, channel);
	}
	
	private final Codec<PACKET> codec;
	protected final PACKET defaultPacket;
	
	/**
	 * 
	 * @param codec A codec for the packet's class
	 * @param invalidPacket If a packet instance cannot be parsed on the client, it will be treated as this packet instance instead.
	 * The invalidPacket instance must have the same class instance as any packets sent of this type,
	 * and cannot have the same class instance as any other registered packet.
	 * (lambdas and anonymous classes are fine as long as a different declaration is used for each packet type)
	 */
	public PacketType(Codec<PACKET> codec, PACKET invalidPacket)
	{
		this.codec = codec;
		this.defaultPacket = invalidPacket;
	}

	public PacketType<PACKET> registerMessage(int id, SimpleChannel channel)
	{
		final BiConsumer<PACKET,PacketBuffer> encoder = (packet,buffer) -> this.codec.encodeStart(NBTDynamicOps.INSTANCE, packet)
			.result()
			.ifPresent(nbt -> buffer.writeCompoundTag((CompoundNBT)nbt));
		final Function<PacketBuffer,PACKET> decoder = buffer -> this.codec.parse(NBTDynamicOps.INSTANCE, buffer.readCompoundTag())
			.result()
			.orElse(this.defaultPacket);	// forge doesn't catch any decoding errors so we should return a packet without throwing
		final BiConsumer<PACKET,Supplier<Context>> handler = (packet,context) -> {
			packet.accept(context.get());
			context.get().setPacketHandled(true);
		};
		final Class<PACKET> packetClass = (Class<PACKET>) (this.defaultPacket.getClass());
		channel.registerMessage(id, packetClass, encoder, decoder, handler);
		return this;
	}
	
	public static abstract class PacketFactory<PACKET extends Consumer<NetworkEvent.Context>> extends PacketType<PACKET>
	{

		public PacketFactory(Codec<PACKET> codec, PACKET defaultPacket)
		{
			super(codec, defaultPacket);
		}
		
		public abstract PACKET create();
	}
	
	public static class UnitPacketFactory extends PacketFactory<Consumer<NetworkEvent.Context>>
	{
		public UnitPacketFactory(Consumer<NetworkEvent.Context> defaultPacket)
		{
			super(Codec.unit(defaultPacket), defaultPacket);
		}

		@Override
		public Consumer<Context> create()
		{
			return this.defaultPacket;
		}
	}
}
