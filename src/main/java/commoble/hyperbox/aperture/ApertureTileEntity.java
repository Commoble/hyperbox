package commoble.hyperbox.aperture;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class ApertureTileEntity extends TileEntity
{

	public ApertureTileEntity()
	{
		super(Hyperbox.INSTANCE.apertureTileEntityType.get());
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
	{
		if (this.world instanceof ServerWorld)
		{
			ServerWorld serverWorld = (ServerWorld)this.world;
			MinecraftServer server = serverWorld.getServer();
			HyperboxWorldData data = HyperboxWorldData.getOrCreate(serverWorld);
			BlockPos parentPos = data.getParentPos();
			RegistryKey<World> parentWorldKey = data.getParentWorld();
			ServerWorld parentWorld = server.getWorld(parentWorldKey);
			// delegate to the potential TE on the other side of the parent hyperbox
			// e.g. if this block is being accessed on its west face, we want to check
			// the tile entity one space to the east of the parent block
			BlockPos delegatePos = parentPos.offset(side.getOpposite());
			TileEntity delegateTileEntity = parentWorld.getTileEntity(delegatePos);
			if (delegateTileEntity != null)
			{
				return delegateTileEntity.getCapability(cap, side);
			}
		}
		return super.getCapability(cap, side);
	}

	
}
