package commoble.hyperbox.mixins;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import commoble.hyperbox.MixinCallbacks;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;

@Mixin(ChunkManager.class)
public abstract class ChunkManagerMixin implements Supplier<ServerWorld>
{
	@Accessor
	public abstract ServerWorld getWorld();
	
	@Override
	public ServerWorld get()
	{
		return this.getWorld();
	}
	
	// modify the view distance variable after the base method clamps it
	// normally, it takes the viewDistanceIn, adds 1, and clamps it to the range [3,33]
	// if the dimension is a hyperbox dimension, our mixin modifies it to 2 instead
	// (has to be at least two or entities in the same chunk don't render)
	@ModifyVariable(ordinal=1, method="setViewDistance", at=@At(value = "INVOKE_ASSIGN", target="net/minecraft/util/math/MathHelper.clamp (III)I"))
	public int modifyViewDistance(int viewDistanceIn, int clampedViewDistance)
	{
		return MixinCallbacks.modifyChunkManagerViewDistance(this, clampedViewDistance);
	}
	
//	@Inject(method ="cannotGenerateChunks", at = @At("HEAD"), cancellable=true)
//	public void onCannotGenerateChunks(ServerPlayerEntity player, CallbackInfoReturnable<Boolean> info)
//	{
//		MixinCallbacks.onChunkHolderCannotGenerateChunks(this, info);
//	}
}
