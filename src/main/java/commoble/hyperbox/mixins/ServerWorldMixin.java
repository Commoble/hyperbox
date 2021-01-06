package commoble.hyperbox.mixins;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import commoble.hyperbox.MixinCallbacks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.ISpawnWorldInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World
{

	private ServerWorldMixin(ISpawnWorldInfo worldInfo, RegistryKey<World> dimension, DimensionType dimensionType, Supplier<IProfiler> profiler, boolean isRemote,
		boolean isDebug, long seed)
	{
		super(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
	}
	
	// inject just before the method gets the server list
	// in the original method, a forge event is fired, and the arguments' values are potentially reassigned based on data from that event
	// because the new values are assigned to the argument fields (rather than new local variables), we don't need to capture locals, we just need the arguments
	@Inject(method="playSound", at=@At(value="INVOKE", target="net/minecraft/server/MinecraftServer.getPlayerList ()Lnet/minecraft/server/management/PlayerList;"))
	void playSoundInjection(@Nullable PlayerEntity player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, CallbackInfo info)
	{
		MixinCallbacks.onServerWorldPlaySound((ServerWorld)(Object)this, player,x,y,z,sound,category,volume,pitch,info);
	}
}
