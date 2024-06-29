package net.commoble.hyperbox.mixins;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.commoble.hyperbox.MixinCallbacks;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

@Mixin(IOWorker.class)
public abstract class IOWorkerMixin
{
	@Mutable
	@Accessor
	public abstract void setStorage(RegionFileStorage cache);
	
	@Inject(method="<init>", at=@At("RETURN"))
	private void onConstruction(RegionStorageInfo rsi, Path path, boolean sync, CallbackInfo info)
	{
		MixinCallbacks.onIOWorkerConstruction(rsi, path, sync, this::setStorage);
	}
}
