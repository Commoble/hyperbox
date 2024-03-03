package net.commoble.hyperbox.mixins;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.commoble.hyperbox.MixinCallbacks;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

@Mixin(IOWorker.class)
public abstract class IOWorkerMixin implements Consumer<RegionFileStorage>
{
	@Mutable
	@Accessor
	public abstract void setStorage(RegionFileStorage cache);
	
	@Override
	public void accept(RegionFileStorage cache)
	{
		this.setStorage(cache);
	}
	
	@Inject(method="<init>", at=@At("RETURN"))
	private void onConstruction(Path path, boolean sync, String threadName, CallbackInfo info)
	{
		MixinCallbacks.onIOWorkerConstruction(path, sync, this);
	}
}
