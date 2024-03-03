package commoble.hyperbox.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import commoble.hyperbox.MixinCallbacks;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin
{	
	// inject just before the method gets the server list
	// in the original method, a forge event is fired, and the arguments' values are potentially reassigned based on data from that event
	// because the new values are assigned to the argument fields (rather than new local variables), we don't need to capture locals, we just need the arguments
	@Inject(method="playSeededSound", at=@At(value="INVOKE", target="net/minecraft/server/MinecraftServer.getPlayerList()Lnet/minecraft/server/players/PlayerList;"))
	void playSoundInjection(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed, CallbackInfo info)
	{
		MixinCallbacks.onServerWorldPlaySound((ServerLevel)(Object)this, player,x,y,z,sound,category,volume,pitch,seed,info);
	}
}
