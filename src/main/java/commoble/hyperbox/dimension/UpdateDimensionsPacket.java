package commoble.hyperbox.dimension;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import commoble.hyperbox.client.ClientPacketHandlers;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkEvent.Context;

// packet sent to the client when we add or remove a dimension
public class UpdateDimensionsPacket implements Consumer<NetworkEvent.Context>
{
	public static final UpdateDimensionsPacket INVALID = new UpdateDimensionsPacket(null, false);
	
	public static final Codec<UpdateDimensionsPacket> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			World.CODEC.optionalFieldOf("id", null).forGetter(UpdateDimensionsPacket::getId),
			Codec.BOOL.fieldOf("add").forGetter(UpdateDimensionsPacket::getAdd)
		).apply(instance, UpdateDimensionsPacket::new));

	private final RegistryKey<World> id;	public RegistryKey<World> getId() { return this.id; }
	private final boolean add;	public boolean getAdd() { return this.add; }

	public UpdateDimensionsPacket(RegistryKey<World> id, boolean add)
	{
		this.id = id;
		this.add = add;
	}
	
	@Override
	public void accept(Context context)
	{
		context.enqueueWork(() -> ClientPacketHandlers.handleUpdateDimensionsPacket(this));
	}

}
