package commoble.hyperbox.mixins;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import commoble.hyperbox.MixinCallbacks;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements Supplier<ServerLevel>
{
	@Accessor
	public abstract ServerLevel getLevel();
	
	@Override
	public ServerLevel get()
	{
		return this.getLevel();
	}
	
	// modify the view distance variable after the base method clamps it
	// normally, it takes the viewDistanceIn, adds 1, and clamps it to the range [3,33]
	// if the dimension is a hyperbox dimension, our mixin modifies it to 2 instead
	// (has to be at least two or entities in the same chunk don't render)
	@ModifyVariable(ordinal=1, method="setViewDistance", at=@At(value = "INVOKE_ASSIGN", target="net/minecraft/util/Mth.clamp (III)I"))
	public int modifyViewDistance(int viewDistanceIn, int clampedViewDistance)
	{
		return MixinCallbacks.modifyChunkManagerViewDistance(this, clampedViewDistance);
	}
}
