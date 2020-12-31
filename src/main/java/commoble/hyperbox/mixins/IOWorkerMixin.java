package commoble.hyperbox.mixins;

import java.io.File;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import commoble.hyperbox.MixinCallbacks;
import net.minecraft.world.chunk.storage.IOWorker;
import net.minecraft.world.chunk.storage.RegionFileCache;

@Mixin(IOWorker.class)
public abstract class IOWorkerMixin implements Consumer<RegionFileCache>
{
	@Mutable
	@Accessor(remap=false)
	public abstract void setField_227084_e_(RegionFileCache cache);
	
	@Override
	public void accept(RegionFileCache cache)
	{
		this.setField_227084_e_(cache);
	}
	
	@Inject(method="<init>", at=@At("RETURN"))
	private void onConstruction(File file, boolean sync, String threadName, CallbackInfo info)
	{
		MixinCallbacks.onIOWorkerConstruction(file, sync, this);
	}
}
