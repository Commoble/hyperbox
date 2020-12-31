package commoble.hyperbox.aperture;

import java.util.Optional;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.SpawnPointHelper;
import commoble.hyperbox.box.HyperboxTileEntity;
import commoble.hyperbox.dimension.DelayedTeleportWorldData;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer.Builder;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

// the blocks on the inside of the storage dimension that lead outside
public class ApertureBlock extends Block
{
//	// shapes by Direction index (down, up, north, south, west, east)
//	public static final VoxelShape[] SHAPES_DUNSWE = {
//		Block.makeCuboidShape(0, 0, 0, 16, 1, 16),
//		Block.makeCuboidShape(0, 15, 0, 16, 16, 16),
//		Block.makeCuboidShape(0, 0, 0, 16, 16, 1),
//		Block.makeCuboidShape(0, 0, 15, 16, 16, 16),
//		Block.makeCuboidShape(0, 0, 0, 1, 16, 16),
//		Block.makeCuboidShape(16, 0, 0, 15, 16, 16)
//	};
	// direction of facing of aperture
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	public ApertureBlock(Properties properties)
	{
		super(properties);
		this.setDefaultState(this.stateContainer.getBaseState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void fillStateContainer(Builder<Block, BlockState> builder)
	{
		super.fillStateContainer(builder);
		builder.add(FACING);
	}

	@Override
	public boolean hasTileEntity(BlockState state)
	{
		return true;
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world)
	{
		return Hyperbox.INSTANCE.apertureTileEntityType.get().create();
	}

//	@Override
//	public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
//	{
//		return VoxelShapes.fullCube();
//	}

//	@Override
//	public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
//	{
//		return VoxelShapes.fullCube();
//	}

	@Override
	public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
	{
		if (player instanceof ServerPlayerEntity && worldIn instanceof ServerWorld)
		{
			ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
			ServerWorld serverWorld = (ServerWorld)worldIn;
			MinecraftServer server = serverPlayer.server;
			
			HyperboxWorldData data = HyperboxWorldData.getOrCreate(serverWorld);
			RegistryKey<World> parentKey = data.getParentWorld();
			BlockPos parentPos = data.getParentPos();
			ServerWorld destinationWorld = server.getWorld(parentKey);
			if (destinationWorld == null)
			{
				destinationWorld = server.getWorld(World.OVERWORLD);
			}
			Direction apertureFacing = state.get(FACING);
			// TODO find best free spawn point instead of forcing it
			BlockPos targetPos = parentPos.offset(apertureFacing.getOpposite());
			if (destinationWorld.getBlockState(targetPos).getBlockHardness(destinationWorld, targetPos) < 0)
			{	// if this face of the exit block faces bedrock, make the initial spawn search target be the exit block instead of the adjacent position
				// (we do this so the spawn finder doesn't skip through the bedrock)
				targetPos = parentPos;
			}
			targetPos = SpawnPointHelper.getBestSpawnPosition(destinationWorld, targetPos, targetPos.add(-3,-3,-3), targetPos.add(3,3,3));
//			DimensionHelper.sendPlayerToDimension(serverPlayer, destinationWorld, Vector3d.copyCentered(targetPos));
			DelayedTeleportWorldData.get(serverPlayer.getServerWorld()).addPlayer(serverPlayer, destinationWorld.getDimensionKey(), Vector3d.copyCentered(targetPos));
		}
		return ActionResultType.SUCCESS;
	}

	// called after an adjacent blockstate changes	
	@Override
	@Deprecated
	public void neighborChanged(BlockState thisState, World world, BlockPos thisPos, Block fromBlock, BlockPos fromPos, boolean isMoving)
	{
		this.onNeighborUpdated(thisState, world, thisPos, world.getBlockState(fromPos), fromPos);
		super.neighborChanged(thisState, world, thisPos, fromBlock, fromPos, isMoving);
	}

	// called when a neighboring te's data changes
	@Override
	public void onNeighborChange(BlockState thisState, IWorldReader world, BlockPos thisPos, BlockPos neighborPos)
	{
		this.onNeighborUpdated(thisState, world, thisPos, world.getBlockState(neighborPos), neighborPos);
		// does nothing by default
		super.onNeighborChange(thisState, world, thisPos, neighborPos);
	}
	
	protected void onNeighborUpdated(BlockState thisState, IBlockReader world, BlockPos thisPos, BlockState neighborState, BlockPos neighborPos)
	{
		if (world instanceof ServerWorld)
		{
			ServerWorld serverWorld = (ServerWorld)world;
			// get power from neighbor
			Direction directionToNeighbor = thisState.get(FACING);
			int weakPower = neighborState.getWeakPower(world, neighborPos, directionToNeighbor);
			int strongPower = neighborState.getStrongPower(world, neighborPos, directionToNeighbor);
			getHyperboxTileEntity(serverWorld,thisPos).ifPresent(te -> {
				te.updatePower(weakPower, strongPower, directionToNeighbor.getOpposite());
			});
		}
		
	}
	
	@Override
	public boolean canProvidePower(BlockState state)
	{
		return true;
	}

	@Override
	public int getWeakPower(BlockState blockState, IBlockReader world, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		return ApertureTileEntity.get(world, pos)
			.map(te -> te.getPower(false))
			.orElse(0);
	}

	@Override
	public int getStrongPower(BlockState blockState, IBlockReader world, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		return sideOfAdjacentBlock.getOpposite() == blockState.get(FACING)
			? ApertureTileEntity.get(world, pos)
				.map(te -> te.getPower(true))
				.orElse(0)
			: 0;
	}
	
	// get the hyperbox TE this aperture's world is linked to
	public static Optional<HyperboxTileEntity> getHyperboxTileEntity(ServerWorld world, BlockPos thisPos)
	{
		MinecraftServer server = world.getServer();
		HyperboxWorldData data = HyperboxWorldData.getOrCreate(world);
		BlockPos parentPos = data.getParentPos();
		RegistryKey<World> parentWorldKey = data.getParentWorld();
		ServerWorld parentWorld = server.getWorld(parentWorldKey);
		return HyperboxTileEntity.get(parentWorld, parentPos);
	}
}
