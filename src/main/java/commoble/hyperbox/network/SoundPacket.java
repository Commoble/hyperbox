package commoble.hyperbox.network;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import commoble.hyperbox.client.ClientPacketHandlers;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkEvent.Context;

// packet for playing a sound with more information than ServerWorld::playSound allows
public class SoundPacket implements Consumer<NetworkEvent.Context>
{
	@SuppressWarnings("deprecation")
	public static final Codec<SoundPacket> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Registry.SOUND_EVENT.optionalFieldOf("event", null).forGetter(SoundPacket::getEvent),
			Codec.STRING.xmap(SoundCategory.SOUND_CATEGORIES::get, SoundCategory::getName).fieldOf("category").forGetter(SoundPacket::getCategory),
			Codec.FLOAT.fieldOf("volume").forGetter(SoundPacket::getVolume),
			Codec.FLOAT.fieldOf("pitch").forGetter(SoundPacket::getPitch),
			Codec.BOOL.optionalFieldOf("repeat",false).forGetter(SoundPacket::getRepeat),
			Codec.INT.optionalFieldOf("repeat_delay",0).forGetter(SoundPacket::getRepeatdelay),
			Codec.BOOL.optionalFieldOf("attenuate",false).forGetter(SoundPacket::getAttenuate),
			Codec.DOUBLE.fieldOf("x").forGetter(SoundPacket::getX),
			Codec.DOUBLE.fieldOf("y").forGetter(SoundPacket::getY),
			Codec.DOUBLE.fieldOf("z").forGetter(SoundPacket::getZ),
			Codec.BOOL.optionalFieldOf("global", false).forGetter(SoundPacket::getGlobal)
		).apply(instance, SoundPacket::new));

	private final SoundEvent event;	public SoundEvent getEvent() { return this.event; }
	private final SoundCategory category;	public SoundCategory getCategory() { return this.category; }
	private final float volume;	public float getVolume() { return this.volume; }
	private final float pitch;	public float getPitch() { return this.pitch; }
	private final boolean repeat;	public boolean getRepeat() { return this.repeat; }
	private final int repeatDelay;	public int getRepeatdelay() { return this.repeatDelay; }
	private final boolean attenuate;	public boolean getAttenuate() { return this.attenuate; }
	private final double x;	public double getX() { return this.x; }
	private final double y;	public double getY() { return this.y; }
	private final double z;	public double getZ() { return this.z; }
	private final boolean global;	public boolean getGlobal() { return this.global; }
	
	public static final SoundPacket INVALID = new SoundPacket(null, SoundCategory.MASTER, 0F, 0F, false, 0, false, 0D, 0D, 0D, false);

	public SoundPacket(@Nullable SoundEvent event, SoundCategory category, float volume, float pitch, boolean repeat, int repeatDelay, boolean attenuate, double x, double y, double z, boolean global)
	{
		this.event = event;
		this.category = category;
		this.volume = volume;
		this.pitch = pitch;
		this.repeat = repeat;
		this.repeatDelay = repeatDelay;
		this.attenuate = attenuate;
		this.x = x;
		this.y = y;
		this.z = z;
		this.global = global;
	}

	@Override
	public void accept(Context context)
	{
		context.enqueueWork(() -> ClientPacketHandlers.handleSoundPacket(this));
	}
}