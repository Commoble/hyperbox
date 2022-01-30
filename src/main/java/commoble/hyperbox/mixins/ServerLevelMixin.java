package commoble.hyperbox.mixins;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import commoble.hyperbox.MixinCallbacks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.WritableLevelData;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level
{

	private ServerLevelMixin(WritableLevelData worldInfo, ResourceKey<Level> dimension, DimensionType dimensionType, Supplier<ProfilerFiller> profiler, boolean isRemote,
		boolean isDebug, long seed)
	{
		super(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
	}
	
	// inject just before the method gets the server list
	// in the original method, a forge event is fired, and the arguments' values are potentially reassigned based on data from that event
	// because the new values are assigned to the argument fields (rather than new local variables), we don't need to capture locals, we just need the arguments
	// (the forge event doesn't have positional context so we can't use the forge event at this time)
	@Inject(method="playSound", at=@At(value="INVOKE", target="net/minecraft/server/MinecraftServer.getPlayerList()Lnet/minecraft/server/players/PlayerList;"))
	void playSoundInjection(@Nullable Player player, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, CallbackInfo info)
	{
		MixinCallbacks.onServerWorldPlaySound((ServerLevel)(Object)this, player,x,y,z,sound,category,volume,pitch,info);
	}
}
